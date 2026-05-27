/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.carbon.inbound.salesforce.poll;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.BasicConfigurator;
import org.apache.synapse.SynapseException;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.registry.AbstractRegistry;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.Assert;
import org.testng.IObjectFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.ObjectFactory;
import org.testng.annotations.Test;
import org.wso2.carbon.context.CarbonContext;
import org.wso2.carbon.context.PrivilegedCarbonContext;

import java.io.InputStream;
import java.util.Properties;

import static org.mockito.MockitoAnnotations.initMocks;
import static org.powermock.api.mockito.PowerMockito.spy;

@PowerMockIgnore({"javax.net.ssl.*", "jdk.internal.*"})
@RunWith(PowerMockRunner.class)
@PrepareForTest({StringUtils.class, PrivilegedCarbonContext.class, CarbonContext.class})
@SuppressStaticInitializationFor
public class SalesforceStreamDataTest extends PowerMockTestCase {

    private SalesforceStreamData salesforceStreamData;
    private SynapseEnvironment synapseEnvironment;
    private final Log LOG = LogFactory.getLog(SalesforceStreamDataTest.class);

    @ObjectFactory
    public IObjectFactory getObjectFactory() {
        return new org.powermock.modules.testng.PowerMockObjectFactory();
    }

    @BeforeMethod
    public void setUp() {
        BasicConfigurator.configure();
        initMocks(this);
    }

    @Mock
    LoginHelper loginHelper;

    @Test(description = "read id from given file and subscribed to platform event")
    public void testFile() throws Exception {
        setupMocks();
        Properties properties = new Properties();
        properties.setProperty("inbound.behavior", "polling");
        properties.setProperty("interval", "1000");
        properties.setProperty("sequential", "true");
        properties.setProperty("coordination", "true");
        properties.setProperty("connection.salesforce.replay", "true");
        properties.setProperty("connection.salesforce.packageVersion", "37.0");
        properties.setProperty("connection.salesforce.waitTime", "5000");
        properties.setProperty("connection.salesforce.connectionTimeout", "20000");
        properties.setProperty("connection.salesforce.soapApiVersion", "22.0");
        loadPropertiesFromFile(properties);
        // Fall back to dummy values when Property.properties is empty (e.g. CI).
        // Fill Property.properties with real credentials to test against a live org.
        if (StringUtils.isEmpty(properties.getProperty("connection.salesforce.userName"))) {
            properties.setProperty("connection.salesforce.userName", "testUser");
        }
        if (StringUtils.isEmpty(properties.getProperty("connection.salesforce.password"))) {
            properties.setProperty("connection.salesforce.password", "testPass");
        }
        if (StringUtils.isEmpty(properties.getProperty("connection.salesforce.salesforceObject"))) {
            properties.setProperty("connection.salesforce.salesforceObject", "/topic/TestTopic");
        }
        properties.setProperty("connection.salesforce.replay", "true");

        salesforceStreamData = spy(new SalesforceStreamData(properties, "SaleforceInboundEP", synapseEnvironment, 100, "test", "fault", true, true));
        PowerMockito.whenNew(LoginHelper.class).withAnyArguments().thenReturn(loginHelper);
        Assert.assertNull(salesforceStreamData.poll());
        salesforceStreamData.destroy();
    }

    @Test(description = "Subscribed to platform event and receive from current event")
    public void testreplayOff() throws Exception {
        setupMocks();
        Properties properties = new Properties();
        properties.setProperty("inbound.behavior", "polling");
        properties.setProperty("interval", "1000");
        properties.setProperty("sequential", "true");
        properties.setProperty("coordination", "true");
        properties.setProperty("connection.salesforce.packageVersion", "37.0");
        properties.setProperty("connection.salesforce.waitTime", "5000");
        properties.setProperty("connection.salesforce.connectionTimeout", "20000");
        properties.setProperty("connection.salesforce.soapApiVersion", "22.0");
        loadPropertiesFromFile(properties);
        // Fall back to dummy values when Property.properties is empty (e.g. CI).
        // Fill Property.properties with real credentials to test against a live org.
        if (StringUtils.isEmpty(properties.getProperty("connection.salesforce.userName"))) {
            properties.setProperty("connection.salesforce.userName", "testUser");
        }
        if (StringUtils.isEmpty(properties.getProperty("connection.salesforce.password"))) {
            properties.setProperty("connection.salesforce.password", "testPass");
        }
        if (StringUtils.isEmpty(properties.getProperty("connection.salesforce.salesforceObject"))) {
            properties.setProperty("connection.salesforce.salesforceObject", "/topic/TestTopic");
        }
        properties.setProperty("connection.salesforce.replay", "false");

        salesforceStreamData = spy(new SalesforceStreamData(properties, "SaleforceInboundEP", synapseEnvironment, 100, "test", "fault", true, true));
        PowerMockito.whenNew(LoginHelper.class).withAnyArguments().thenReturn(loginHelper);
        Assert.assertNull(salesforceStreamData.poll());
    }

