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

import static org.junit.Assert.*;

import java.math.BigInteger;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementCertificate;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.fedoraproject.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;

/**
 * EntitlementCuratorTest
 */
public class EntitlementCuratorTest extends DatabaseTestFixture {
    private Entitlement secondEntitlement;
    private Entitlement firstEntitlement;
    
    private final Integer EXPECTED_CERTIFICATE_SERIAL = 456;

    @Before
    public void setUp() {
        Owner owner = createOwner();
        ownerCurator.create(owner);
        
        Consumer consumer = createConsumer(owner);
        consumerCurator.create(consumer);
        
        Product product = TestUtil.createProduct();
        productCurator.create(product);
        
        Pool firstPool = createPoolAndSub(
            owner, product, 1L, dateSource.currentDate(), dateSource.currentDate());
        poolCurator.create(firstPool);
        
        EntitlementCertificate firstCertificate 
            = createEntitlementCertificate("key", "certificate",
                new BigInteger(Integer.toString(123)));
        
        firstEntitlement = createEntitlement(owner, null, firstPool, firstCertificate);
        entitlementCurator.create(firstEntitlement);
        
        Product product1 = TestUtil.createProduct();
        productCurator.create(product);

        Pool secondPool = createPoolAndSub(
            owner, product1, 1L, dateSource.currentDate(), dateSource.currentDate());
        poolCurator.create(secondPool);
        
        EntitlementCertificate secondCertificate 
            = createEntitlementCertificate("key", "certificate", 
                new BigInteger(Integer.toString(EXPECTED_CERTIFICATE_SERIAL)));
        
        secondEntitlement = createEntitlement(owner, null, secondPool, secondCertificate);
        entitlementCurator.create(secondEntitlement);
    }
    
    @Test
    public void shouldReturnCorrectCertificate() {
        assertEquals(secondEntitlement, 
            entitlementCurator.findByCertificateSerial(
                new BigInteger(new Long(EXPECTED_CERTIFICATE_SERIAL).toString())));
    }
}
