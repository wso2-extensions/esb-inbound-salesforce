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
import java.io.InputStream;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.powermock.api.mockito.PowerMockito.spy;
import java.util.Properties;
import org.apache.synapse.core.SynapseEnvironment;
import org.wso2.carbon.context.CarbonContext;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.core.axis2.Axis2SynapseEnvironment;

@PowerMockIgnore({"javax.net.ssl.*"})
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

        System.setProperty("carbon.home", ".");

        PowerMockito.mockStatic(PrivilegedCarbonContext.class);
        PowerMockito.mockStatic(CarbonContext.class);
        SynapseConfiguration config = new SynapseConfiguration();
        this.synapseEnvironment = new Axis2SynapseEnvironment(config);
        PrivilegedCarbonContext privilegedCarbonContext = Mockito.mock(PrivilegedCarbonContext.class);

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
        properties.setProperty("connection.salesforce.replay","true");

        PowerMockito.when(PrivilegedCarbonContext.getThreadLocalCarbonContext()).thenReturn(privilegedCarbonContext);
        salesforceStreamData = spy(new SalesforceStreamData(properties, "SaleforceInboundEP", synapseEnvironment, 100, "test", "fault", true, true));

        PowerMockito.whenNew(LoginHelper.class).withAnyArguments().thenReturn(loginHelper);
        Assert.assertNull(salesforceStreamData.poll());
        salesforceStreamData.destroy();
    }

    @Test(description = "Subscribed to platform event and recieve from current event")
    public void testreplayOff() throws Exception {

        System.setProperty("carbon.home", ".");
        PowerMockito.mockStatic(PrivilegedCarbonContext.class);
        PowerMockito.mockStatic(CarbonContext.class);
        SynapseConfiguration config = new SynapseConfiguration();
        this.synapseEnvironment = new Axis2SynapseEnvironment(config);
        PrivilegedCarbonContext privilegedCarbonContext = Mockito.mock(PrivilegedCarbonContext.class);

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
        properties.setProperty("connection.salesforce.replay","false");

        PowerMockito.when(PrivilegedCarbonContext.getThreadLocalCarbonContext()).thenReturn(privilegedCarbonContext);
        salesforceStreamData = spy(new SalesforceStreamData(properties, "SaleforceInboundEP", synapseEnvironment, 100, "test", "fault", true, true));
        PowerMockito.whenNew(LoginHelper.class).withAnyArguments().thenReturn(loginHelper);
        Assert.assertNull(salesforceStreamData.poll());
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
            input.close();
        } catch (Exception e) {
            LOG.error("Properties reading failed  :", e);
        }
    }
}
