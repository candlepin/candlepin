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

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

/**
 * ConsumerInstalledProductTest
 */
public class ConsumerInstalledProductTest {

    private ConsumerInstalledProduct cip1;
    private ConsumerInstalledProduct cip2;

    @Before
    public void setUpTestObjects() {
        cip1 = new ConsumerInstalledProduct("ProdA", "Product A");
        cip1.setArch("x86");
        cip1.setVersion("1.0");
        cip2 = new ConsumerInstalledProduct("ProdA", "Product A");
        cip2.setArch("x86");
        cip2.setVersion("1.0");
    }

    @Test
    public void testEquals() {
        assertEquals(cip1, cip2);
        cip1.setVersion(null);
        cip1.setArch(null);
        cip2.setVersion(null);
        cip2.setArch(null);
        assertEquals(cip1, cip2);
    }

    @Test
    public void testNotEquals() {
        cip1.setProductId("SomeProd");
        assertFalse(cip1.equals(cip2));
        cip1.setProductId("ProdA");
        assertEquals(cip1, cip2);
        cip1.setProductName("some name");
        assertFalse(cip1.equals(cip2));
        cip1.setProductName("Product A");
        assertEquals(cip1, cip2);
        cip1.setVersion(null);
        assertFalse(cip1.equals(cip2));
        cip1.setVersion("1.0");
        assertEquals(cip1, cip2);
        cip2.setVersion(null);
        assertFalse(cip1.equals(cip2));
        cip2.setVersion("1.0");
        assertEquals(cip1, cip2);
        cip1.setArch(null);
        assertFalse(cip1.equals(cip2));
        cip1.setArch("x86");
        assertEquals(cip1, cip2);
        cip2.setArch(null);
        assertFalse(cip1.equals(cip2));
    }
}
