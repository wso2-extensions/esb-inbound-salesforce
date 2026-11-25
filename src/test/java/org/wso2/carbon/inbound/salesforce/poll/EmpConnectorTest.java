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

import org.cometd.client.BayeuxClient;
import org.eclipse.jetty.client.HttpClient;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Tests the Bayeux client lifecycle of {@link EmpConnector} against an in-process streaming server
 * ({@link MockStreamingServer}): reconnection after session expiry and handshake failures, re-handshakes and shutdown.
 */
public class EmpConnectorTest {

    static final String TOPIC = "/event/Test_Event__e";
    static final long TIMEOUT_MILLIS = 15000;
    /** A connected client polls again at least every HOLD_MILLIS, so this window always contains its latest poll. */
    static final long POLL_WINDOW_MILLIS = MockStreamingServer.HOLD_MILLIS * 3;

    private MockStreamingServer server;
    private EmpConnector connector;
    private final List<Long> received = new CopyOnWriteArrayList<>();

    @BeforeMethod
    public void setUp() throws Exception {
        server = new MockStreamingServer();
        received.clear();
        SalesforceDataHolderObject.connectionTimeout = 5000;
        connector = newConnector(server);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        try {
            connector.stop(true);
        } finally {
            server.stop();
        }
    }

    @Test(description = "Regression for duplicate event processing: after a reconnection whose first handshake failed "
            + "and a further reconnection, each event is delivered once and only one Bayeux client stays connected")
    public void testEventProcessedOnceAfterReconnectFollowingFailedHandshake() throws Exception {
        startAndSubscribe();

        // The session expires; the reconnection's first handshake fails and CometD's retry succeeds on the same client.
        server.failNextHandshakes(1);
        server.expireAllClients();
        waitUntil("the reconnection to succeed after the failed handshake",
                () -> server.failedHandshakesServed() == 1 && server.validSubscribedClients(TOPIC) == 1);

        // The session expires again. Before the fix, the previous client stayed connected and registered a second
        // listener on the new client.
        server.expireAllClients();
        waitUntil("the second reconnection to subscribe", () -> server.validSubscribedClients(TOPIC) == 1);
        Thread.sleep(POLL_WINDOW_MILLIS); // give a leftover client time to re-handshake and subscribe again

        assertEachEventDeliveredOnce();
        Assert.assertEquals(server.pollingClients(POLL_WINDOW_MILLIS), 1, "only the current client may stay connected");
    }

    @Test(description = "When the server forgets the client without an authentication failure, CometD re-handshakes "
            + "the same client: the topic must be subscribed again, once, and events must keep arriving")
    public void testSameClientRehandshakeKeepsSubscription() throws Exception {
        startAndSubscribe();

        for (int i = 1; i <= 2; i++) {
            int handshakes = server.handshakeAttempts();
            server.forgetAllClients();
            waitUntil("re-handshake " + i, () -> server.handshakeAttempts() > handshakes
                    && server.validSubscribedClients(TOPIC) == 1);
            assertEachEventDeliveredOnce();
        }
        Assert.assertEquals(server.pollingClients(POLL_WINDOW_MILLIS), 1, "only the current client may stay connected");
    }

    @Test(description = "Subscribing a topic on a client it is already subscribed on must not add a second listener, "
            + "otherwise every event is delivered twice")
    public void testSubscribingTwiceOnSameClientAddsOneListener() throws Exception {
        startAndSubscribe();
        BayeuxClient client = (BayeuxClient) field(connector, "client");

        // What the handshake callback does when it overlaps an explicit subscribe() on the same client.
        Object subscription = ((Set<?>) field(connector, "subscriptions")).iterator().next();
        Method subscribe = subscription.getClass().getDeclaredMethod("subscribe", BayeuxClient.class);
        subscribe.setAccessible(true);
        subscribe.invoke(subscription, client);

        Assert.assertEquals(client.getChannel(TOPIC).getSubscribers().size(), 1, "listeners on the topic channel");
        assertEachEventDeliveredOnce();
    }

