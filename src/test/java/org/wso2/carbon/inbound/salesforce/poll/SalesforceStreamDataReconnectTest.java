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

import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.registry.AbstractRegistry;
import org.eclipse.jetty.client.HttpClient;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Properties;

import static org.wso2.carbon.inbound.salesforce.poll.EmpConnectorTest.POLL_WINDOW_MILLIS;
import static org.wso2.carbon.inbound.salesforce.poll.EmpConnectorTest.TOPIC;
import static org.wso2.carbon.inbound.salesforce.poll.EmpConnectorTest.field;
import static org.wso2.carbon.inbound.salesforce.poll.EmpConnectorTest.waitUntil;

/**
 * Tests how the Salesforce inbound endpoint sets up and replaces its connection: re-initialisation after a connection
 * failure, and a pause (destroy() followed by resume(), as MI does when a node loses and regains coordination).
 */
public class SalesforceStreamDataReconnectTest {

    private static final long JOIN_TIMEOUT_MILLIS = 30000;

    private MockStreamingServer server;
    private SalesforceStreamData streamData;

    @BeforeMethod
    public void setUp() throws Exception {
        server = new MockStreamingServer();
        AbstractRegistry registry = Mockito.mock(AbstractRegistry.class);
        SynapseConfiguration configuration = Mockito.mock(SynapseConfiguration.class);
        Mockito.when(configuration.getRegistry()).thenReturn(registry);
        SynapseEnvironment synapseEnvironment = Mockito.mock(SynapseEnvironment.class);
        Mockito.when(synapseEnvironment.getSynapseConfiguration()).thenReturn(configuration);

        Properties properties = new Properties();
        properties.setProperty("interval", "1000");
        properties.setProperty("sequential", "true");
        properties.setProperty("coordination", "true");
        properties.setProperty(SalesforceConstant.LOGIN_ENDPOINT, server.baseUrl());
        properties.setProperty(SalesforceConstant.USER_NAME, "user1");
        properties.setProperty(SalesforceConstant.PASSWORD, "dummy-secret");
        properties.setProperty(SalesforceConstant.SOBJECT, TOPIC);
        properties.setProperty(SalesforceConstant.PACKAGE_VERSION, MockStreamingServer.COMETD_VERSION);
        properties.setProperty(SalesforceConstant.SOAP_API_VERSION, "22.0");
        properties.setProperty(SalesforceConstant.WAIT_TIME, "5000");
        properties.setProperty(SalesforceConstant.CONNECTION_TIMEOUT, "5000");
        properties.setProperty("connection.salesforce.replay", "false");
        streamData = new SalesforceStreamData(properties, "TestEP", synapseEnvironment, 1000, "testSeq", "faultSeq",
                true, true);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        try {
            streamData.destroy();
        } finally {
            server.stop();
        }
    }

    @Test(description = "After a connection failure is reported, the next poll replaces the connector and stops the "
            + "previous one")
    public void testReconnectAfterConnectionFailureStopsPreviousConnector() throws Exception {
        streamData.poll();
        waitUntil("the inbound endpoint to subscribe", () -> server.validSubscribedClients(TOPIC) == 1);
        EmpConnector first = (EmpConnector) field(streamData, "connector");

        streamData.onConnectionFailure();
        streamData.poll();

        Assert.assertNotSame(field(streamData, "connector"), first, "the connector must be replaced");
        Assert.assertTrue(((HttpClient) field(first, "httpClient")).isStopped(), "the previous connector must be stopped");
        Thread.sleep(POLL_WINDOW_MILLIS);
        Assert.assertEquals(server.pollingClients(POLL_WINDOW_MILLIS), 1,
                "only the new connector's client may stay connected");
    }

    @Test(description = "A pause while the connection is being set up leaves nothing connected, polls while paused do "
            + "not log in, and resume() connects again")
    public void testPauseWhileConnectingThenResume() throws Exception {
        // Pause (destroy) while poll() is logging in.
        server.setLoginDelay(2000);
        Thread poller = new Thread(streamData::poll, "poll");
        poller.start();
        waitUntil("the login to start", () -> server.logins() == 1);
        streamData.destroy();
        poller.join(JOIN_TIMEOUT_MILLIS);
        server.setLoginDelay(0);
        Thread.sleep(POLL_WINDOW_MILLIS);
        Assert.assertEquals(server.pollingClients(POLL_WINDOW_MILLIS), 0, "nothing may stay connected while paused");

        // Polls that run before resume() must not log in.
        int logins = server.logins();
        streamData.poll();
        streamData.poll();
        Assert.assertEquals(server.logins(), logins, "no login while paused");

        // Resume connects again.
        streamData.resume();
        streamData.poll();
        waitUntil("the inbound endpoint to subscribe after resume", () -> server.validSubscribedClients(TOPIC) == 1);
        Thread.sleep(POLL_WINDOW_MILLIS);
        Assert.assertEquals(server.pollingClients(POLL_WINDOW_MILLIS), 1, "one client after resume");
    }

}
