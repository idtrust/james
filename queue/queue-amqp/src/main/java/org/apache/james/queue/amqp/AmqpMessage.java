package org.apache.james.queue.amqp;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import javax.jms.JMSException;
import javax.mail.MessagingException;
import javax.mail.internet.AddressException;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.james.core.MailImpl;
import org.apache.james.core.MimeMessageCopyOnWriteProxy;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;

public class AmqpMessage implements Serializable {

	/** JMS Property which holds the recipient as String */
	final static String JAMES_MAIL_RECIPIENTS = "JAMES_MAIL_RECIPIENTS";

	/** JMS Property which holds the sender as String */
	final static String JAMES_MAIL_SENDER = "JAMES_MAIL_SENDER";

	/** JMS Property which holds the error message as String */
	final static String JAMES_MAIL_ERROR_MESSAGE = "JAMES_MAIL_ERROR_MESSAGE";

	/** JMS Property which holds the last updated time as long (ms) */
	final static String JAMES_MAIL_LAST_UPDATED = "JAMES_MAIL_LAST_UPDATED";

	/** JMS Property which holds the mail size as long (bytes) */
	final static String JAMES_MAIL_MESSAGE_SIZE = "JAMES_MAIL_MESSAGE_SIZE";

	/** JMS Property which holds the mail name as String */
	final static String JAMES_MAIL_NAME = "JAMES_MAIL_NAME";

	/**
	 * Separator which is used for separate an array of String values in the JMS
	 * Property value
	 */
	final static String JAMES_MAIL_SEPARATOR = ";";

	/** JMS Property which holds the remote hostname as String */
	final static String JAMES_MAIL_REMOTEHOST = "JAMES_MAIL_REMOTEHOST";

	/** JMS Property which holds the remote ipaddress as String */
	final static String JAMES_MAIL_REMOTEADDR = "JAMES_MAIL_REMOTEADDR";

	/** JMS Property which holds the mail state as String */
	final static String JAMES_MAIL_STATE = "JAMES_MAIL_STATE";

	/** JMS Property which holds the mail attribute names as String */
	final static String JAMES_MAIL_ATTRIBUTE_NAMES = "JAMES_MAIL_ATTRIBUTE_NAMES";

	/** JMS Property which holds next delivery time as long (ms) */
	final static String JAMES_NEXT_DELIVERY = "JAMES_NEXT_DELIVERY";

	private static final long serialVersionUID = 6375127425239406908L;

	private Map<String, Object> props;
	private Map<String, Serializable> attributes;
	private byte[] message;
	private String id;

