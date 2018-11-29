/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

import java.util.Properties;

public class SalesforceDataHolderObject {

    public static int connectionTimeout;
    public static int readTimeout;
    public static int waitTime;
    public static long replayFromOption;
    public static String soapApiVersion;
    public static String packageVersion;
    public static Properties properties;

    public static void setSoapApiVersion(String soapApiVersion) {

        SalesforceDataHolderObject.soapApiVersion = soapApiVersion;
    }

    public static String getSoapApiVersion() {

        return soapApiVersion;
    }

    public static void setConnectionTimeout(int connectionTimeout) {

        SalesforceDataHolderObject.connectionTimeout = connectionTimeout;
    }

    public static void setReadTimeout(int readTimeout) {

        SalesforceDataHolderObject.readTimeout = readTimeout;
    }

    public static void setWaitTime(int waitTime) {

        SalesforceDataHolderObject.waitTime = waitTime;
    }

    public static void setReplayFromOption(long replayFromOption) {

        SalesforceDataHolderObject.replayFromOption = replayFromOption;
    }

    public static void setPackageVersion(String packageVersion) {

        SalesforceDataHolderObject.packageVersion = packageVersion;
    }

    public static void setProperties(Properties properties) {

        SalesforceDataHolderObject.properties = properties;
    }
}
