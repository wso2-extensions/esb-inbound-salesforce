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
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.testng.PowerMockTestCase;
import org.powermock.reflect.Whitebox;
import org.testng.Assert;
import org.testng.IObjectFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.ObjectFactory;
import org.testng.annotations.Test;


import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.spy;
import static org.powermock.api.mockito.PowerMockito.when;

import java.net.URL;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

@RunWith(PowerMockRunner.class)
@PrepareForTest({BearerTokenProvider.class,SalesforceStreamData.class})
public class BearerTokenProviderTest extends PowerMockTestCase {


    private BearerTokenProvider bearerTokenProvider;

    private DelegatingBayeuxParameters delegatingBayeuxParameters;


    @Mock
    private BayeuxParameters bayeuxParameters;

    private Supplier<BayeuxParameters> supplier;


    @ObjectFactory
    public IObjectFactory getObjectFactory() {

        return new org.powermock.modules.testng.PowerMockObjectFactory();
    }


    @BeforeMethod
    public void setUp() {
        delegatingBayeuxParameters=new DelegatingBayeuxParameters(bayeuxParameters);
        initMocks(this);
    }

    @Test
    public void testLogging() throws Exception {
        //Assert.assertNotNull(Whitebox.invokeMethod(salesforceStreamData, "readFromGivenFile", ""));

        //when(anyBoolean()).thenReturn(false);
        //loggingListener=new LoggingListener(true,true);
        //when(message.isSuccessful()).thenReturn(true);
        //loggingListener.onMessage(clientSessionChannel,message);
        //Assert.assertNull(loggingListener);
        // Assert.assertNotNull(Whitebox.invokeMethod(salesforceStreamData,"handleException","err"));

        URL url=delegatingBayeuxParameters.endpoint();
        long keepAlive=delegatingBayeuxParameters.keepAlive();
        TimeUnit timeUnit=delegatingBayeuxParameters.keepAliveUnit();
        String packageVersion=delegatingBayeuxParameters.version();
        Assert.assertEquals(delegatingBayeuxParameters.bearerToken(),null);



    }

    @Test
    public void testStringToken() throws Exception {

    }
}
