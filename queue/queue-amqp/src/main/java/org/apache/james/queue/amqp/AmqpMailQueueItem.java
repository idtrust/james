package org.apache.james.queue.amqp;

import org.apache.james.queue.api.MailQueue.MailQueueException;
import org.apache.james.queue.api.MailQueue.MailQueueItem;
import org.apache.mailet.Mail;

public class AmqpMailQueueItem implements MailQueueItem {

	private Mail mail;

	public AmqpMailQueueItem(Mail mail) {
		super();
		this.mail = mail;
	}

	@Override
	public Mail getMail() {
		return mail;
	}

	@Override
	public void done(boolean success) throws MailQueueException {
	}

}
