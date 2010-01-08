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

package org.apache.james.imapserver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Date;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.decode.ImapDecoder;
import org.apache.james.imap.encode.ImapEncoder;
import org.apache.james.imap.mailbox.Mailbox;
import org.apache.james.imap.mailbox.MailboxManager;
import org.apache.james.imap.mailbox.MailboxSession;
import org.apache.james.imap.main.ImapRequestHandler;
import org.apache.james.imap.processor.main.DefaultImapProcessorFactory;
import org.apache.james.services.MailServer;
import org.apache.james.socket.api.ProtocolHandler;
import org.apache.james.socket.shared.AbstractProtocolHandlerFactory;
import org.apache.jsieve.mailet.Poster;

/**
 * TODO: this is a quick cut-and-paste hack from POP3Server. Should probably be
 * rewritten from scratch, together with ImapHandler.
 *
 * <p>Accepts IMAP connections on a server socket and dispatches them to IMAPHandlers.</p>
 *
 * <p>Also responsible for loading and parsing IMAP specific configuration.</p>
 */
public class ImapServerProtocolHandlerFactory extends AbstractProtocolHandlerFactory implements ImapConstants, Poster
{
    private static final String softwaretype = "JAMES "+VERSION+" Server "; //+ Constants.SOFTWARE_VERSION;
         
    private MailServer mailServer;
    
    private String hello;
    
    private ImapEncoder encoder;
    private ImapDecoder decoder;
    private ImapProcessor processor;

    private MailboxManager mailboxManager;
    
    @Resource(name="James")
    public void setMailSerer(MailServer mailServer) {
        this.mailServer = mailServer;
    }
    
    @Resource(name="mailboxmanager")
    public void setMailboxManager(MailboxManager mailboxManager) {
        this.mailboxManager = mailboxManager;
    }
    
    public void setImapDecoder(ImapDecoder decoder) {
        this.decoder = decoder;
    }
    
    public void setImapEncoder(ImapEncoder encoder) {
        this.encoder = encoder;
    }
    
    public void onInit() {
        processor = DefaultImapProcessorFactory.createDefaultProcessor(mailboxManager);
    }

    @Override
    public void onConfigure( final HierarchicalConfiguration configuration ) throws ConfigurationException {
        hello  = softwaretype + " Server " + getHelloName() + " is ready.";
    }
    
    /**
     * @see AbstractProtocolServer#getDefaultPort()
     */
    public int getDefaultPort() {
        return 143;
    }

    /**
     * @see AbstractProtocolServer#getServiceType()
     */
    public String getServiceType() {
        return "IMAP Service";
    }
    
    /**
     * Producing handlers.
     * @see org.apache.avalon.excalibur.pool.ObjectFactory#newInstance()
     */
    public ProtocolHandler newProtocolHandlerInstance()
    {  
        final ImapRequestHandler handler = new ImapRequestHandler(decoder, processor, encoder);
        final ImapHandler imapHandler = new ImapHandler(handler, hello); 
        getLogger().debug("Create handler instance");
        return imapHandler;
    }
    
    /**
     * @see org.apache.jsieve.mailet.Poster#post(java.lang.String, javax.mail.internet.MimeMessage)
     */
    public void post(String url, MimeMessage mail)throws MessagingException {
        final int endOfScheme = url.indexOf(':');
        if (endOfScheme < 0) {
            throw new MessagingException("Malformed URI");
        } else {
            final String scheme = url.substring(0, endOfScheme);
            if ("mailbox".equals(scheme)) {
                final int startOfUser = endOfScheme + 3;
                final int endOfUser = url.indexOf('@', startOfUser);
                if (endOfUser < 0) {
                    // TODO: when user missing, append to a default location
                    throw new MessagingException("Shared mailbox is not supported");
                } else {
                    String user = url.substring(startOfUser, endOfUser);
                    final int startOfHost = endOfUser + 1;
                    final int endOfHost  = url.indexOf('/', startOfHost);
                    final String host = url.substring(startOfHost, endOfHost);
                    //if (!"localhost".equals(host)) {
                    if (mailServer.isLocalServer(host) == false) {
                        //TODO: possible support for clustering?
                        throw new MessagingException("Only local mailboxes are supported");
                    } else {
                        final String urlPath;
                        final int length = url.length();
                        if (endOfHost + 1 == length) {
                            urlPath = "INBOX";
                        } else {
                            urlPath = url.substring(endOfHost, length);
                        }
                        
                        // check if we should use the full emailaddress as username
                        if (mailServer.supportVirtualHosting()) {
                            user = user + "@" + host;
                        } 
                        
                        final MailboxSession session = mailboxManager.createSystemSession(user, getLogger());
                        // This allows Sieve scripts to use a standard delimiter regardless of mailbox implementation
                        final String mailbox = urlPath.replace('/', session.getPersonalSpace().getDeliminator());
                        postToMailbox(user, mail, mailbox, session, mailboxManager);
                    }
                }
            } else {
                // TODO: add support for more protocols
                // TODO: for example mailto: for forwarding over SMTP
                // TODO: for example xmpp: for forwarding over Jabber
                throw new MessagingException("Unsupported protocol");
            }
        }
    }
    
    public void postToMailbox(String username, MimeMessage mail, String destination, final MailboxSession session, final MailboxManager mailboxManager) throws MessagingException {
        if (destination == null || "".equals(destination)) {
            destination = "INBOX";
        }
        final String name = mailboxManager.resolve(username, destination);
        try
        {
            if ("INBOX".equalsIgnoreCase(destination) && !(mailboxManager.mailboxExists(name, session))) {
                mailboxManager.createMailbox(name, session);
            }
            final Mailbox mailbox = mailboxManager.getMailbox(name, session);
            
            if (mailbox == null) {
                final String error = "Mailbox for user " + username
                        + " was not found on this server.";
                throw new MessagingException(error);
            }

            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            mail.writeTo(baos);
            mailbox.appendMessage(baos.toByteArray() , new Date(), session, true, null);
        }
        catch (IOException e)
        {
            throw new MessagingException("Failed to write mail message", e);
        }
        finally 
        {
            session.close();   
            mailboxManager.logout(session, true);
        }
    }
}
