package org.candlepin.audit.qpidtest;

import org.candlepin.audit.AMQPBusPublisher;
import org.candlepin.audit.Event;
import org.candlepin.audit.Event.Target;
import org.candlepin.audit.Event.Type;
import org.candlepin.auth.PrincipalData;
import org.candlepin.guice.AMQPBusPubProvider;

import com.fasterxml.jackson.databind.ObjectMapper;


public class AMQPBusPubProviderFunctionalTest {
    public static void main(String[] args) throws Exception {
//        EventReceiver ereceiver = new EventReceiver(new AMQPDummyConfig(), new EventMessageListener("l1"));
//        EventReceiver ereceiver2 = new EventReceiver(new AMQPDummyConfig(), new EventMessageListener("l2"));
        
        QueueEventReceiver queueReceiver = new QueueEventReceiver(new AMQPDummyConfig());
//        QueueEventReceiver queueReceiver2 = new QueueEventReceiver(new AMQPDummyConfig());
        
        Thread.sleep(2000);
        
        AMQPBusPubProvider provider = new  AMQPBusPubProvider(new AMQPDummyConfig(), new ObjectMapper());
        
        AMQPBusPublisher publisher = provider.get();
        Event e = new Event();
        e.setPrincipal(new PrincipalData("tprinc", "tprinc"));
        e.setTarget(Target.ACTIVATIONKEY);
        e.setType(Type.CREATED);
        
        publisher.onEvent(e);
        publisher.onEvent(e);
    }
}
