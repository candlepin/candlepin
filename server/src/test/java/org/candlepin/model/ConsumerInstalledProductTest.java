/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.candlepin.test.TestUtil;

import org.junit.Before;
import org.junit.Test;

/**
 * ConsumerInstalledProductTest
 */
public class ConsumerInstalledProductTest {

    private Consumer consumer1;
    private Consumer consumer2;
    private Product product1;
    private Product product2;
    private ConsumerInstalledProduct cip1;
    private ConsumerInstalledProduct cip2;

    @Before
    public void setUpTestObjects() {
        Owner owner = TestUtil.createOwner();
        this.consumer1 = TestUtil.createConsumer();
        this.consumer2 = TestUtil.createConsumer();
        this.product1 = TestUtil.createProduct("ProdA", "Product A", owner);
        this.product2 = TestUtil.createProduct("ProdB", "Product B", owner);

        cip1 = new ConsumerInstalledProduct();
        product1.setAttribute("arch", "x86");
        product1.setAttribute("version", "1.0");
        cip2 = new ConsumerInstalledProduct();
        product2.setAttribute("arch", "x86");
        product2.setAttribute("version", "1.0");
    }

    @Test
    public void testEquals() {
        assertEquals(cip1, cip2);

        this.cip1.setConsumer(this.consumer1);
        this.cip1.setProduct(this.product1);
        this.cip2.setConsumer(this.consumer1);
        this.cip2.setProduct(this.product1);

        assertEquals(cip1, cip2);
    }

    @Test
    public void testNotEquals() {
        this.cip1.setConsumer(this.consumer1);
        this.cip1.setProduct(this.product1);
        this.cip2.setConsumer(this.consumer1);
        this.cip2.setProduct(this.product1);

        assertEquals(cip1, cip2);

        this.cip2.setConsumer(this.consumer2);
        assertFalse(cip1.equals(cip2));

        this.cip2.setConsumer(this.consumer1);
        this.cip2.setProduct(this.product2);
        assertFalse(cip1.equals(cip2));
    }
}