    @Test(description = "OAuth2 client credentials flow - poll() completes without throwing. "
            + "With real credentials in Property.properties, this exercises a live token exchange and channel subscription.")
    public void testOAuthClientCredentials() throws Exception {
        setupMocks();
        Properties properties = new Properties();
        properties.setProperty("inbound.behavior", "polling");
        properties.setProperty("interval", "1000");
        properties.setProperty("sequential", "true");
        properties.setProperty("coordination", "true");
        properties.setProperty("connection.salesforce.packageVersion", "37.0");
        properties.setProperty("connection.salesforce.waitTime", "5000");
        properties.setProperty("connection.salesforce.connectionTimeout", "20000");
        properties.setProperty("connection.salesforce.soapApiVersion", "22.0");
        properties.setProperty("connection.salesforce.authenticationType", "oauth");
        loadPropertiesFromFile(properties);
        // Fall back to dummy values when Property.properties is empty (e.g. CI).
        // Fill Property.properties with real OAuth credentials to test against a live org.
        if (StringUtils.isEmpty(properties.getProperty("connection.salesforce.clientId"))) {
            properties.setProperty("connection.salesforce.clientId", "dummyClientId");
        }
        if (StringUtils.isEmpty(properties.getProperty("connection.salesforce.clientSecret"))) {
            properties.setProperty("connection.salesforce.clientSecret", "dummyClientSecret");
        }
        if (StringUtils.isEmpty(properties.getProperty("connection.salesforce.salesforceObject"))) {
            properties.setProperty("connection.salesforce.salesforceObject", "/topic/TestTopic");
        }
        // Use a non-routable endpoint in CI so the test fails fast without making external network calls.
        if (StringUtils.isEmpty(properties.getProperty("connection.salesforce.tokenEndpoint"))) {
            properties.setProperty("connection.salesforce.tokenEndpoint", "http://localhost:0/token");
        }

        salesforceStreamData = spy(new SalesforceStreamData(properties, "OAuthInboundEP", synapseEnvironment, 100, "test", "fault", true, true));
        // poll() always returns null by design — it is a listener-based inbound; events are pushed via the consumer callback.
        Assert.assertNull(salesforceStreamData.poll());
    }

    // -------------------------------------------------------------------------
    // Authentication type validation tests
    // -------------------------------------------------------------------------

    @Test(description = "No authenticationType set defaults to username-token with valid credentials")
    public void testDefaultAuthTypeUsernameToken() throws Exception {
        setupMocks();
        Properties properties = buildBaseProperties();
        properties.setProperty("connection.salesforce.userName", "testUser");
        properties.setProperty("connection.salesforce.password", "testPass");

        Assert.assertNotNull(buildStreamData(properties));
    }

    @Test(description = "Explicit username-token authenticationType with valid credentials succeeds")
    public void testExplicitUsernameTokenAuthType() throws Exception {
        setupMocks();
        Properties properties = buildBaseProperties();
        properties.setProperty("connection.salesforce.authenticationType", "username-token");
        properties.setProperty("connection.salesforce.userName", "testUser");
        properties.setProperty("connection.salesforce.password", "testPass");

        Assert.assertNotNull(buildStreamData(properties));
    }

    @Test(description = "oauth authenticationType with valid clientId and clientSecret succeeds")
    public void testOauthAuthTypeWithValidCredentials() throws Exception {
        setupMocks();
        Properties properties = buildBaseProperties();
        properties.setProperty("connection.salesforce.authenticationType", "oauth");
        properties.setProperty("connection.salesforce.clientId", "testClientId");
        properties.setProperty("connection.salesforce.clientSecret", "testClientSecret");

        Assert.assertNotNull(buildStreamData(properties));
    }

    @Test(description = "Auth type value is trimmed and lowercased before validation")
    public void testAuthTypeNormalization() throws Exception {
        setupMocks();
        Properties properties = buildBaseProperties();
        properties.setProperty("connection.salesforce.authenticationType", "  OAuth  ");
        properties.setProperty("connection.salesforce.clientId", "testClientId");
        properties.setProperty("connection.salesforce.clientSecret", "testClientSecret");

        Assert.assertNotNull(buildStreamData(properties));
    }

    @Test(expectedExceptions = SynapseException.class,
            description = "Unknown authenticationType throws SynapseException immediately")
    public void testInvalidAuthTypeThrowsException() throws Exception {
        setupMocks();
        Properties properties = buildBaseProperties();
        properties.setProperty("connection.salesforce.authenticationType", "invalid-type");
        properties.setProperty("connection.salesforce.userName", "testUser");
        properties.setProperty("connection.salesforce.password", "testPass");

        buildStreamData(properties);
    }

