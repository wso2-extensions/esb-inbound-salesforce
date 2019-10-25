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
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.IObjectFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.ObjectFactory;
import org.testng.annotations.Test;
import static org.mockito.MockitoAnnotations.initMocks;
import org.cometd.bayeux.Message;
import org.cometd.bayeux.client.ClientSessionChannel;

@RunWith(PowerMockRunner.class)
public class LogginListnerTest extends PowerMockTestCase {
    private LoggingListener loggingListener;

    @ObjectFactory
    public IObjectFactory getObjectFactory() {
        return new org.powermock.modules.testng.PowerMockObjectFactory();
    }

    @Mock
    private ClientSessionChannel clientSessionChannel;

    @Mock
    private Message message;

    @BeforeMethod
    public void setUp() {
        initMocks(this);
    }

    @Test
    public void testOnMessage() throws Exception {
        loggingListener = new LoggingListener(true, true);
        loggingListener.onMessage(clientSessionChannel, message);
    }
}
