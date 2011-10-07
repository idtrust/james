/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.pop3server.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.james.pop3server.POP3Response;
import org.apache.james.pop3server.POP3Session;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.CommandHandler;

/**
 * Handles STAT command
 */
public class StatCmdHandler implements CommandHandler<POP3Session> {
    private final static String COMMAND_NAME = "STAT";

    /**
     * Handler method called upon receipt of a STAT command. Returns the number
     * of messages in the mailbox and its aggregate size.
     */
    @SuppressWarnings("unchecked")
    public Response onCommand(POP3Session session, Request request) {
        POP3Response response = null;
        if (session.getHandlerState() == POP3Session.TRANSACTION) {

            List<MessageMetaData> uidList = (List<MessageMetaData>) session.getState().get(POP3Session.UID_LIST);
            List<Long> deletedUidList = (List<Long>) session.getState().get(POP3Session.DELETED_UID_LIST);
            long size = 0;
            int count = 0;
            if (uidList.isEmpty() == false) {
                List<MessageMetaData> validResults = new ArrayList<MessageMetaData>();
                for (int i = 0; i < uidList.size(); i++) {
                    MessageMetaData data = uidList.get(i);
                    if (deletedUidList.contains(data.getUid()) == false) {
                        size += data.getSize();
                        count++;
                        validResults.add(data);
                    }
                }
            }
            StringBuilder responseBuffer = new StringBuilder(32).append(count).append(" ").append(size);
            response = new POP3Response(POP3Response.OK_RESPONSE, responseBuffer.toString());

        } else {
            response = new POP3Response(POP3Response.ERR_RESPONSE);
        }
        return response;
    }

    /**
     * @see org.apache.james.protocols.api.handler.CommandHandler#getImplCommands()
     */
    public Collection<String> getImplCommands() {
        List<String> commands = new ArrayList<String>();
        commands.add(COMMAND_NAME);
        return commands;
    }

}
