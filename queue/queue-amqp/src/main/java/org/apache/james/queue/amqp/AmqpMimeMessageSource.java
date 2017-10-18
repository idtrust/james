package org.apache.james.queue.amqp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.james.core.MimeMessageSource;

public class AmqpMimeMessageSource extends MimeMessageSource {

	private AmqpMessage message;

	public AmqpMimeMessageSource(AmqpMessage message) {
		super();
		this.message = message;
	}

	@Override
	public String getSourceId() {
		return null;
	}

	@Override
	public InputStream getInputStream() throws IOException {
		return new ByteArrayInputStream(message.getMessage());
	}

}
