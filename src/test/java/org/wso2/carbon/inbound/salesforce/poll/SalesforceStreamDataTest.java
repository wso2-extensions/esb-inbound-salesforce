/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

import javafx.beans.binding.When;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.BasicConfigurator;
import org.eclipse.jetty.client.HttpClient;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.opensaml.xml.signature.P;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareEverythingForTest;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.testng.PowerMockTestCase;
import org.powermock.reflect.Whitebox;
import org.testng.Assert;
import org.testng.IObjectFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.ObjectFactory;
import org.testng.annotations.Test;

//import javax.net.ssl.SSLContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.spy;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;
import static org.powermock.api.mockito.PowerMockito.when;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Properties;
import java.util.Set;

import org.apache.synapse.core.SynapseEnvironment;
import org.wso2.carbon.context.CarbonContext;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.core.axis2.Axis2SynapseEnvironment;

@PowerMockIgnore({"javax.net.ssl.*"})
@RunWith(PowerMockRunner.class)
@PrepareForTest({StringUtils.class,PrivilegedCarbonContext.class,CarbonContext.class
})
@SuppressStaticInitializationFor
public class SalesforceStreamDataTest extends PowerMockTestCase {


    private SalesforceStreamData salesforceStreamData;


    private  SynapseEnvironment synapseEnvironment;





    private boolean isPolled = false;

    @ObjectFactory
    public IObjectFactory getObjectFactory() {

        return new org.powermock.modules.testng.PowerMockObjectFactory();
    }

    @BeforeMethod
    public void setUp() {


        //salesforceStreamData =mocksalesforceStreamData;
        BasicConfigurator.configure();

        //salesforceStreamData=new SalesforceStreamData(properties,"SaleforceInboundEP",synapseEnvironment,100,"test","fault",true,true);
        initMocks(this);
    }

    @Mock
    HttpClient httpClient;
    @Mock
    BayeuxParameters bayeuxParameters;
    @Mock
    LoginHelper loginHelper;


