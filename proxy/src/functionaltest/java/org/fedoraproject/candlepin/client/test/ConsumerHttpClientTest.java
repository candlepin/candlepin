/**
 * Copyright (c) 2009 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.fedoraproject.candlepin.client.test;

import static org.junit.Assert.*;

import java.util.List;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerFacts;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.Owner;
import org.junit.Before;
import org.junit.Test;

import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;

public class ConsumerHttpClientTest extends AbstractGuiceGrizzlyTest {

    private static final String METADATA_VALUE = "jsontestname";
    private static final String METADATA_NAME = "name";
    private static final String CONSUMER_NAME = "consumer name";

    private ConsumerType standardSystemType;
    private Owner owner;
    private Consumer consumer;
    
    @Before
    public void setUp() throws Exception {
        super.setUp();
        
        standardSystemType = consumerTypeCurator.create(new ConsumerType(
                "standard-system"));
        owner = ownerCurator.create(new Owner("test-owner"));
        consumer = consumerCurator.create(new Consumer(CONSUMER_NAME, owner,
                standardSystemType));
    }

    @Test
    public void listConsumers() {
        assertTrue(1 == consumerCurator.findAll().size());

        for (String consumerName : new String[] { "first", "second", "third",
            "fourth" }) {

            consumerCurator.create(new Consumer(consumerName, owner,
                    standardSystemType));
        }

        WebResource r = resource().path("/consumer");
        List<Consumer> returned = r.accept("application/json").type(
                "application/json").get(new GenericType<List<Consumer>>() {
                });

        assertTrue(5 == returned.size());
        assertTrue(5 == consumerCurator.findAll().size());
    }

    @Test
    public void getSingleConsumer() {
        WebResource r = resource().path("/consumer/" + consumer.getUuid());
        Consumer returned = r.accept("application/json").type(
                "application/json").get(Consumer.class);

        assertSameConsumer(consumer, returned);
    }

    @Test
    public void getSingleConsumerWithInvalidUuidShouldFail() {
        try {
            WebResource r = resource().path("/consumer/1234-5678");
            r.accept("application/json").type("application/json").get(
                    Consumer.class);
            fail();
        }
        catch (UniformInterfaceException e) {
            assertEquals(404, e.getResponse().getStatus());
        }
    }

    @Test
    public void createConsumer() {
        Consumer submitted = new Consumer(CONSUMER_NAME, null,
                standardSystemType);
        submitted.setFacts(new ConsumerFacts() {

            {
                setFact(METADATA_NAME, METADATA_VALUE);
            }
        });

        WebResource r = resource().path("/consumer");
        Consumer returned = r.accept("application/json").type(
                "application/json").post(Consumer.class, submitted);

        assertConsumerCreatedCorrectly(submitted, returned);
        assertSameConsumer(returned, consumerCurator.lookupByUuid(returned
                .getUuid()));
    }

    @Test
    public void createConsumerWithNonExistentConsumerTypeShouldFail() {
        Consumer submitted = new Consumer(CONSUMER_NAME, null,
                new ConsumerType("non-existent"));

        try {
            WebResource r = resource().path("/consumer");
            r.accept("application/json").type("application/json").post(
                    Consumer.class, submitted);
        }
        catch (UniformInterfaceException e) {
            assertEquals(400, e.getResponse().getStatus());
        }
    }

    @Test
    public void createConsumerWithMissingRequiredFieldsShouldFail() {
        Consumer submitted = new Consumer(null, null, standardSystemType);

        try {
            WebResource r = resource().path("/consumer");
            r.accept("application/json").type("application/json").post(
                    Consumer.class, submitted);
        }
        catch (UniformInterfaceException e) {
            assertEquals(400, e.getResponse().getStatus());
        }
    }

    @Test
    public void deleteConsumer() {
        unitOfWork.beginWork();

        WebResource r = resource().path("/consumer/" + consumer.getUuid());
        r.accept("application/json").type("application/json").delete();

        unitOfWork.endWork();
        unitOfWork.beginWork();

        Consumer c = consumerCurator.find(consumer.getId());
        assertNull(c);

        unitOfWork.endWork();
    }

    @Test
    public void deleteConsumerWithInvalidUuidShouldFail() {
        try {
            WebResource r = resource().path("/consumer/1234-5678");
            r.accept("application/json").type("application/json").delete();
        }
        catch (UniformInterfaceException e) {
            assertEquals(404, e.getResponse().getStatus());
        }
    }

    private void assertConsumerCreatedCorrectly(Consumer toSubmit,
            Consumer returned) {
        assertEquals(toSubmit.getName(), returned.getName());
        assertEquals(toSubmit.getType(), returned.getType());
        assertEquals(toSubmit.getFact(METADATA_NAME), returned
                .getFact(METADATA_NAME));
    }

    private void assertSameConsumer(Consumer first, Consumer second) {
        assertEquals(first.getUuid(), second.getUuid());
        assertEquals(first.getName(), second.getName());
        assertEquals(first.getType(), second.getType());
        // assertEquals(first.getOwner(), second.getOwner()); // annotated as
        // transient...
        assertEquals(first.getFact(METADATA_NAME), second
                .getFact(METADATA_NAME));
    }
}