    @Test(expectedExceptions = SynapseException.class,
            description = "username-token without userName throws SynapseException")
    public void testUsernameTokenMissingUserNameThrowsException() throws Exception {
        setupMocks();
        Properties properties = buildBaseProperties();
        properties.setProperty("connection.salesforce.authenticationType", "username-token");
        properties.setProperty("connection.salesforce.password", "testPass");

        buildStreamData(properties);
    }

    @Test(expectedExceptions = SynapseException.class,
            description = "username-token without password throws SynapseException")
    public void testUsernameTokenMissingPasswordThrowsException() throws Exception {
        setupMocks();
        Properties properties = buildBaseProperties();
        properties.setProperty("connection.salesforce.authenticationType", "username-token");
        properties.setProperty("connection.salesforce.userName", "testUser");

        buildStreamData(properties);
    }

    @Test(expectedExceptions = SynapseException.class,
            description = "oauth without clientId throws SynapseException")
    public void testOauthMissingClientIdThrowsException() throws Exception {
        setupMocks();
        Properties properties = buildBaseProperties();
        properties.setProperty("connection.salesforce.authenticationType", "oauth");
        properties.setProperty("connection.salesforce.clientSecret", "testClientSecret");

        buildStreamData(properties);
    }

    @Test(expectedExceptions = SynapseException.class,
            description = "oauth without clientSecret throws SynapseException")
    public void testOauthMissingClientSecretThrowsException() throws Exception {
        setupMocks();
        Properties properties = buildBaseProperties();
        properties.setProperty("connection.salesforce.authenticationType", "oauth");
        properties.setProperty("connection.salesforce.clientId", "testClientId");

        buildStreamData(properties);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void setupMocks() throws Exception {
        System.setProperty("carbon.home", ".");
        PowerMockito.mockStatic(PrivilegedCarbonContext.class);
        PowerMockito.mockStatic(CarbonContext.class);
        AbstractRegistry registry = Mockito.mock(AbstractRegistry.class);
        SynapseConfiguration config = Mockito.mock(SynapseConfiguration.class);
        Mockito.when(config.getRegistry()).thenReturn(registry);
        this.synapseEnvironment = Mockito.mock(SynapseEnvironment.class);
        Mockito.when(synapseEnvironment.getSynapseConfiguration()).thenReturn(config);
        PrivilegedCarbonContext privilegedCarbonContext = Mockito.mock(PrivilegedCarbonContext.class);
        PowerMockito.when(PrivilegedCarbonContext.getThreadLocalCarbonContext()).thenReturn(privilegedCarbonContext);
    }

    private Properties buildBaseProperties() {
        Properties properties = new Properties();
        properties.setProperty("inbound.behavior", "polling");
        properties.setProperty("interval", "1000");
        properties.setProperty("sequential", "true");
        properties.setProperty("coordination", "true");
        properties.setProperty("connection.salesforce.salesforceObject", "/topic/TestTopic");
        properties.setProperty("connection.salesforce.packageVersion", "37.0");
        properties.setProperty("connection.salesforce.connectionTimeout", "10000");
        properties.setProperty("connection.salesforce.waitTime", "5000");
        properties.setProperty("connection.salesforce.soapApiVersion", "22.0");
        return properties;
    }

    private SalesforceStreamData buildStreamData(Properties properties) {
        return new SalesforceStreamData(properties, "TestEP", synapseEnvironment, 100, "testSeq", "faultSeq", true, true);
    }

    public void loadPropertiesFromFile(Properties prop) {
        Properties properties = new Properties();
        try {
            InputStream input = getClass().getClassLoader().getResourceAsStream("Property.properties");
            properties.load(input);
            prop.setProperty("connection.salesforce.userName", properties.getProperty("userName"));
            prop.setProperty("connection.salesforce.EventIDStoredFilePath", properties.getProperty("EventIDStoredFilePath"));
            prop.setProperty("connection.salesforce.salesforceObject", properties.getProperty("salesforceObject"));
            prop.setProperty("connection.salesforce.loginEndpoint", properties.getProperty("loginEndpoint"));
            prop.setProperty("connection.salesforce.password", properties.getProperty("password"));
            prop.setProperty("connection.salesforce.clientId", properties.getProperty("clientId"));
            prop.setProperty("connection.salesforce.clientSecret", properties.getProperty("clientSecret"));
            prop.setProperty("connection.salesforce.tokenEndpoint", properties.getProperty("tokenEndpoint"));
            input.close();
        } catch (Exception e) {
            LOG.error("Properties reading failed  :", e);
        }
    }
}
