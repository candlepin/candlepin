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
package org.fedoraproject.candlepin.resource.test;

import static org.junit.Assert.*;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerFacts;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.resource.ConsumerResource;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * ConsumerResourceTest
 */
public class ConsumerResourceTest extends DatabaseTestFixture {
    
    private static final String METADATA_VALUE = "jsontestname";
    private static final String METADATA_NAME = "name";
    private static final String CONSUMER_NAME = "consumer name";
    
    private ConsumerType standardSystemType;
    private ConsumerResource consumerResource;
    private Owner owner;

    @Before
    public void setUp() {

        consumerResource = new ConsumerResource(ownerCurator, consumerCurator, consumerTypeCurator);
        standardSystemType = consumerTypeCurator.create(new ConsumerType("standard-system"));
        owner = ownerCurator.create(new Owner("test-owner"));
    }
    
    // TODO: Test no such consumer type.
    
//    @Test
//    public void testDelete() {
//        Consumer c = TestUtil.createConsumer();
//        String uuid = c.getUuid();
//        ConsumerResource capi = new ConsumerResource();
//        assertNotNull(ObjectFactory.get().lookupByUUID(c.getClass(), uuid));
//        capi.delete(uuid);
//        assertNull(ObjectFactory.get().lookupByUUID(c.getClass(), uuid));
//    }

    @Test
    public void testCreateConsumer() {
        Consumer toSubmit = new Consumer(CONSUMER_NAME, null, standardSystemType);
        toSubmit.setFacts(new ConsumerFacts() {{ setFact(METADATA_NAME, METADATA_VALUE); }});

        Consumer created = consumerResource.create(toSubmit);
        
        assertNotNull(created);
        assertNotNull(created.getUuid());
        assertNotNull(consumerCurator.find(created.getId()));
        assertEquals(standardSystemType.getLabel(), created.getType().getLabel());
        assertEquals(METADATA_VALUE, created.getMetadataField(METADATA_NAME));
    }
    
    @Ignore // TODO: implement 'delete' functionality
    public void testDeleteResource() {
        Consumer created = consumerCurator.create(new Consumer(CONSUMER_NAME, owner, standardSystemType));
        //consumerResource.delete(created.getUuid());
        
        assertNull(consumerCurator.find(created.getId()));
    }
}
