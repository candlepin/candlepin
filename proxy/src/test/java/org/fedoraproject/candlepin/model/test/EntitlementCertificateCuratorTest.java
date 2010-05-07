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
package org.fedoraproject.candlepin.model.test;

import java.math.BigInteger;
import java.util.Collections;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementCertificate;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.fedoraproject.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * EntitlementCertificateCuratorTest
 */
public class EntitlementCertificateCuratorTest extends DatabaseTestFixture {
    
    private Consumer consumer;
    private Consumer anotherConsumer;
    private Entitlement entitlement1;
    private Entitlement entitlement2;

    @Before
    public void setUp() {
        ConsumerType standardSystemType 
            = consumerTypeCurator.create(new ConsumerType("standard-system"));
        
        Owner owner = ownerCurator.create(new Owner("test-owner"));
        ownerCurator.create(owner);
        
        consumer = TestUtil.createConsumer(standardSystemType, owner);
        consumerCurator.create(consumer);
        anotherConsumer = TestUtil.createConsumer(standardSystemType, owner);
        consumerCurator.create(anotherConsumer);
        
        
        Product product1 = TestUtil.createProduct();
        productCurator.create(product1);
        
        Pool pool1 = TestUtil.createEntitlementPool(owner, product1);
        poolCurator.create(pool1);
        
        entitlement1 = TestUtil.createEntitlement(pool1, consumer);
        EntitlementCertificate cert1 = new EntitlementCertificate();
        cert1.setKey(new byte[0]);
        cert1.setCert(new byte[0]);
        cert1.setSerial(new BigInteger("1"));
        cert1.setEntitlement(entitlement1);
        entitlement1.setCertificates(Collections.singleton(cert1));
        entitlementCurator.create(entitlement1);

        entitlement2 = TestUtil.createEntitlement(pool1, anotherConsumer);
        EntitlementCertificate cert2 = new EntitlementCertificate();
        cert2.setKey(new byte[0]);
        cert2.setCert(new byte[0]);
        cert2.setSerial(new BigInteger("2"));
        cert2.setEntitlement(entitlement2);
        entitlement2.setCertificates(Collections.singleton(cert2));
        entitlementCurator.create(entitlement2);
    }
    
    @Test
    public void shouldFilterByConsumer() {
        assertEquals(1, entCertCurator.listForConsumer(consumer).size());
    }
}
