package org.candlepin.gutterball.receive;

import javax.jms.Message;
import javax.jms.MessageListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventMessageListener implements MessageListener {

    private static Logger log = LoggerFactory.getLogger(EventReceiver.class);
    
	@Override
	public void onMessage(Message message) {
		log.info(message.toString());
	}

}
