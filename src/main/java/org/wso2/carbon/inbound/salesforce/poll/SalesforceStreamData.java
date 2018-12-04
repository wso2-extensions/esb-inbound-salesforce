/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *   WSO2 Inc. licenses this file to you under the Apache License,
 *   Version 2.0 (the "License"); you may not use this file except
 *   in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.wso2.carbon.inbound.salesforce.poll;

import static org.cometd.bayeux.Channel.META_CONNECT;
import static org.cometd.bayeux.Channel.META_DISCONNECT;
import static org.cometd.bayeux.Channel.META_HANDSHAKE;
import static org.cometd.bayeux.Channel.META_SUBSCRIBE;
import static org.cometd.bayeux.Channel.META_UNSUBSCRIBE;
import static org.wso2.carbon.inbound.salesforce.poll.SalesforceDataHolderObject.setProperties;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.InputMismatchException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.SynapseException;
import org.apache.synapse.core.SynapseEnvironment;
import org.eclipse.jetty.util.ajax.JSON;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.inbound.endpoint.protocol.generic.GenericPollingConsumer;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.service.RegistryService;
import org.wso2.carbon.identity.core.util.IdentityTenantUtil;

/**
 * Salesforce streaming api Inbound.
 */
public class SalesforceStreamData extends GenericPollingConsumer {

    private static final Log LOG = LogFactory.getLog(SalesforceStreamData.class);
    // Mandatory parameters.
    private String loginEndpoint;
    private String userName;
    private String password;
    private String salesforceObject;
    private String streamingEndpointUri;
    private String injectingSeq;
    private long replayFromOption;
    // optional parameters.
    private int connectionTimeout;
    private int waitTime;
    private boolean isPolled = false;
    private String tenantDomain;
    private final RegistryService registryService = IdentityTenantUtil.getRegistryService();
    private EmpConnector connector;

    public SalesforceStreamData(Properties salesforceProperties, String name, SynapseEnvironment synapseEnvironment,
                                long scanInterval, String injectingSeq, String onErrorSeq, boolean coordination,
                                boolean sequential) {
        super(salesforceProperties, name, synapseEnvironment, scanInterval, injectingSeq, onErrorSeq, coordination,
                sequential);
        setProperties(salesforceProperties);
        tenantDomain = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantDomain();
        loadMandatoryParameters(salesforceProperties);
        loadOptionalParameters(salesforceProperties);
        streamingEndpointUri = loginEndpoint;
        this.injectingSeq = injectingSeq;
    }

