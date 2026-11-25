/*
 * Copyright (c) 2016-2018, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.TXT file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */
package org.wso2.carbon.inbound.salesforce.poll;

import java.net.ConnectException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Future;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import org.cometd.bayeux.Channel;
import org.cometd.bayeux.Message;
import org.cometd.bayeux.client.ClientSession;
import org.cometd.bayeux.client.ClientSessionChannel;
import org.cometd.client.BayeuxClient;
import org.cometd.client.transport.LongPollingTransport;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author hal.hildebrand
 * @since 202
 */
public class EmpConnector {

    private static final Logger LOG = LoggerFactory.getLogger(EmpConnector.class);
    private static final String ERROR = "error";
    private static final String FAILURE = "failure";

    private class SubscriptionImpl implements TopicSubscription {

        private final String topic;
        private final Consumer<Map<String, Object>> consumer;
        // A single listener instance per subscription, so that it can be added to a client's channel at most once.
        private final ClientSessionChannel.MessageListener listener;
        // The client this subscription was last subscribed on, and the result of that subscribe. Guarded by this.
        private BayeuxClient subscribedOn;
        private CompletableFuture<TopicSubscription> subscribeResult;
        // Completed by the first subscribe reply on any client. Returned when no client is available to subscribe on.
        private final CompletableFuture<TopicSubscription> firstResult = new CompletableFuture<>();

        private SubscriptionImpl(String topic, Consumer<Map<String, Object>> consumer) {
            this.topic = topic;
            this.consumer = consumer;
            this.listener = (c, message) -> consumer.accept(message.getDataAsMap());
            subscriptions.add(this);
        }

        /*
         * (non-Javadoc)
         * @see com.salesforce.emp.connector.Subscription#cancel()
         */
        @Override
        public void cancel() {
            replay.remove(topic);
            if (running.get() && client != null) {
                client.getChannel(topic).unsubscribe();
                subscriptions.remove(this);
            }
        }

        /*
         * (non-Javadoc)
         * @see com.salesforce.emp.connector.Subscription#getReplay()
         */
        @Override
        public long getReplayFrom() {
            return replay.getOrDefault(topic, REPLAY_FROM_EARLIEST);
        }

        /*
         * (non-Javadoc)
         * @see com.salesforce.emp.connector.Subscription#getTopic()
         */
        @Override
        public String getTopic() {
            return topic;
        }

        @Override
        public String toString() {
            return String.format("Subscription [%s:%s]", getTopic(), getReplayFrom());
        }

        /**
         * Subscribe this topic on the given Bayeux client.
         *
         * The target client is passed explicitly instead of reading the {@code client} field, so that a handshake
         * callback of one client can never register a listener on a different client.
         *
         * This is idempotent per client: if this subscription is already subscribed on the target, the existing result
         * is returned. CometD would otherwise add the same listener again and deliver every event twice. This happens
         * when both the handshake callback and {@link EmpConnector#subscribe} subscribe the same topic on a client.
         * A re-handshake resets the client's subscriptions, so this subscribes again after one.
         *
         * @param target the Bayeux client to subscribe on
         */
        Future<TopicSubscription> subscribe(BayeuxClient target) {
            ClientSessionChannel channel = target.getChannel(topic);
            synchronized (this) {
                if (subscribedOn == target && channel.getSubscribers().contains(listener)) {
                    return subscribeResult;
                }
                Long replayFrom = getReplayFrom();
                CompletableFuture<TopicSubscription> future = new CompletableFuture<>();
                subscribedOn = target;
                subscribeResult = future;
                channel.subscribe(listener, (c, message) -> {
                    if (message.isSuccessful()) {
                        future.complete(this);
                        firstResult.complete(this);
                    } else {
                        Object error = message.get(ERROR);
                        if (error == null) {
                            error = message.get(FAILURE);
                        }
                        String errorText = String.valueOf(error != null ? error : message);
                        Throwable failure;
                        if (isInvalidReplayError(errorText))  {
                            cancel();
                            failure = new InvalidReplayIdException(topic, replayFrom, errorText);
                        } else {
                            failure = new CannotSubscribe(parameters.endpoint(), topic, replayFrom,
                                    error != null ? error : message);
                        }
                        future.completeExceptionally(failure);
                        firstResult.completeExceptionally(failure);
                    }
                });
                return future;
            }
        }

        private boolean isInvalidReplayError(String errorText) {
            if (errorText == null) {
                return false;
            }
            String lower = errorText.toLowerCase();
            return lower.contains("replayid") && lower.contains("invalid");
        }
    }

