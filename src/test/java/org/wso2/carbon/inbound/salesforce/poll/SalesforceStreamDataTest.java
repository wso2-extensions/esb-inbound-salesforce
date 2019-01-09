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
import org.wso2.carbon.integration.common.admin.client.LogViewerClient;
import org.wso2.carbon.logging.view.stub.types.carbon.LogEvent;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.BasicConfigurator;
import org.eclipse.jetty.client.HttpClient;
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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.FileInputStream;
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
@PrepareForTest({StringUtils.class,PrivilegedCarbonContext.class,CarbonContext.class
})
@SuppressStaticInitializationFor
public class SalesforceStreamDataTest extends PowerMockTestCase {


    private SalesforceStreamData salesforceStreamData;


    private  SynapseEnvironment synapseEnvironment;



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
        PrivilegedCarbonContext privilegedCarbonContext=Mockito.mock(PrivilegedCarbonContext.class);

        Properties properties=new Properties();
        properties.setProperty("inbound.behavior","polling");
        properties.setProperty("interval","1000");
        properties.setProperty("sequential","true");
        properties.setProperty("coordination","true");
        properties.setProperty("connection.salesforce.replay","true");
        //properties.setProperty("connection.salesforce.EventIDStoredFilePath","/Users/nalaka/Desktop/a.txt");
        properties.setProperty("connection.salesforce.packageVersion","37.0");
        //properties.setProperty("connection.salesforce.salesforceObject","/event/InvoiceStatementReading1s__e");
        //properties.setProperty("connection.salesforce.loginEndpoint","https://wso2--EigSeptTen.cs62.my.salesforce.com");
        //properties.setProperty("connection.salesforce.userName","nalakase@wso2.com");
        //properties.setProperty("connection.salesforce.password","Nsenarathna123@N28Xd5Le8UCJ6QhwVxlGHo4X");
        properties.setProperty("connection.salesforce.waitTime","5000");
        properties.setProperty("connection.salesforce.connectionTimeout","20000");
        properties.setProperty("connection.salesforce.soapApiVersion","22.0");
        loadPropertiesFromFile(properties);

        //when(PrivilegedCarbonContext.getThreadLocalCarbonContext()).thenReturn(privilegedCarbonContext);
        PowerMockito.when(PrivilegedCarbonContext.getThreadLocalCarbonContext()).thenReturn(privilegedCarbonContext);




        salesforceStreamData=spy(new SalesforceStreamData(properties,"SaleforceInboundEP",synapseEnvironment,100,"test","fault",true,true));


        PowerMockito.whenNew(LoginHelper.class).withAnyArguments().thenReturn(loginHelper);

        Assert.assertNull(salesforceStreamData.poll());

        //LogViewerClient logViewerClient=new LogViewerClient(getBackEndUrl(), getSessionCookie());

        //boolean read=readLogsfromFile("Connector connecting");
        //Assert.assertTrue(read,""+read);
       // Assert.assertTrue(read);
       // boolean search1=searchLogs(logViewerClient,"20:30:43.752 [main] INFO org.wso2.carbon.inbound.salesforce.poll.EmpConnector - Connector connecting");
        //boolean search2=searchLogs(logViewerClient,"Connector connecting");
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

        properties.setProperty("connection.salesforce.EventIDStoredFilePath","/Users/nalaka/Desktop/a.txt");
        properties.setProperty("connection.salesforce.packageVersion","37.0");
        //properties.setProperty("connection.salesforce.salesforceObject","/event/InvoiceStatementReading1s__e");
        //properties.setProperty("connection.salesforce.loginEndpoint","dummy");
        //properties.setProperty("connection.salesforce.userName","dummy");
        //properties.setProperty("connection.salesforce.password","dummy");
        properties.setProperty("connection.salesforce.waitTime","5000");
        properties.setProperty("connection.salesforce.connectionTimeout","20000");
        properties.setProperty("connection.salesforce.soapApiVersion","22.0");
        loadPropertiesFromFile(properties);

PowerMockito.when(PrivilegedCarbonContext.getThreadLocalCarbonContext()).thenReturn(privilegedCarbonContext);

        salesforceStreamData=spy(new SalesforceStreamData(properties,"SaleforceInboundEP",synapseEnvironment,100,"test","fault",true,true));
        PowerMockito.whenNew(LoginHelper.class).withAnyArguments().thenReturn(loginHelper);
        Assert.assertNull(salesforceStreamData.poll());



    }

    /*@Test(description = "Testing subscribtion to platform event")
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
    }*/

    public void loadPropertiesFromFile(Properties prop)
    {
        Properties properties = new Properties();
        try
        {
            InputStream input = getClass().getClassLoader().getResourceAsStream("Property.properties");
            properties.load(input);
            prop.setProperty("connection.salesforce.userName",properties.getProperty("userName"));
            prop.setProperty("connection.salesforce.replay",properties.getProperty("replay"));
            prop.setProperty("connection.salesforce.EventIDStoredFilePath",properties.getProperty("EventIDStoredFilePath"));
            prop.setProperty("connection.salesforce.salesforceObject",properties.getProperty("salesforceObject"));
            prop.setProperty("connection.salesforce.loginEndpoint",properties.getProperty("loginEndpoint"));
            prop.setProperty("connection.salesforce.password",properties.getProperty("password"));
            input.close();
        }
        catch(IOException e)
        {
            System.out.println("Properties reading failed  :" + e.getMessage());
        }
    }

    private boolean searchLogs(LogViewerClient logViewerClient, String searchString) {
        boolean logFound = false;
        try{


        for (int i = 0; i < 60; i++) {
            LogEvent[] logEvents = logViewerClient.getAllRemoteSystemLogs();
            if (logEvents != null) {
                for (LogEvent logEvent : logEvents) {
                    if (logEvent == null) {
                        continue;
                    }
                    if (logEvent.getMessage().contains(searchString)) {
                        logFound = true;
                        break;
                    }
                }
            }
            if (logFound) {
                break;
            }
            Thread.sleep(500);
        }
        }
        catch (Exception e){
            System.out.println("Unable to load logs");
        }
        return logFound;
    }

    public boolean readLogsfromFile(String log)
    {
        boolean booleanvalue=false;
        try{
            ClassLoader classLoader = getClass().getClassLoader();
            File file = new File(classLoader.getResource("abc.txt").getFile());

            FileInputStream fstream = new FileInputStream(file);
            BufferedReader br = new BufferedReader(new InputStreamReader(fstream));
            String strLine;
            while (((strLine = br.readLine()) != null) && !booleanvalue)   {
                if(strLine.contains(log))
                {
                    booleanvalue= true;
                }

            }
            fstream.close();
        } catch (Exception e) {
            System.err.println("Unable to read from file: " + e.getMessage());
        }
        return booleanvalue;
    }
}
