package org.candlepin.audit.qpidtest;

import java.net.URISyntaxException;

import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.qpid.client.AMQQueue;

public class ProducingThread extends Thread{

    private Session s; 
    private String name;
    private MessageProducer mp = null;
    
    public ProducingThread(Session s, String name) throws JMSException, URISyntaxException {
        this.s = s;
        this.name = name;
        /**
         * We must be producing into EXCHANGE. Not into QUEUE name!
         * Otherwise, the QPID Java client will work but it will create
         * some weird undurable direct exchange with name '' (empty string)
         *  
         */
        mp = s.createProducer(new AMQQueue("activation"));
    }

    @Override 
    public void run() {
        for (int i = 0 ;i < 2;i++){
            try {
                produce(i);
            } catch (JMSException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void produce(int i) throws JMSException {
        System.out.println("producing");
        TextMessage tm = s.createTextMessage(name+"->"+i);
        mp.send(tm);
        s.commit();
    }
}
