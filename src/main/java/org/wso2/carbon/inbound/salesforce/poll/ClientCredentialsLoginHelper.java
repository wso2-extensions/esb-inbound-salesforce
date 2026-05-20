/*
 * Copyright (c) 2024, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.apache.commons.codec.binary.Base64;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.FormContentProvider;
import org.eclipse.jetty.util.Fields;
import org.eclipse.jetty.util.ajax.JSON;

/**
 * Helper to obtain OAuth2 access token using Client Credentials grant type.
 * Handles token exchange and provides BayeuxParameters with bearer token.
 */
public class ClientCredentialsLoginHelper {

    private static final String GRANT_TYPE = "grant_type";
    private static final String CLIENT_CREDENTIALS = "client_credentials";
    private static final String CLIENT_ID = "client_id";
    private static final String CLIENT_SECRET = "client_secret";
    private static final String ACCESS_TOKEN = "access_token";
    private static final String INSTANCE_URL = "instance_url";
    private static final String COMETD_REPLAY = "/cometd/";
    private static final String COMETD_REPLAY_OLD = "/cometd/replay/";

    /**
     * Obtain OAuth2 access token via Client Credentials grant and return BayeuxParameters
     */
    public static BayeuxParameters login(String clientId, String clientSecret, URL tokenEndpoint,
                                         BayeuxParameters parameters) throws Exception {
        HttpClient client = new HttpClient(parameters.sslContextFactory());
        try {
            client.getProxyConfiguration().getProxies().addAll(parameters.proxies());
            client.start();

            Fields fields = new Fields();
            fields.put(GRANT_TYPE, CLIENT_CREDENTIALS);
            fields.put(CLIENT_ID, clientId);
            fields.put(CLIENT_SECRET, clientSecret);

            Request request = client.POST(tokenEndpoint.toURI());
            request.content(new FormContentProvider(fields));
            ContentResponse response = request.send();

            if (response.getStatus() != 200) {
                throw new RuntimeException("OAuth2 token exchange failed with status " + response.getStatus() +
                        ": " + response.getContentAsString());
            }

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> tokenResponse = (java.util.Map<String, Object>) JSON.parse(
                    response.getContentAsString());

            String accessToken = (String) tokenResponse.get(ACCESS_TOKEN);
            String instanceUrl = (String) tokenResponse.get(INSTANCE_URL);

            if (accessToken == null || instanceUrl == null) {
                throw new RuntimeException("Missing access_token or instance_url in OAuth2 response");
            }

            URL instanceUrlObj = new URL(instanceUrl);
            String cometdEndpoint = Float.parseFloat(parameters.version()) < 37 ? COMETD_REPLAY_OLD : COMETD_REPLAY;
            URL replayEndpoint = new URL(instanceUrlObj.getProtocol(), instanceUrlObj.getHost(),
                    instanceUrlObj.getPort(),
                    cometdEndpoint + parameters.version());

            return new DelegatingBayeuxParameters(parameters) {
                @Override
                public String bearerToken() {
                    return accessToken;
                }

                @Override
                public URL endpoint() {
                    return replayEndpoint;
                }
            };
        } finally {
            client.stop();
            client.destroy();
        }
    }
}