    @Test(description = "A reconnect keeps the HTTP client for the new Bayeux client, and the final stop releases all "
            + "of its threads")
    public void testFinalStopAfterReconnectReleasesHttpClient() throws Exception {
        startAndSubscribe();
        int handshakes = server.handshakeAttempts();
        server.expireAllClients();
        waitUntil("the reconnection", () -> server.handshakeAttempts() > handshakes
                && server.validSubscribedClients(TOPIC) == 1);
        HttpClient httpClient = (HttpClient) field(connector, "httpClient");
        Assert.assertTrue(httpClient.isStarted(), "a reconnect must keep the HTTP client started");

        connector.stop(true);

        Assert.assertTrue(httpClient.isStopped(), "the final stop must stop the HTTP client");
        waitUntil("the HTTP client's threads to end", () -> threadsOf(httpClient) == 0);
        waitUntil("no client to stay connected", () -> server.pollingClients(POLL_WINDOW_MILLIS) == 0);
    }

    @Test(description = "A connect() that overlaps the current client replaces and aborts it, a final stop then leaves "
            + "no client running, and a connect() after the final stop starts nothing")
    public void testOverlappingConnectAndFinalStopLeaveNoClient() throws Exception {
        startAndSubscribe();
        BayeuxClient first = (BayeuxClient) field(connector, "client");

        // A second connect() while the first client is still current: the replaced client must be aborted.
        int handshakes = server.handshakeAttempts();
        connect();
        waitUntil("the second client to handshake", () -> server.handshakeAttempts() > handshakes);
        waitUntil("the replaced client to be aborted", first::isDisconnected);
        Thread.sleep(POLL_WINDOW_MILLIS);
        Assert.assertEquals(server.pollingClients(POLL_WINDOW_MILLIS), 1, "only the new client may stay connected");
        assertEachEventDeliveredOnce();

        // The final stop aborts the started client.
        connector.stop(true);
        waitUntil("no client to stay connected", () -> server.pollingClients(POLL_WINDOW_MILLIS) == 0);

        // A connect() after the final stop must not start a client.
        int handshakesAfterStop = server.handshakeAttempts();
        Assert.assertFalse(connect().get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS), "connect() after a final stop");
        Assert.assertNull(field(connector, "client"), "no client may be published after a final stop");
        Thread.sleep(POLL_WINDOW_MILLIS);
        Assert.assertEquals(server.handshakeAttempts(), handshakesAfterStop, "no handshake after a final stop");
    }

    // ---------------------------------------------------------------- helpers

    @SuppressWarnings("unchecked")
    private Future<Boolean> connect() throws Exception {
        Method connect = EmpConnector.class.getDeclaredMethod("connect");
        connect.setAccessible(true);
        return (Future<Boolean>) connect.invoke(connector);
    }

    static EmpConnector newConnector(MockStreamingServer server) {
        BayeuxParameters parameters = new BayeuxParameters() {
            @Override
            public String bearerToken() {
                return "dummy-token";
            }

            @Override
            public URL endpoint() {
                try {
                    return new URL(server.cometdUrl());
                } catch (MalformedURLException e) {
                    throw new IllegalStateException(e);
                }
            }

            @Override
            public int maxNetworkDelay() {
                // Larger than the server's long-poll hold, so the client never abandons a poll the server still holds.
                return (int) MockStreamingServer.HOLD_MILLIS * 5;
            }
        };
        EmpConnector empConnector = new EmpConnector(parameters, () -> { });
        empConnector.setBearerTokenProvider(reauthenticate -> "dummy-token");
        return empConnector;
    }

    private void startAndSubscribe() throws Exception {
        Assert.assertTrue(connector.start().get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS), "the connector must start");
        connector.subscribeTip(TOPIC, this::record).get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertEquals(server.validSubscribedClients(TOPIC), 1);
    }

    /** Publish three events and assert that the consumer receives each of them exactly once. */
    private void assertEachEventDeliveredOnce() throws Exception {
        received.clear();
        List<Long> published = server.publish(TOPIC, 3);
        waitUntil("the published events to be delivered", () -> received.size() >= published.size());
        Thread.sleep(MockStreamingServer.HOLD_MILLIS * 2); // a duplicate listener would deliver within this window
        Assert.assertEquals(received, published, "each event must be delivered exactly once");
    }

    @SuppressWarnings("unchecked")
    private void record(Map<String, Object> event) {
        Map<String, Object> eventInfo = (Map<String, Object>) event.get("event");
        received.add(((Number) eventInfo.get("replayId")).longValue());
    }

    static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static long threadsOf(HttpClient httpClient) {
        String prefix = "HttpClient@" + Integer.toHexString(httpClient.hashCode());
        return Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.isAlive() && t.getName().startsWith(prefix)).count();
    }

    static void waitUntil(String what, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                Assert.fail("Timed out waiting for " + what);
            }
            Thread.sleep(100);
        }
    }
}
