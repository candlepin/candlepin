package org.candlepin.audit.qpidtest;

import java.net.URISyntaxException;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.qpid.client.AMQQueue;

public class ConsumingThread extends Thread implements MessageListener {
    private Session s;
    private String name;

    public ConsumingThread(Queue queue, Session s, String name) throws JMSException, URISyntaxException {
        this.s = s;
        this.name = name;

        /**
         * We must receive from the AMQP queue. Not from EXCHANGE! If consuming
         * directly from exchange, the Java QPID client will create 
         * temporary queue that binds to the exchange. Thats not what we want 
         * because the FANOUT will cause duplicates of all messages to that exchange to be 
         * delivered to this queue!
         */
        MessageConsumer mc = s.createConsumer(queue);

        mc.setMessageListener(this);
        ;

    }

    @Override
    public void run() {
        while (true) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void onMessage(Message msg) {
        try {
            System.out.println(name + " -> RECEIVING -> " + ((TextMessage) msg).getText());
            msg.acknowledge();
            s.commit();
            
        } catch (JMSException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
   
    }

}
