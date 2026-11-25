/*
 * Copyright (c) 2026, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.carbon.inbound.salesforce.poll;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.eclipse.jetty.util.ajax.JSON;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-process stand-in for the Salesforce SOAP login and Streaming API (Bayeux long-polling) endpoints, used to test the
 * connector's client lifecycle. It implements only what the connector uses: SOAP login, handshake, subscribe, connect
 * and disconnect. Faults can be injected: failed handshakes, an expired session ({@code 403::Unknown client}), and an
 * unknown client that must re-handshake ({@code 402::Unknown client}). The server also records each Bayeux client, so
 * tests can assert from the server side which clients are still connected.
 *
 * Long polls are held for {@link #HOLD_MILLIS} and the server advises the same timeout. Tests configure a larger
 * {@code maxNetworkDelay}, so the client never abandons a poll that the server still holds.
 */
class MockStreamingServer {

    static final long HOLD_MILLIS = 1000;
    static final String COMETD_VERSION = "56.0";

    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Object lock = new Object();
    private final Map<String, ClientState> clients = new LinkedHashMap<>();
    private final AtomicInteger replaySequence = new AtomicInteger(1000);
    private final AtomicInteger logins = new AtomicInteger();
    private final AtomicInteger handshakeAttempts = new AtomicInteger();
    private final AtomicInteger failedHandshakesServed = new AtomicInteger();
    private volatile int handshakesToFail;
    private volatile long loginDelayMillis;

    /** Server-side view of one Bayeux client (one clientId). Guarded by the server lock. */
    private static final class ClientState {
        final Set<String> subscriptions = new HashSet<>();
        final List<Map<String, Object>> queue = new ArrayList<>();
        boolean valid = true;
        boolean unknown;
        int connectsInFlight;
        long lastConnectStartMillis;
    }

    MockStreamingServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.setExecutor(executor);
        server.createContext("/services/Soap/", this::handleSoapLogin);
        server.createContext("/cometd/", this::handleBayeux);
        server.start();
    }

    void stop() {
        synchronized (lock) {
            lock.notifyAll();
        }
        server.stop(0);
        executor.shutdownNow();
    }

    String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    String cometdUrl() {
        return baseUrl() + LoginHelper.COMETD_REPLAY + COMETD_VERSION;
    }

    // ---------------------------------------------------------------- fault injection / control

    /** Reply HTTP 503 to the next {@code count} handshake requests. */
    void failNextHandshakes(int count) {
        handshakesToFail = count;
    }

    /** Delay every SOAP login reply. */
    void setLoginDelay(long millis) {
        loginDelayMillis = millis;
    }

    /** Expire every client's session, as Salesforce does: the next poll gets {@code 403::Unknown client}. */
    void expireAllClients() {
        synchronized (lock) {
            for (ClientState c : clients.values()) {
                c.valid = false;
            }
            lock.notifyAll();
        }
    }

    /**
     * Make the next poll of every valid client fail with {@code 402::Unknown client} and handshake advice. This is not
     * an authentication failure, so the connector does not reconnect: CometD re-handshakes the same client object.
     */
    void forgetAllClients() {
        synchronized (lock) {
            for (ClientState c : clients.values()) {
                if (c.valid) {
                    c.unknown = true;
                }
            }
            lock.notifyAll();
        }
    }

    /** Publish {@code count} events on {@code topic} to every valid client subscribed to it. Returns their replay ids. */
    List<Long> publish(String topic, int count) {
        List<Long> ids = new ArrayList<>();
        synchronized (lock) {
            for (int i = 0; i < count; i++) {
                long replayId = replaySequence.getAndIncrement();
                ids.add(replayId);
                for (ClientState c : clients.values()) {
                    if (c.valid && !c.unknown && c.subscriptions.contains(topic)) {
                        c.queue.add(event(topic, replayId));
                    }
                }
            }
            lock.notifyAll();
        }
        return ids;
    }

    // ---------------------------------------------------------------- observations

    int logins() {
        return logins.get();
    }

    int handshakeAttempts() {
        return handshakeAttempts.get();
    }

    int failedHandshakesServed() {
        return failedHandshakesServed.get();
    }

    /** Valid clients that hold a subscription to {@code topic}. */
    int validSubscribedClients(String topic) {
        synchronized (lock) {
            int n = 0;
            for (ClientState c : clients.values()) {
                if (c.valid && !c.unknown && c.subscriptions.contains(topic)) {
                    n++;
                }
            }
            return n;
        }
    }

    /**
     * Number of clients still long-polling: a poll is being held right now, or one started within
     * {@code windowMillis}. A connected client polls again at least every {@link #HOLD_MILLIS}.
     */
    int pollingClients(long windowMillis) {
        long now = System.currentTimeMillis();
        int n = 0;
        synchronized (lock) {
            for (ClientState c : clients.values()) {
                if (c.connectsInFlight > 0 || now - c.lastConnectStartMillis <= windowMillis) {
                    n++;
                }
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- handlers

    private void handleSoapLogin(HttpExchange exchange) throws IOException {
        readBody(exchange);
        int login = logins.incrementAndGet();
        if (loginDelayMillis > 0) {
            sleep(loginDelayMillis);
        }
        String xml = "<?xml version='1.0' encoding='UTF-8'?>"
                + "<soapenv:Envelope xmlns:soapenv='http://schemas.xmlsoap.org/soap/envelope/' "
                + "xmlns='urn:partner.soap.sforce.com'><soapenv:Body><loginResponse><result>"
                + "<serverUrl>" + baseUrl() + "/services/Soap/u/22.0/00D000000000001</serverUrl>"
                + "<sessionId>SESSION" + login + "</sessionId>"
                + "</result></loginResponse></soapenv:Body></soapenv:Envelope>";
        send(exchange, 200, "text/xml", xml);
    }

    @SuppressWarnings("unchecked")
    private void handleBayeux(HttpExchange exchange) throws IOException {
        Object parsed = JSON.parse(readBody(exchange));
        Object[] messages = parsed instanceof Object[] ? (Object[]) parsed : new Object[]{parsed};
        List<Map<String, Object>> replies = new ArrayList<>();
        for (Object o : messages) {
            Map<String, Object> m = (Map<String, Object>) o;
            String channel = (String) m.get("channel");
            if ("/meta/handshake".equals(channel)) {
                handshakeAttempts.incrementAndGet();
                if (handshakesToFail > 0) {
                    handshakesToFail--;
                    failedHandshakesServed.incrementAndGet();
                    send(exchange, 503, "text/plain", "Service Unavailable");
                    return;
                }
                replies.add(handshake(m));
            } else if ("/meta/subscribe".equals(channel)) {
                replies.add(subscribe(m));
            } else if ("/meta/connect".equals(channel)) {
                replies.addAll(connect(m));
            } else if ("/meta/disconnect".equals(channel)) {
                synchronized (lock) {
                    clients.remove(m.get("clientId"));
                }
                replies.add(reply(m, true));
            } else {
                replies.add(reply(m, true));
            }
        }
        send(exchange, 200, "application/json", JSON.toString(replies.toArray()));
    }

    private Map<String, Object> handshake(Map<String, Object> m) {
        String clientId = UUID.randomUUID().toString().substring(0, 8);
        synchronized (lock) {
            clients.put(clientId, new ClientState());
        }
        Map<String, Object> r = reply(m, true);
        r.put("version", "1.0");
        r.put("clientId", clientId);
        r.put("supportedConnectionTypes", new Object[]{"long-polling"});
        r.put("advice", advice("retry"));
        return r;
    }

    private Map<String, Object> subscribe(Map<String, Object> m) {
        String clientId = (String) m.get("clientId");
        String subscription = (String) m.get("subscription");
        boolean ok;
        synchronized (lock) {
            ClientState c = clients.get(clientId);
            ok = c != null && c.valid && !c.unknown;
            if (ok) {
                c.subscriptions.add(subscription);
            }
        }
        Map<String, Object> r = reply(m, ok);
        r.put("clientId", clientId);
        r.put("subscription", subscription);
        if (!ok) {
            r.put("error", "403::Unknown client");
            r.put("advice", advice("handshake"));
        }
        return r;
    }

    private List<Map<String, Object>> connect(Map<String, Object> m) {
        String clientId = (String) m.get("clientId");
        List<Map<String, Object>> events = new ArrayList<>();
        String error = null;
        synchronized (lock) {
            ClientState c = clients.get(clientId);
            if (c != null) {
                c.connectsInFlight++;
                c.lastConnectStartMillis = System.currentTimeMillis();
            }
            long deadline = System.currentTimeMillis() + HOLD_MILLIS;
            try {
                while (c != null && c.valid && !c.unknown && c.queue.isEmpty()) {
                    long remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0) {
                        break;
                    }
                    lock.wait(remaining);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (c != null) {
                c.connectsInFlight--;
            }
            if (c == null || !c.valid) {
                error = "403::Unknown client";
            } else if (c.unknown) {
                error = "402::Unknown client";
                clients.remove(clientId);
            } else {
                events.addAll(c.queue);
                c.queue.clear();
            }
        }
        Map<String, Object> r = reply(m, error == null);
        r.put("clientId", clientId);
        if (error == null) {
            r.put("advice", advice("retry"));
        } else {
            r.put("error", error);
            r.put("advice", advice("handshake"));
        }
        List<Map<String, Object>> out = new ArrayList<>();
        out.add(r);
        out.addAll(events);
        return out;
    }

    // ---------------------------------------------------------------- helpers

    private static Map<String, Object> event(String topic, long replayId) {
        Map<String, Object> eventInfo = new HashMap<>();
        eventInfo.put("replayId", replayId);
        Map<String, Object> data = new HashMap<>();
        data.put("event", eventInfo);
        data.put("payload", new HashMap<>());
        Map<String, Object> message = new HashMap<>();
        message.put("channel", topic);
        message.put("data", data);
        return message;
    }

    private static Map<String, Object> reply(Map<String, Object> request, boolean successful) {
        Map<String, Object> r = new HashMap<>();
        r.put("channel", request.get("channel"));
        r.put("id", request.get("id"));
        r.put("successful", successful);
        return r;
    }

    private static Map<String, Object> advice(String reconnect) {
        Map<String, Object> advice = new HashMap<>();
        advice.put("reconnect", reconnect);
        advice.put("interval", 0);
        advice.put("timeout", HOLD_MILLIS);
        return advice;
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int n;
            while ((n = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, n);
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        try {
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        } catch (IOException e) {
            // The client abandoned the request (for example it was aborted); nothing to deliver to.
        } finally {
            exchange.close();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
