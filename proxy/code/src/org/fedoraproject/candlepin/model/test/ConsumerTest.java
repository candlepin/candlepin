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

import org.fedoraproject.candlepin.model.BaseModel;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Product;

import org.junit.Test;
import static org.junit.Assert.*;



public class ConsumerTest {

    @Test
    public void testConsumedProduct() throws Exception {
        Owner o = TestUtil.createOwner();
        
        Product rhel = new Product(BaseModel.generateUUID());
        rhel.setName("Red Hat Enterprise Linux");
        
        Consumer c = TestUtil.createConsumer(o);
        c.addConsumedProduct(rhel);
        
        
        
    }
    
    @Test
    public void testProperties() {
        Owner o = TestUtil.createOwner();
        Consumer c = TestUtil.createConsumer(o);
        c.setMetadataField("cpu", "2");
        
        assertEquals(c.getMetadataField("cpu"), "2");
    }
}
