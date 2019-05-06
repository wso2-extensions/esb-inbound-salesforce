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

import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.Assert;
import org.testng.IObjectFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.ObjectFactory;
import org.testng.annotations.Test;

import static org.mockito.MockitoAnnotations.initMocks;

import java.net.URL;
import java.util.concurrent.TimeUnit;

@RunWith(PowerMockRunner.class)
@PrepareForTest({BearerTokenProvider.class, SalesforceStreamData.class})
public class BearerTokenProviderTest extends PowerMockTestCase {

    private DelegatingBayeuxParameters delegatingBayeuxParameters;

    @Mock
    private BayeuxParameters bayeuxParameters;

    @ObjectFactory
    public IObjectFactory getObjectFactory() {
        return new org.powermock.modules.testng.PowerMockObjectFactory();
    }

    @BeforeMethod
    public void setUp() {
        delegatingBayeuxParameters = new DelegatingBayeuxParameters(bayeuxParameters);
        initMocks(this);
    }

    @Test
    public void testLogging() throws Exception {
        URL url = delegatingBayeuxParameters.endpoint();
        long keepAlive = delegatingBayeuxParameters.keepAlive();
        TimeUnit timeUnit = delegatingBayeuxParameters.keepAliveUnit();
        String packageVersion = delegatingBayeuxParameters.version();
        Assert.assertEquals(delegatingBayeuxParameters.bearerToken(), null);
    }
}
