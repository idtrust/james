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
package org.apache.james.imapserver.netty;

import java.io.IOException;
import java.net.InetSocketAddress;

import javax.net.ssl.SSLContext;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.ImapSessionState;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.encode.ImapEncoder;
import org.apache.james.imap.encode.ImapResponseComposer;
import org.apache.james.imap.encode.base.ImapResponseComposerImpl;
import org.apache.james.imap.main.ResponseEncoder;
import org.apache.james.protocols.impl.ChannelAttributeSupport;
import org.apache.james.protocols.impl.SessionLog;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.frame.TooLongFrameException;
import org.slf4j.Logger;

/**
 * {@link SimpleChannelUpstreamHandler} which handles IMAP
 */
public class ImapChannelUpstreamHandler extends SimpleChannelUpstreamHandler implements ChannelAttributeSupport {

    private final Logger logger;

    private final String hello;

    private String[] enabledCipherSuites;

    private SSLContext context;

    private boolean compress;

    private ImapProcessor processor;

    private ImapEncoder encoder;

    public ImapChannelUpstreamHandler(final String hello, final ImapProcessor processor, ImapEncoder encoder, final Logger logger, boolean compress) {
        this(hello, processor, encoder, logger, compress, null, null);
    }

    public ImapChannelUpstreamHandler(final String hello, final ImapProcessor processor, ImapEncoder encoder, final Logger logger, boolean compress, SSLContext context, String[] enabledCipherSuites) {
        this.logger = logger;
        this.hello = hello;
        this.processor = processor;
        this.encoder = encoder;
        this.context = context;
        this.enabledCipherSuites = enabledCipherSuites;
        this.compress = compress;
    }

    private Logger getLogger(Channel channel) {
        return new SessionLog("" + channel.getId(), logger);
    }

    @Override
    public void channelBound(final ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {

        ImapSession imapsession = new NettyImapSession(ctx, logger, context, enabledCipherSuites, compress);
        attributes.set(ctx.getChannel(), imapsession);
        super.channelBound(ctx, e);
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        InetSocketAddress address = (InetSocketAddress) ctx.getChannel().getRemoteAddress();
        getLogger(ctx.getChannel()).info("Connection closed for " + address.getHostName() + " (" + address.getAddress().getHostAddress() + ")");

        // remove the stored attribute for the channel to free up resources
        // See JAMES-1195
        ImapSession imapSession = (ImapSession) attributes.remove(ctx.getChannel());
        if (imapSession != null)
            imapSession.logout();

        super.channelClosed(ctx, e);
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        InetSocketAddress address = (InetSocketAddress) ctx.getChannel().getRemoteAddress();
        getLogger(ctx.getChannel()).info("Connection established from " + address.getHostName() + " (" + address.getAddress().getHostAddress() + ")");

        ImapResponseComposer response = new ImapResponseComposerImpl(new ChannelImapResponseWriter(ctx.getChannel()));
        ctx.setAttachment(response);

        // write hello to client
        response.hello(hello);
        // ctx.getChannel().write(ChannelBuffers.copiedBuffer((ImapConstants.UNTAGGED
        // + " OK " + hello +" " + new
        // String(ImapConstants.BYTES_LINE_END)).getBytes()));

        super.channelConnected(ctx, e);

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        getLogger(ctx.getChannel()).debug("Error while processing imap request", e.getCause());

        if (e.getCause() instanceof TooLongFrameException) {

            // Max line length exceeded
            // See RFC 2683 section 3.2.1
            //
            // "For its part, a server should allow for a command line of at
            // least
            // 8000 octets. This provides plenty of leeway for accepting
            // reasonable
            // length commands from clients. The server should send a BAD
            // response
            // to a command that does not end within the server's maximum
            // accepted
            // command length."
            //
            // See also JAMES-1190
            ImapResponseComposer composer = (ImapResponseComposer) ctx.getAttachment();
            composer.untaggedResponse(ImapConstants.BAD + " failed. Maximum command line length exceeded");
        } else {
            // logout on error not sure if that is the best way to handle it
            final ImapSession imapSession = (ImapSession) attributes.get(ctx.getChannel());
            if (imapSession != null)
                imapSession.logout();

            // just close the channel now!
            ctx.getChannel().close();

        }

    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        ImapSession session = (ImapSession) attributes.get(ctx.getChannel());
        ImapResponseComposer response = (ImapResponseComposer) ctx.getAttachment();
        ImapMessage message = (ImapMessage) e.getMessage();

        final ResponseEncoder responseEncoder = new ResponseEncoder(encoder, response, session);
        processor.process(message, responseEncoder, session);

        if (session.getState() == ImapSessionState.LOGOUT) {
            ctx.getChannel().close();
        }
        final IOException failure = responseEncoder.getFailure();

        if (failure != null) {
            final Logger logger = session.getLog();
            logger.info(failure.getMessage());
            if (logger.isDebugEnabled()) {
                logger.debug("Failed to write " + message, failure);
            }
            throw failure;
        }

        super.messageReceived(ctx, e);
    }

}
