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
package org.apache.james.queue.amqp;

import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.jms.ObjectMessage;

import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.mailet.Mail;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * <p>
 * {@link MailQueue} implementation which use an ActiveMQ Queue.
 * <p>
 * </p>
 * This implementation require at ActiveMQ 5.4.0+.
 * <p>
 * </p>
 * When a {@link Mail} attribute is found and is not one of the supported
 * primitives, then the toString() method is called on the attribute value to
 * convert it
 * <p>
 * </p>
 * The implementation use {@link BlobMessage} or {@link ObjectMessage},
 * depending on the constructor which was used
 * <p>
 * </p>
 * See <a href="http://activemq.apache.org/blob-messages.html">http://activemq.
 * apache .org/blob-messages.html</a> for more details
 * <p>
 * </p>
 * Some other supported feature is handling of priorities. See:<br>
 * <a href="http://activemq.apache.org/how-can-i-support-priority-queues.html">
 * http://activemq.apache.org/how-can-i-support-priority-queues.html</a>
 * <p>
 * </p>
 * For this just add a {@link Mail} attribute with name {@link #MAIL_PRIORITY}
 * to it. It should use one of the following value {@link #LOW_PRIORITY},
 * {@link #NORMAL_PRIORITY}, {@link #HIGH_PRIORITY}
 * <p>
 * </p>
 * To have a good throughput you should use a caching connection factory.
 * </p>
 */
public class AmqpMailQueue implements ManageableMailQueue {

	private RabbitAdmin rabbitAdmin;
	private ConnectionFactory connectionFactory;
	private AmqpTemplate amqpTemplate;
	private String queueName;

	public AmqpMailQueue(ConnectionFactory connectionFactory, String queueName) {
		super();
		this.connectionFactory = connectionFactory;
		this.queueName = queueName;

		this.rabbitAdmin = new RabbitAdmin(connectionFactory);

		this.rabbitAdmin.declareQueue(new Queue(queueName));
		this.amqpTemplate = new RabbitTemplate(connectionFactory);
	}

	@Override
	public void enQueue(Mail mail, long delay, TimeUnit unit) throws MailQueueException {
		try {
			unit.sleep(delay);
		} catch (InterruptedException e) {
		}
		amqpTemplate.convertAndSend(queueName, new AmqpMessage(UUID.randomUUID().toString(), mail));
	}

	@Override
	public void enQueue(Mail mail) throws MailQueueException {
		enQueue(mail, 0, TimeUnit.SECONDS);
	}

	@Override
	public MailQueueItem deQueue() throws MailQueueException {
		AmqpMessage mail = null;
		for (;;) {
			mail = (AmqpMessage) amqpTemplate.receiveAndConvert(queueName);
			if (mail == null) {
				try {
					Thread.sleep(10);
				} catch (InterruptedException ignore) {
				}
			} else {
				break;
			}
		}
		Mail m = mail.toMail();
		return new AmqpMailQueueItem(m);
	}

	@Override
	public long getSize() throws MailQueueException {
		Properties props = rabbitAdmin.getQueueProperties(queueName);
		return new Long(String.valueOf((Integer) props.get("QUEUE_MESSAGE_COUNT")));
	}

	@Override
	public long flush() throws MailQueueException {
		return 0;
	}

	@Override
	public long clear() throws MailQueueException {
		return 0;
	}

	@Override
	public long remove(Type type, String value) throws MailQueueException {
		return 0;
	}

	@Override
	public MailQueueIterator browse() throws MailQueueException {
		return new MailQueueIterator() {
			@Override
			public MailQueueItemView next() {
				return null;
			}

			@Override
			public boolean hasNext() {
				return false;
			}

			@Override
			public void close() {
			}
		};
	}

}
