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
package org.fedoraproject.candlepin.policy.test;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.fedoraproject.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;

public class JavaEnforcerTest extends DatabaseTestFixture {
    
    private final static String PRODUCT_VIRT_HOST = "virtualization_host";
    private Product virtHost;
    private Owner owner;
    private Consumer consumer;

    @Before
    public void setup() {
        // Create required products:
        
        virtHost = new Product(PRODUCT_VIRT_HOST, PRODUCT_VIRT_HOST);
        productCurator.create(virtHost);
        
        owner = TestUtil.createOwner();
        ownerCurator.create(owner);
        

        EntitlementPool pool = new EntitlementPool(owner, virtHost, new Long(10), 
                TestUtil.createDate(2009, 11, 30), TestUtil.createDate(2040, 11, 30));
        entitlementPoolCurator.create(pool);

        ConsumerType system = new ConsumerType("system");
        consumerTypeCurator.create(system);
        
        consumer = new Consumer("system", owner, system);
        consumer.getFacts().setFact("total_guests", "0");
        consumerCurator.create(consumer);
        
    }
    
    @Test
    public void testVirtualizationHostConsumption() {
        entitlementPoolCurator.createEntitlement(owner, consumer, virtHost);
    }
}