    private static long REPLAY_FROM_EARLIEST = -2L;
    private static long REPLAY_FROM_TIP = -1L;
    private static String AUTHORIZATION = "Authorization";
    // The current Bayeux client. Written only under lifecycleLock; whoever removes a client from here aborts it.
    private volatile BayeuxClient client;
    private final HttpClient httpClient;
    private final BayeuxParameters parameters;
    private final ConcurrentMap<String, Long> replay = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean();
    // Set by a final stop. Once set, no new Bayeux client may be published or handshaken by this connector.
    // Written only under lifecycleLock.
    private volatile boolean terminated;
    // Guards publishing and clearing the client, setting terminated, starting the HTTP client and starting a handshake.
    // Held only briefly: never while logging in, aborting or waiting for a client, or stopping the HTTP client.
    private final Object lifecycleLock = new Object();
    private final Set<SubscriptionImpl> subscriptions = new CopyOnWriteArraySet<>();
    private final Set<MessageListenerInfo> listenerInfos = new CopyOnWriteArraySet<>();
    private Function<Boolean, String> bearerTokenProvider;
    private AtomicBoolean reauthenticate = new AtomicBoolean(false);

    // A reference to the ConnectionFailureListener to notify upon connection failure
    private ConnectionFailureListener failureListener;

    public EmpConnector(BayeuxParameters parameters, ConnectionFailureListener listener) {
        this.parameters = parameters;
        httpClient = new HttpClient(parameters.sslContextFactory());
        httpClient.getProxyConfiguration().getProxies().addAll(parameters.proxies());
        httpClient.setConnectTimeout(SalesforceDataHolderObject.connectionTimeout);
        failureListener = listener;
    }

    /**
     * Start the connector.
     *
     * @return true if connection was established, false otherwise
     */
    public Future<Boolean> start() {
        if (running.compareAndSet(false, true)) {
            addListener(Channel.META_CONNECT, new AuthFailureListener());
            addListener(Channel.META_HANDSHAKE, new AuthFailureListener());
            replay.clear();
            return connect();
        }
        CompletableFuture<Boolean> future = new CompletableFuture<Boolean>();
        future.complete(true);
        return future;
    }

    /**
     * Stop the connector.
     *
     * @param terminateCompletely indicates if this is a final shutdown (true) or a temporary stop (false).
     */
    public void stop(boolean terminateCompletely ) {
        BayeuxClient current;
        synchronized (lifecycleLock) {
            // A concurrent connect() checks terminated under this lock before starting the HTTP client, and again
            // before publishing its client and starting the handshake. So it either sees the flag and starts neither,
            // or has already published its client, which is taken here and aborted below.
            if (terminateCompletely) {
                terminated = true;
            }
            current = client;
            client = null;
        }
        boolean wasRunning = running.getAndSet(false);

        if (current == null && !wasRunning && !terminateCompletely) {
            // Already stopped and nothing to clean up.
            return;
        }

        if (current != null) {
            if (!wasRunning) {
                // The running flag must never hide a live client; otherwise it is left connected as an orphan.
                LOG.warn("EmpConnector was not marked as running, but a Bayeux Client is still active. "
                        + "Shutting it down.");
            }
            LOG.info("Forcefully shutting down Bayeux Client in EmpConnector");

            // Unsubscribe from all active subscriptions
            if (terminateCompletely) {
                subscriptions.forEach(SubscriptionImpl::cancel);
            }
            // Force disconnect
            current.abort();  // Immediately terminates transport connections
            boolean disconnected = current.waitFor(5000, BayeuxClient.State.DISCONNECTED);

            if (!disconnected) {
                LOG.warn("Bayeux Client did not disconnect within 5 seconds! Forcing shutdown...");
            }
        }

        try {
            // Only a final stop releases the HTTP client. A temporary stop (reconnect) keeps it for the next connect().
            // It must be stopped before it is destroyed: destroy() drops the client's components, so a later stop()
            // could no longer stop their threads.
            if (terminateCompletely) {
                httpClient.stop();
                httpClient.destroy();
                LOG.info("HTTP client stopped successfully.");
            }
        } catch (Exception e) {
            LOG.error("Error while shutting down HTTP transport[{}]", parameters.endpoint(), e);
        }
    }

    /**
     * Stop the connector.
     * This method is used to stop the connector without terminating the HTTP client.
    */
    public void stop() {
        stop(false);
    }


    /**
     * Set a bearer token / session id provider function that takes a boolean as input and returns a valid token.
     * If the input is true, the provider function is supposed to re-authenticate with the Salesforce server
     * and get a fresh session id or token.
     *
     * @param bearerTokenProvider a bearer token provider function.
     */
    public void setBearerTokenProvider(Function<Boolean, String> bearerTokenProvider) {
        this.bearerTokenProvider = bearerTokenProvider;
    }

