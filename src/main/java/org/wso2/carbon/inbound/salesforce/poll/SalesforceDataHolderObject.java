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
/**
 * Salesforce configuration data holder class.
 */
public class SalesforceDataHolderObject {

    public static int connectionTimeout;
    public int waitTime;
    public long replayFromOption;
    public static String soapApiVersion;
    public static String packageVersion;
    public Properties properties;
    public  String salesforceObject;

    /**
     * set soap API version.
     * @param soapApiVersion    version of the soap API version.
     */
    public void setSoapApiVersion(String soapApiVersion) {
        this.soapApiVersion = soapApiVersion;
    }

    /**
     * set soap API version.
     * @return  soap API version.
     */
    public static String getSoapApiVersion() {
        return soapApiVersion;
    }

    /**
     * set connection timeout.
     * @param connectionTimeout  connection timeout.
     */
    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    /**
     * Set wait time to connect.
     * @param waitTime  wait time.
     */
    public void setWaitTime(int waitTime) {
        this.waitTime = waitTime;
    }

    /**
     * Set replay option.
     * @param replayFromOption   replay option.
     */
    public void setReplayFromOption(long replayFromOption) {
        this.replayFromOption = replayFromOption;
    }

    /**
     * Set package version of salesforce.
     * @param packageVersion    package version of salesforce.
     */
    public void setPackageVersion(String packageVersion) {
        this.packageVersion = packageVersion;
    }

    /**
     * set properties.
     * @param properties    properties.
     */
    public void setProperties(Properties properties) {
        this.properties = properties;
    }

    public void setObjectName(String salesforceObject) {
        String processString=salesforceObject.trim();
            this.salesforceObject=processString.substring(7);
    }
}