	public AmqpMessage(String id, Mail mail) {
		try {
			this.id = id;
			this.props = getProperties(mail, 0L);
			this.attributes = getAttributes(mail);
			long size = mail.getMessageSize();
			ByteArrayOutputStream out;
			if (size > -1) {
				out = new ByteArrayOutputStream((int) size);
			} else {
				out = new ByteArrayOutputStream();
			}
			mail.getMessage().writeTo(out);
			this.message = out.toByteArray();
		} catch (MessagingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private Map<String, Serializable> getAttributes(Mail mail) {
		Map<String, Serializable> attributes = new HashMap<String, Serializable>();
		Iterator<String> atts = mail.getAttributeNames();
		while (atts.hasNext()) {
			String attribute = atts.next();
			Object value = mail.getAttribute(attribute);
			if (value instanceof Serializable) {
				attributes.put(attribute, (Serializable) value);
			}
		}
		return attributes;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public Map<String, Object> getProps() {
		return props;
	}

	public void setProps(Map<String, Object> props) {
		this.props = props;
	}

	public byte[] getMessage() {
		return message;
	}

	public void setMessage(byte[] message) {
		this.message = message;
	}

	public Mail toMail() {
		try {
			return createMail();
		} catch (MessagingException e) {
			e.printStackTrace();
		} catch (JMSException e) {
			e.printStackTrace();
		}
		return null;
	}

	protected final Mail createMail() throws MessagingException, JMSException {
		MailImpl mail = new MailImpl();
		populateMail(mail);
		populateMailMimeMessage(mail);

		return mail;
	}

	protected void populateMailMimeMessage(Mail mail) throws MessagingException, JMSException {
		mail.setMessage(new MimeMessageCopyOnWriteProxy(new AmqpMimeMessageSource(this)));
	}

	protected void populateMail(MailImpl mail) throws JMSException {
		mail.setErrorMessage((String) props.get(JAMES_MAIL_ERROR_MESSAGE));
		mail.setLastUpdated(new Date((Long) props.get(JAMES_MAIL_LAST_UPDATED)));
		mail.setName((String) props.get(JAMES_MAIL_NAME));

		for (String attribute : attributes.keySet()) {
			mail.setAttribute(attribute, attributes.get(attribute));
		}

		List<MailAddress> rcpts = new ArrayList<MailAddress>();
		String recipients = (String) props.get(JAMES_MAIL_RECIPIENTS);
		StringTokenizer recipientTokenizer = new StringTokenizer(recipients, JAMES_MAIL_SEPARATOR);
		while (recipientTokenizer.hasMoreTokens()) {
			String token = recipientTokenizer.nextToken();
			try {
				MailAddress rcpt = new MailAddress(token);
				rcpts.add(rcpt);
			} catch (AddressException e) {
			}
		}
		mail.setRecipients(rcpts);
		mail.setRemoteAddr((String) props.get(JAMES_MAIL_REMOTEADDR));
		mail.setRemoteHost((String) props.get(JAMES_MAIL_REMOTEHOST));

		String sender = (String) props.get(JAMES_MAIL_SENDER);
		if (sender == null || sender.trim().length() <= 0) {
			mail.setSender(null);
		} else {
			try {
				mail.setSender(new MailAddress(sender));
			} catch (AddressException e) {
				mail.setSender(null);
			}
		}

		mail.setState((String) props.get(JAMES_MAIL_STATE));
	}

	protected Map<String, Object> getProperties(Mail mail, long delayInMillis) throws MessagingException {
		Map<String, Object> props = new HashMap<String, Object>();
		long nextDelivery = -1;
		if (delayInMillis > 0) {
			nextDelivery = System.currentTimeMillis() + delayInMillis;

		}
		props.put(JAMES_NEXT_DELIVERY, nextDelivery);
		props.put(JAMES_MAIL_ERROR_MESSAGE, mail.getErrorMessage());
		props.put(JAMES_MAIL_LAST_UPDATED, mail.getLastUpdated().getTime());
		props.put(JAMES_MAIL_MESSAGE_SIZE, mail.getMessageSize());
		props.put(JAMES_MAIL_NAME, mail.getName());

		StringBuilder recipientsBuilder = new StringBuilder();

		Iterator<MailAddress> recipients = mail.getRecipients().iterator();
		while (recipients.hasNext()) {
			String recipient = recipients.next().toString();
			recipientsBuilder.append(recipient.trim());
			if (recipients.hasNext()) {
				recipientsBuilder.append(JAMES_MAIL_SEPARATOR);
			}
		}
		props.put(JAMES_MAIL_RECIPIENTS, recipientsBuilder.toString());
		props.put(JAMES_MAIL_REMOTEADDR, mail.getRemoteAddr());
		props.put(JAMES_MAIL_REMOTEHOST, mail.getRemoteHost());

		String sender;
		MailAddress s = mail.getSender();
		if (s == null) {
			sender = "";
		} else {
			sender = mail.getSender().toString();
		}

		StringBuilder attrsBuilder = new StringBuilder();
		Iterator<String> attrs = mail.getAttributeNames();
		while (attrs.hasNext()) {
			String attrName = attrs.next();
			attrsBuilder.append(attrName);

			Object value = convertAttributeValue(mail.getAttribute(attrName));
			props.put(attrName, value);

			if (attrs.hasNext()) {
				attrsBuilder.append(JAMES_MAIL_SEPARATOR);
			}
		}
		props.put(JAMES_MAIL_ATTRIBUTE_NAMES, attrsBuilder.toString());
		props.put(JAMES_MAIL_SENDER, sender);
		props.put(JAMES_MAIL_STATE, mail.getState());
		return props;
	}

	protected Object convertAttributeValue(Object value) {
		if (value == null || value instanceof String || value instanceof Byte || value instanceof Long
				|| value instanceof Double || value instanceof Boolean || value instanceof Integer
				|| value instanceof Short || value instanceof Float) {
			return value;
		}
		return value.toString();
	}

}
