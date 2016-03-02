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
        EventReceiver ereceiver = new EventReceiver(new AMQPDummyConfig(), new EventMessageListener());
        
        
        AMQPBusPubProvider provider = new  AMQPBusPubProvider(new AMQPDummyConfig(), new ObjectMapper());
        
        AMQPBusPublisher publisher = provider.get();
        Event e = new Event();
        e.setPrincipal(new PrincipalData("tprinc", "tprinc"));
        e.setTarget(Target.ACTIVATIONKEY);
        e.setType(Type.CREATED);
        
        publisher.onEvent(e);
    }
}
