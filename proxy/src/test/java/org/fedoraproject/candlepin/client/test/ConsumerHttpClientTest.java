package org.fedoraproject.candlepin.client.test;

import static org.junit.Assert.*;

import java.util.List;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerFacts;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.Owner;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.Injector;
import com.google.inject.servlet.GuiceServletContextListener;
import com.google.inject.servlet.ServletModule;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.guice.spi.container.servlet.GuiceContainer;

public class ConsumerHttpClientTest extends AbstractGuiceGrizzlyTest {
    
    private static final String METADATA_VALUE = "jsontestname";
    private static final String METADATA_NAME = "name";
    private static final String CONSUMER_NAME = "consumer name";

    private ConsumerType standardSystemType;
    private Owner owner;
    private Consumer consumer;

    @Before
    public void setUp() {
        TestServletConfig.servletInjector = injector;
        
        startServer(TestServletConfig.class);
                
        standardSystemType = consumerTypeCurator.create(new ConsumerType("standard-system"));
        owner = ownerCurator.create(new Owner("test-owner"));
        consumer = consumerCurator.create(new Consumer(CONSUMER_NAME, owner, standardSystemType));
    }
    
    @Test
    public void listConsumers() {
        assertTrue(1 == consumerCurator.findAll().size());
        
        for(String consumerName:new String[] {"first", "second", "third", "fourth"}) {
            consumerCurator.create(new Consumer(consumerName, owner, standardSystemType));
        }

        WebResource r = resource().path("/consumer");
        List<Consumer> returned = r.accept("application/json")
             .type("application/json")
             .get(new GenericType<List<Consumer>>() {});
        
        assertTrue(5 == returned.size());
        assertTrue(5 == consumerCurator.findAll().size());
    }
    
    @Test
    public void getSingleConsumer() {
        WebResource r = resource().path("/consumer/" + consumer.getUuid());
        Consumer returned = r.accept("application/json")
             .type("application/json")
             .get(Consumer.class);
        
        assertSameConsumer(consumer, returned);
    }
    
    @Test
    public void createConsumer() {
        Consumer submitted = new Consumer(CONSUMER_NAME, null, standardSystemType);
        submitted.setFacts(new ConsumerFacts() {{ setFact(METADATA_NAME, METADATA_VALUE); }});

        
        WebResource r = resource().path("/consumer");
        Consumer returned = r.accept("application/json")
             .type("application/json")
             .post(Consumer.class, submitted);
        
        assertConsumerCorrectly(submitted, returned);
        assertSameConsumer(returned, consumerCurator.lookupByUuid(returned.getUuid()));
    }

    
    private void assertConsumerCorrectly(Consumer toSubmit, Consumer returned) {
        assertEquals(toSubmit.getName(), returned.getName());
        assertEquals(toSubmit.getType(), returned.getType());
        assertEquals(toSubmit.getFact(METADATA_NAME), returned.getFact(METADATA_NAME));
    }
    
    private void assertSameConsumer(Consumer first, Consumer second) {
        assertEquals(first.getUuid(), second.getUuid());
        assertEquals(first.getName(), second.getName());
        assertEquals(first.getType(), second.getType());
        // assertEquals(first.getOwner(), second.getOwner()); // annotated as transient...
        assertEquals(first.getFact(METADATA_NAME), second.getFact(METADATA_NAME));
    }
    
    public static class TestServletConfig extends GuiceServletContextListener {
        
        public static Injector servletInjector;
        
        @Override
        protected Injector getInjector() {

            return servletInjector.createChildInjector(new ServletModule() {

                @Override
                protected void configureServlets() {
                    serve("*").with(GuiceContainer.class);
                }
            });
        }
    }
}
