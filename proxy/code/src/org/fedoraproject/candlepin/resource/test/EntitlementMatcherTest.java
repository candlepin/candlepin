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

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.ObjectFactory;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.ProductFactory;
import org.fedoraproject.candlepin.resource.EntitlementMatcher;
import org.fedoraproject.candlepin.test.TestUtil;

import java.util.List;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * EntitlementMatcherTest
 * @version $Rev$
 */
public class EntitlementMatcherTest {

    @Test
    public void testIsCompatable() throws Exception {
        Consumer consumer = TestUtil.createConsumer();
        ConsumerType typeSystem = ProductFactory.get().lookupConsumerTypeByLabel("system");
        consumer.setType(typeSystem);
        
        List f = ObjectFactory.get().listObjectsByClass(Product.class);
        Product rhel = (Product) ObjectFactory.get().lookupByFieldName(
                Product.class, "label", "rhel");
        Product rhelvirt = (Product) ObjectFactory.get().lookupByFieldName(
                Product.class, "label", "rhel-virt");

        EntitlementMatcher m = new EntitlementMatcher();
        
        assertTrue(m.isCompatible(consumer, rhel));
        
        ConsumerType vmwarehost = 
            ProductFactory.get().lookupConsumerTypeByLabel("vmwarehost");
        consumer.setType(vmwarehost);
        
        // Check that you can't use rhelvirt on a vmware host
        assertFalse(m.isCompatible(consumer, rhelvirt));
    }
}