    /**
     * Subscribe to a topic, receiving events after the replayFrom position
     *
     * @param topic      - the topic to subscribe to
     * @param replayFrom - the replayFrom position in the event stream
     * @param consumer   - the consumer of the events
     * @return a Future returning the Subscription - on completion returns a Subscription or throws a CannotSubscribe
     * exception
     */
    public Future<TopicSubscription> subscribe(String topic, long replayFrom, Consumer<Map<String, Object>> consumer) {
        if (!running.get()) {
            throw new IllegalStateException(String.format("Connector[%s} has not been started", parameters.endpoint()));
        }
        if (replay.putIfAbsent(topic, replayFrom) != null) {
            throw new IllegalStateException(
                    String.format("Already subscribed to %s [%s]", topic, parameters.endpoint()));
        }
        SubscriptionImpl subscription = new SubscriptionImpl(topic, consumer);
        BayeuxClient current = client;
        if (current == null) {
            // A reconnect is in progress. The subscription is registered, so the next successful handshake subscribes it.
            return subscription.firstResult;
        }
        return subscription.subscribe(current);
    }

    /**
     * Subscribe to a topic, receiving events from the earliest event position in the stream
     *
     * @param topic    - the topic to subscribe to
     * @param consumer - the consumer of the events
     * @return a Future returning the Subscription - on completion returns a Subscription or throws a CannotSubscribe
     * exception
     */
    public Future<TopicSubscription> subscribeEarliest(String topic, Consumer<Map<String, Object>> consumer) {
        return subscribe(topic, REPLAY_FROM_EARLIEST, consumer);
    }

    /**
     * Subscribe to a topic, receiving events from the latest event position in the stream
     *
     * @param topic    - the topic to subscribe to
     * @param consumer - the consumer of the events
     * @return a Future returning the Subscription - on completion returns a Subscription or throws a CannotSubscribe
     * exception
     */
    public Future<TopicSubscription> subscribeTip(String topic, Consumer<Map<String, Object>> consumer) {
        return subscribe(topic, REPLAY_FROM_TIP, consumer);
    }

    public EmpConnector addListener(String channel, ClientSessionChannel.MessageListener messageListener) {
        listenerInfos.add(new MessageListenerInfo(channel, messageListener));
        return this;
    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    public boolean isDisconnected() {
        return client == null || client.isDisconnected();
    }

    public boolean isHandshook() {
        return client != null && client.isHandshook();
    }

    public long getLastReplayId(String topic) {
        return replay.get(topic);
    }

    private Future<Boolean> connect() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        // The check and the start are atomic with respect to a final stop (see stop()): the HTTP client is either
        // started before the final stop stops it, or not started at all.
        synchronized (lifecycleLock) {
            if (terminated) {
                running.set(false);
                future.complete(false);
                return future;
            }
            LOG.info("Connector connecting");
            try {
                httpClient.start();
            } catch (Exception e) {
                LOG.error("Unable to start HTTP transport[{}]", parameters.endpoint(), e);
                running.set(false);
                future.complete(false);
                return future;
            }
        }
        String bearerToken = bearerToken();
        LongPollingTransport httpTransport = new LongPollingTransport(parameters.longPollingOptions(), httpClient) {
            @Override
            protected void customize(Request request) {

                request.header(AUTHORIZATION, bearerToken);
            }
        };
        final BayeuxClient newClient = new BayeuxClient(parameters.endpoint().toExternalForm(), httpTransport);
        newClient.addExtension(new ReplayExtension(replay));
        addListeners(newClient);
        // Note: CometD invokes this callback for every handshake reply of this client, including its own automatic
        // handshake retries and re-handshakes, not only for the first handshake.
        ClientSessionChannel.MessageListener onHandshake = (c, m) -> {
            if (terminated || newClient != client) {
                // This client has been replaced (or the connector was stopped). It must not subscribe anything,
                // otherwise a duplicate listener is registered and events are processed more than once.
                if (newClient.isDisconnected()) {
                    // Expected: stop() aborted this client while its handshake was in flight (TERMINATING/
                    // DISCONNECTED), e.g. during a reconnect or redeploy.
                    LOG.info("Discarding handshake reply of a Bayeux Client that was already shut down.");
                } else {
                    // Unexpected: an old client is still alive and handshaking while it is no longer the active one.
                    LOG.warn("Received a handshake reply from a stale Bayeux Client that is still active. "
                            + "Aborting the stale client.");
                }
                newClient.abort();
                future.complete(false);
                return;
            }
            if (!m.isSuccessful()) {
                Object error = m.get(ERROR);
                if (error == null) {
                    error = m.get(FAILURE);
                }
                // Do not mark the connector as stopped here: CometD keeps retrying the handshake on this client, so
                // the client is still active and must remain reachable by stop()/reconnect().
                future.completeExceptionally(
                        new ConnectException(String.format("Cannot connect [%s] : %s", parameters.endpoint(), error)));
            } else {
                subscriptions.forEach(subscription -> subscription.subscribe(newClient));
                future.complete(true);
            }
        };
        // The terminated check, publishing the client and starting the handshake are atomic with respect to stop():
        // either stop() runs first and this client is never published, or the handshake has started before stop()
        // takes and aborts the client. abort() does nothing on a client that has not started its handshake, so stop()
        // must not see the client in between.
        BayeuxClient replaced;
        synchronized (lifecycleLock) {
            if (terminated) {
                // A final stop ran while this connect() was in progress (for example during the re-login in
                // bearerToken()). Never publish or start a client for a stopped connector.
                running.set(false);
                future.complete(false);
                return future;
            }
            replaced = client;
            client = newClient;
            newClient.handshake(onHandshake);
        }
        if (replaced != null) {
            // Not expected: every path clears the client before connecting. Never leave a replaced client running.
            LOG.warn("Replacing a Bayeux Client that is still active. Shutting it down.");
            replaced.abort();
        }
        return future;
    }