    /** Read event id to replay from specific file. */
    private static long readFromGivenFile(String filePath) {
        String str;
        BufferedReader bufferedReader = null;
        try {
            bufferedReader = new BufferedReader(new FileReader(filePath));
            str = bufferedReader.readLine();
            if (str != null && !str.isEmpty()) {
                try {
                    return Long.parseLong(str);
                } catch (NumberFormatException e) {
                    LOG.warn("Event id is not a number. Default id used");
                }
            } else
                LOG.warn("Event id not specified in the file. Default id used");
            return SalesforceConstant.REPLAY_FROM_TIP;
        } catch (IOException e) {
            if (LOG.isDebugEnabled()) {
                LOG.error("Unable to read file from given path", e);
            }
        } finally {
            try {
                if (bufferedReader != null) {
                    bufferedReader.close();
                }
            } catch (IOException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.error("Unable to close resources", e);
                }
            }
        }
        LOG.warn("Failed to read id. Default event id will use");
        return SalesforceConstant.REPLAY_FROM_TIP;
    }

    /** Store event id to replay in the config registry DB. */
    private void updateRegistryEventID(long id) {
        startTenantFlow(tenantDomain);
        try {
            Registry registry = getRegistryForTenant(tenantDomain);
            if (registry.resourceExists(SalesforceConstant.RESOURCE_PATH)) {
                registry.beginTransaction();
                Resource resource = registry.get(SalesforceConstant.RESOURCE_PATH);
                resource.setProperty(SalesforceConstant.PROPERTY_NAME, "" + id);
                registry.put(SalesforceConstant.RESOURCE_PATH, resource);
                registry.commitTransaction();
            } else {
                LOG.warn("Resource not exists.Please create resource");
            }
        } catch (RegistryException e) {
            LOG.error("Unable to read resource eventID from " + SalesforceConstant.RESOURCE_PATH);
        } finally {
            PrivilegedCarbonContext.endTenantFlow();
        }
    }

    /** Read event id to replay from registry DB.*/
    private long getRegistryEventID() {
        startTenantFlow(tenantDomain);
        long eventIDFromDB;
        try {
            Registry registry = getRegistryForTenant(tenantDomain);
            if (registry.resourceExists(SalesforceConstant.RESOURCE_PATH)
                    && registry.get(SalesforceConstant.RESOURCE_PATH) != null
                    && registry.get(SalesforceConstant.RESOURCE_PATH).getProperty(SalesforceConstant.PROPERTY_NAME)
                    != null && !registry.get(SalesforceConstant.RESOURCE_PATH)
                    .getProperty(SalesforceConstant.PROPERTY_NAME).isEmpty()) {
                try {
                    eventIDFromDB = Long.parseLong(
                            registry.get(SalesforceConstant.RESOURCE_PATH).getProperty(SalesforceConstant.PROPERTY_NAME));
                } catch (NumberFormatException e) {
                    eventIDFromDB = SalesforceConstant.REPLAY_FROM_TIP;
                    LOG.warn("Event id mentioned in the registry property is not a number. Default id used to retrieve events");
                }

            } else {
                LOG.warn("Event id not specified in the resource in registry db. Default id used");
                eventIDFromDB = SalesforceConstant.REPLAY_FROM_TIP;
            }
            return eventIDFromDB;
        } catch (RegistryException e) {
            LOG.error("Unable to get the property eventID from the resource " + SalesforceConstant.RESOURCE_PATH, e);
        } finally {
            PrivilegedCarbonContext.endTenantFlow();
        }
        return SalesforceConstant.REPLAY_FROM_TIP;
    }

    /** Tenant flow start to get tenant domain. */
    private void startTenantFlow(String tenantDomain) {
        PrivilegedCarbonContext.startTenantFlow();
        PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain, true);
    }

    /** Get registry for the tanant. */
    private Registry getRegistryForTenant(String tenantDomain) throws RegistryException {
        int tenantId = IdentityTenantUtil.getTenantId(tenantDomain);
        return registryService.getConfigSystemRegistry(tenantId);
    }

    /** Connecting to Salesforce and listning to events*/
    private void makeConnect() throws Throwable {
        Consumer<Map<String, Object>> consumer = event -> injectSalesforceMessage(JSON.toString(event),
                (Long) ((HashMap) event.
                        get(SalesforceConstant.EVENT)).get(SalesforceConstant.REPLAY_ID));
        BearerTokenProvider tokenProvider = new BearerTokenProvider(() -> {
            try {
                return LoginHelper.login(new URL(streamingEndpointUri), userName, password);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        BayeuxParameters params = tokenProvider.login();
        connector = new EmpConnector(params);
        LoggingListener loggingListener = new LoggingListener(true, true);
        connector.addListener(META_HANDSHAKE, loggingListener).addListener(META_CONNECT, loggingListener)
                .addListener(META_DISCONNECT, loggingListener).addListener(META_SUBSCRIBE, loggingListener)
                .addListener(META_UNSUBSCRIBE, loggingListener);
        connector.setBearerTokenProvider(tokenProvider);
        if(connector.isConnected()){connector.stop();}
        connector.start().get(waitTime, TimeUnit.MILLISECONDS);
        TopicSubscription subscription;
        try {
            subscription = connector.subscribe(salesforceObject, replayFromOption, consumer)
                    .get(waitTime, TimeUnit.MILLISECONDS);
            LOG.info("Subscribed: " + subscription);
        } catch (ExecutionException e) {
            LOG.error("Unable to subscribed for the event/topic", e);
        } catch (TimeoutException e) {
            LOG.error("Timed out subscribing", e);
        } catch (InterruptedException e) {
            LOG.error("Unexpected error occured while subscribing to event/topic", e);
        }
    }

    /**
     * load essential property for salesforce inbound endpoint.
     *
     * @param properties The mandatory parameters of Salesforce.
     */
    private void loadMandatoryParameters(Properties properties) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting to load the salesforce credentials");
        }
        userName = properties.getProperty(SalesforceConstant.USER_NAME);
        salesforceObject = properties.getProperty(SalesforceConstant.SOBJECT);
        password = properties.getProperty(SalesforceConstant.PASSWORD);
        loginEndpoint = properties.getProperty(SalesforceConstant.LOGIN_ENDPOINT);
        String packageVersion = properties.getProperty(SalesforceConstant.PACKAGE_VERSION);
        if (StringUtils.isEmpty(userName) && StringUtils.isEmpty(salesforceObject) && StringUtils.isEmpty(password)
                && StringUtils.isEmpty(loginEndpoint) && StringUtils.isEmpty(packageVersion)) {
            handleException("Mandatory Parameters can't be Empty...");
        }
        SalesforceDataHolderObject.setPackageVersion(packageVersion);
    }

    /**
     * Load optional parameters for salesforce inbound endpoint file.
     *
     * @param properties The Optional parameters of Salesforce.
     */
    private void loadOptionalParameters(Properties properties) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting to load the salesforce credentials");
        }
        if (properties.getProperty(SalesforceConstant.CONNECTION_TIMEOUT) == null) {
            connectionTimeout = SalesforceConstant.CONNECTION_TIMEOUT_DEFAULT;
        } else {
            try {
                connectionTimeout = Integer.parseInt(properties.getProperty(SalesforceConstant.CONNECTION_TIMEOUT));
            } catch (NumberFormatException e) {
                LOG.error("The Value should be in Number", e);
            }
        }
        SalesforceDataHolderObject.setConnectionTimeout(connectionTimeout);
        if (properties.getProperty(SalesforceConstant.WAIT_TIME) == null) {
            waitTime = SalesforceConstant.WAIT_TIME_DEFAULT;
        } else {
            try {
                waitTime = Integer.parseInt(properties.getProperty(SalesforceConstant.WAIT_TIME));
            } catch (NumberFormatException e) {
                LOG.error("The Value should be in Number", e);
            }
        }
        SalesforceDataHolderObject.setWaitTime(waitTime);
        //replay enable
        if (Boolean.parseBoolean(properties.getProperty(SalesforceConstant.REPLAY_FROM))) {

            if (properties.getProperty(SalesforceConstant.REPLAY_FROM_ID_Stored_File_Path) != null && !StringUtils.
                    isEmpty(properties.getProperty(SalesforceConstant.REPLAY_FROM_ID_Stored_File_Path))) {
                try {
                    //read id from file
                    replayFromOption = readFromGivenFile(
                            properties.getProperty(SalesforceConstant.REPLAY_FROM_ID_Stored_File_Path));
                } catch (NumberFormatException e) {
                    LOG.error("The Value should be number", e);
                }
            } else {
                //read id from  registry db
                replayFromOption = getRegistryEventID();
            }
        } else {
            replayFromOption = SalesforceConstant.REPLAY_FROM_TIP;
        }
        SalesforceDataHolderObject.setReplayFromOption(replayFromOption);
        if (properties.getProperty(SalesforceConstant.SOAP_API_VERSION) == null) {
            SalesforceDataHolderObject.setSoapApiVersion(SalesforceConstant.DEFAULT_SOAP_API_VERSION);
        } else {
            try {
                SalesforceDataHolderObject
                        .setSoapApiVersion(properties.getProperty(SalesforceConstant.SOAP_API_VERSION));
            } catch (InputMismatchException e) {
                LOG.error("The Value should be in Number", e);
            }
        }
    }

    public Object poll() {
        //Establishing connection with Salesforce streaming api.
        try {
            if (!isPolled) {
                makeConnect();
                isPolled = true;
            }
        } catch (Throwable e) {
            LOG.error("Error while setup the Salesforce connection.", e);
        }
        return null;
    }

    /**
     * Injecting the salesforce Stream messages to the ESB sequence.
     *
     * @param message the salesforce response status
     */
    private void injectSalesforceMessage(String message, long id) {
        if (injectingSeq != null) {
            if (LOG.isDebugEnabled()) {
                LOG.info("id for the event recieved: " + id);
            }
            updateRegistryEventID(id);
            injectMessage(message, SalesforceConstant.CONTENT_TYPE);
            if (LOG.isDebugEnabled()) {
                LOG.debug("injecting salesforce message to the sequence : " + injectingSeq);
            }
        } else {
            handleException("the Sequence is not found");
        }
    }

    /** handle Exception. */
    private void handleException(String msg) {
        LOG.error(msg);
        throw new SynapseException(msg);
    }

    /** stop the connector when redeploy or shutdown the server. */
    @Override
    public void destroy() {
        if (connector != null) {
            connector.stop();
        }
    }
}
