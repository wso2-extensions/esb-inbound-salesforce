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

        private SubscriptionImpl(String topic, Consumer<Map<String, Object>> consumer) {
            this.topic = topic;
            this.consumer = consumer;
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

        Future<TopicSubscription> subscribe() {
            Long replayFrom = getReplayFrom();
            ClientSessionChannel channel = client.getChannel(topic);
            CompletableFuture<TopicSubscription> future = new CompletableFuture<>();
            channel.subscribe((c, message) -> consumer.accept(message.getDataAsMap()), (c, message) -> {
                if (message.isSuccessful()) {
                    future.complete(this);
                } else {
                    Object error = message.get(ERROR);
                    if (error == null) {
                        error = message.get(FAILURE);
                    }
                    future.completeExceptionally(new CannotSubscribe(parameters.endpoint(), topic, replayFrom,
                            error != null ? error : message));
                }
            });
            return future;
        }
    }

    private static long REPLAY_FROM_EARLIEST = -2L;
    private static long REPLAY_FROM_TIP = -1L;
    private static String AUTHORIZATION = "Authorization";
    private volatile BayeuxClient client;
    private final HttpClient httpClient;
    private final BayeuxParameters parameters;
    private final ConcurrentMap<String, Long> replay = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean();
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
        if (!running.compareAndSet(true, false)) {
            return;
        }

        if (client != null) {
            LOG.info("Forcefully shutting down Bayeux Client in EmpConnector");

            // Unsubscribe from all active subscriptions
            if (terminateCompletely) {
                subscriptions.forEach(SubscriptionImpl::cancel);
            }
            // Force disconnect
            client.abort();  // Immediately terminates transport connections
            boolean disconnected = client.waitFor(5000, BayeuxClient.State.DISCONNECTED);

            if (!disconnected) {
                LOG.warn("Bayeux Client did not disconnect within 5 seconds! Forcing shutdown...");
            }

            client = null;
        }

        try {
            if (terminateCompletely && httpClient != null) {
                httpClient.stop(); // Stop the HTTP client
                LOG.info("HTTP client stopped successfully.");
            }
            httpClient.destroy(); // Destroy all resources
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
        return subscription.subscribe();
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
        LOG.info("Connector connecting");
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        try {
            httpClient.start();
        } catch (Exception e) {
            LOG.error("Unable to start HTTP transport[{}]", parameters.endpoint(), e);
            running.set(false);
            future.complete(false);
            return future;
        }
        String bearerToken = bearerToken();
        LongPollingTransport httpTransport = new LongPollingTransport(parameters.longPollingOptions(), httpClient) {
            @Override
            protected void customize(Request request) {

                request.header(AUTHORIZATION, bearerToken);
            }
        };
        client = new BayeuxClient(parameters.endpoint().toExternalForm(), httpTransport);
        client.addExtension(new ReplayExtension(replay));
        addListeners(client);
        client.handshake((c, m) -> {
            if (!m.isSuccessful()) {
                Object error = m.get(ERROR);
                if (error == null) {
                    error = m.get(FAILURE);
                }
                future.completeExceptionally(
                        new ConnectException(String.format("Cannot connect [%s] : %s", parameters.endpoint(), error)));
                running.set(false);
            } else {
                subscriptions.forEach(SubscriptionImpl::subscribe);
                future.complete(true);
            }
        });
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

    private void reconnect() {
        stop();
        if (running.compareAndSet(false, true)) {
            LOG.info("Reconnecting to the Bayeux Client...");
            connect();
        } else {
            LOG.error("The current value of running is not as we expect, this means our reconnection may not happen");
        }
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

            if (!message.isSuccessful()) {
                if (isError(message, ERROR_401) || isError(message, ERROR_403)) {
                    reauthenticate.set(true);
                    try {
                        reconnect();
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