    @Test(description = "read id from given file and subscribed to platform event")
    public void testFile() throws Exception {
        //Assert.assertNotNull(Whitebox.invokeMethod(salesforceStreamData, "readFromGivenFile", ""));

        //when(anyBoolean()).thenReturn(false);
       //Assert.assertEquals(salesforceStreamData.poll(),null);
        //salesforceStreamData=spy(mocksalesforceStreamData);
        //mockStatic(SalesforceStreamData.class);
        //Whitebox.invokeMethod(salesforceStreamData,"handleException","err");

        //when(privilegedCarbonContext.getThreadLocalCarbonContext().getTenantDomain()).thenReturn("carbon.super");
        //mockStatic(PrivilegedCarbonContext.class);

        //when(PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantDomain()).thenReturn("carbon.super");

        //
        System.setProperty("carbon.home", ".");

        PowerMockito.mockStatic(PrivilegedCarbonContext.class);
        PowerMockito.mockStatic(CarbonContext.class);
        SynapseConfiguration config = new SynapseConfiguration();
        this.synapseEnvironment = new Axis2SynapseEnvironment(config);
        PrivilegedCarbonContext privilegedCarbonContext=Mockito.mock(PrivilegedCarbonContext.class);

        Properties properties=new Properties();
        properties.setProperty("inbound.behavior","polling");
        properties.setProperty("interval","1000");
        properties.setProperty("sequential","true");
        properties.setProperty("coordination","true");
        properties.setProperty("connection.salesforce.replay","true");
        properties.setProperty("connection.salesforce.EventIDStoredFilePath","/Users/nalaka/Desktop/a.txt");
        properties.setProperty("connection.salesforce.packageVersion","37.0");
        properties.setProperty("connection.salesforce.salesforceObject","/event/InvoiceStatementReading1s__e");
        properties.setProperty("connection.salesforce.loginEndpoint","dummy");
        properties.setProperty("connection.salesforce.userName","dummy");
        properties.setProperty("connection.salesforce.password","dummy");
        properties.setProperty("connection.salesforce.waitTime","5000");
        properties.setProperty("connection.salesforce.connectionTimeout","20000");
        properties.setProperty("connection.salesforce.soapApiVersion","22.0");


        //when(PrivilegedCarbonContext.getThreadLocalCarbonContext()).thenReturn(privilegedCarbonContext);
        PowerMockito.when(PrivilegedCarbonContext.getThreadLocalCarbonContext()).thenReturn(privilegedCarbonContext);
        //BayeuxParameters b=PowerMockito.mock(BayeuxParameters.class);
        //LoginHelper l=PowerMockito.mock(LoginHelper.class);
        //URL url = PowerMockito.mock(URL.class);
        //PowerMockito.when(LoginHelper.login(new URL("dummy"),"nalakase@wso2.com","Nsenarathna123@N28Xd5Le8UCJ6QhwVxlGHo4X")).thenReturn(b);
        //PowerMockito.mockStatic(LoginHelper.class);

       // PowerMockito.when(LoginHelper.login(url,anyString(),anyString())).then((Answer<?>) loginHelper);




        /*BearerTokenProvider tokenProvider = new BearerTokenProvider(() -> {
            try {
                return LoginHelper.login(new URL(properties.getProperty("connection.salesforce.loginEndpoint")), properties.getProperty("connection.salesforce.userName"), properties.getProperty("connection.salesforce.password"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        BayeuxParameters params = tokenProvider.login();*/



        salesforceStreamData=spy(new SalesforceStreamData(properties,"SaleforceInboundEP",synapseEnvironment,100,"test","fault",true,true));


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
        PrivilegedCarbonContext privilegedCarbonContext=Mockito.mock(PrivilegedCarbonContext.class);

        Properties properties=new Properties();
        properties.setProperty("inbound.behavior","polling");
        properties.setProperty("interval","1000");
        properties.setProperty("sequential","true");
        properties.setProperty("coordination","true");
        properties.setProperty("connection.salesforce.replay","false");
        properties.setProperty("connection.salesforce.EventIDStoredFilePath","/Users/nalaka/Desktop/a.txt");
        properties.setProperty("connection.salesforce.packageVersion","37.0");
        properties.setProperty("connection.salesforce.salesforceObject","/event/InvoiceStatementReading1s__e");
        properties.setProperty("connection.salesforce.loginEndpoint","dummy");
        properties.setProperty("connection.salesforce.userName","dummy");
        properties.setProperty("connection.salesforce.password","dummy");
        properties.setProperty("connection.salesforce.waitTime","5000");
        properties.setProperty("connection.salesforce.connectionTimeout","20000");
        properties.setProperty("connection.salesforce.soapApiVersion","22.0");

PowerMockito.when(PrivilegedCarbonContext.getThreadLocalCarbonContext()).thenReturn(privilegedCarbonContext);

        salesforceStreamData=spy(new SalesforceStreamData(properties,"SaleforceInboundEP",synapseEnvironment,100,"test","fault",true,true));
        PowerMockito.whenNew(LoginHelper.class).withAnyArguments().thenReturn(loginHelper);
        Assert.assertNull(salesforceStreamData.poll());



    }

    @Test(description = "Testing subscribtion to platform event")
    public void testSubscription() throws Exception {

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
        properties.setProperty("connection.salesforce.replay", "false");
        properties.setProperty("connection.salesforce.EventIDStoredFilePath", "/Users/nalaka/Desktop/a.txt");
        properties.setProperty("connection.salesforce.packageVersion", "37.0");
        properties.setProperty("connection.salesforce.salesforceObject", "/event/InvoiceStatementReading1s__e");
        properties.setProperty("connection.salesforce.loginEndpoint", "dummy");
        properties.setProperty("connection.salesforce.userName", "dummyUser");
        properties.setProperty("connection.salesforce.password", "Dumypassword");
        properties.setProperty("connection.salesforce.waitTime", "5000");
        properties.setProperty("connection.salesforce.connectionTimeout", "20000");
        properties.setProperty("connection.salesforce.soapApiVersion", "22.0");

        PowerMockito.when(PrivilegedCarbonContext.getThreadLocalCarbonContext()).thenReturn(privilegedCarbonContext);

        salesforceStreamData = spy(new SalesforceStreamData(properties, "SaleforceInboundEP", synapseEnvironment, 100, "test", "fault", true, true));

        PowerMockito.whenNew(LoginHelper.class).withAnyArguments().thenReturn(loginHelper);

        Assert.assertNull(salesforceStreamData.poll());
    }

}
