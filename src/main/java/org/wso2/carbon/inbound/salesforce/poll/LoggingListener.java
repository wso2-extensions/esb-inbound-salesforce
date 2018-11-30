/*
 * Copyright (c) 2016, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.TXT file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */
package org.wso2.carbon.inbound.salesforce.poll;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cometd.bayeux.Message;
import org.cometd.bayeux.client.ClientSessionChannel;

public class LoggingListener implements ClientSessionChannel.MessageListener {

    private static final Log LOG = LogFactory.getLog(LoggingListener.class);
    private boolean logSuccess;
    private boolean logFailure;

    public LoggingListener(boolean logSuccess, boolean logFailure) {

        this.logSuccess = logSuccess;
        this.logFailure = logFailure;
    }

    @Override
    public void onMessage(ClientSessionChannel clientSessionChannel, Message message) {

        if (logSuccess && message.isSuccessful()) {
            if (LOG.isDebugEnabled()) {
                LOG.info("Success:[" + clientSessionChannel.getId() + "] " + message);
            }
        }
        if (logFailure && !message.isSuccessful()) {
            if (LOG.isDebugEnabled()) {
                LOG.info("Failure:[" + clientSessionChannel.getId() + "] " + message);
            }
        }
    }
}