    private void addListeners(BayeuxClient client) {
        for (MessageListenerInfo info : listenerInfos) {
            client.getChannel(info.getChannelName()).addListener(info.getMessageListener());
        }
    }

    private String bearerToken() {
        String bearerToken;
        if (bearerTokenProvider != null) {
            bearerToken = bearerTokenProvider.apply(reauthenticate.get());
            reauthenticate.compareAndSet(true, false);
        } else {
            bearerToken = parameters.bearerToken();
        }
        return bearerToken;
    }

    /**
     * Replace the given client with a new one, if it is still the current client.
     *
     * @param failed the client that reported the authentication failure
     */
    private void reconnect(ClientSession failed) {
        BayeuxClient current;
        synchronized (lifecycleLock) {
            if (terminated || client != failed) {
                // The connector was stopped, or the failing client was already replaced (for example by a concurrent
                // reconnect). Reconnecting now would tear down a healthy client or start one for a stopped connector.
                return;
            }
            current = client;
            client = null;
        }
        reauthenticate.set(true);
        LOG.info("Forcefully shutting down Bayeux Client in EmpConnector");
        current.abort();
        if (!current.waitFor(5000, BayeuxClient.State.DISCONNECTED)) {
            LOG.warn("Bayeux Client did not disconnect within 5 seconds! Forcing shutdown...");
        }
        LOG.info("Reconnecting to the Bayeux Client...");
        connect();
    }

    /**
     * Listens to /meta/connect channel messages and handles 401 errors, where client needs
     * to reauthenticate.
     */
    private class AuthFailureListener implements ClientSessionChannel.MessageListener {

        private static final String ERROR_401 = "401";
        private static final String ERROR_403 = "403";

        @Override
        public void onMessage(ClientSessionChannel channel, Message message) {

            ClientSession session = channel.getSession();
            if (session != client) {
                // Ignore failures reported by a client that has already been replaced or stopped; reconnecting on
                // its behalf would tear down the healthy current client.
                return;
            }
            if (!message.isSuccessful()) {
                if (isError(message, ERROR_401) || isError(message, ERROR_403)) {
                    try {
                        reconnect(session);
                    } catch (Exception e) {
                        LOG.warn("Failed to reconnect ", e);
                        if (failureListener != null) {
                            failureListener.onConnectionFailure();
                        }
                    }
                }
            }
        }

        private boolean isError(Message message, String errorCode) {
            String error = (String) message.get(Message.ERROR_FIELD);
            String failureReason = getFailureReason(message);
            return (error != null && error.startsWith(errorCode)) || (failureReason != null && failureReason
                    .startsWith(errorCode));
        }

        private String getFailureReason(Message message) {
            String failureReason = null;
            Map<String, Object> ext = message.getExt();
            if (ext != null) {
                Map<String, Object> sfdc = (Map<String, Object>) ext.get("sfdc");
                if (sfdc != null) {
                    failureReason = (String) sfdc.get("failureReason");
                }
            }
            return failureReason;
        }
    }

    private class MessageListenerInfo {

        private String channelName;
        private ClientSessionChannel.MessageListener messageListener;

        MessageListenerInfo(String channelName, ClientSessionChannel.MessageListener messageListener) {
            this.channelName = channelName;
            this.messageListener = messageListener;
        }

        String getChannelName() {
            return channelName;
        }

        ClientSessionChannel.MessageListener getMessageListener() {
            return messageListener;
        }
    }
}
