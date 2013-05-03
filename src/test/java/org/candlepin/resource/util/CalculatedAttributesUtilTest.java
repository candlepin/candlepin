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
package org.candlepin.resource.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.auth.Access;
import org.candlepin.auth.Principal;
import org.candlepin.exceptions.ForbiddenException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.Consumer;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductAttribute;
import org.candlepin.policy.js.quantity.QuantityRules;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

/**
 * CalculatedAttributesUtilTest
 */
public class CalculatedAttributesUtilTest extends DatabaseTestFixture {

    private CalculatedAttributesUtil attrUtil;
    private Owner owner1;
    private Product product1;
    private Pool pool1;
    private Principal adminPrincipal;
    private Consumer consumer;

    @Mock private QuantityRules quantityRules;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        owner1 = createOwner();
        ownerCurator.create(owner1);

        product1 = new Product("xyzzy", "xyzzy");
        productCurator.create(product1);

        pool1 = createPoolAndSub(owner1, product1, 500L,
            TestUtil.createDate(2000, 1, 1), TestUtil.createDate(3000, 1, 1));

        adminPrincipal = setupPrincipal(owner1, Access.ALL);

        attrUtil = new CalculatedAttributesUtil(i18n, consumerCurator, quantityRules);

        consumer = createConsumer(owner1);
    }

    @Test(expected = NotFoundException.class)
    public void testBadConsumerId() {
        attrUtil.addCalculatedAttributes(pool1, "xyzzy", adminPrincipal);
    }

    @Test
    public void testCalculatedAttributesPresent() {
        when(quantityRules.getSuggestedQuantity(any(Pool.class), any(Consumer.class))).
            thenReturn(1L);

        Pool p = attrUtil.addCalculatedAttributes(pool1, consumer.getUuid(),
            adminPrincipal);
        Map<String, String> attrs = p.getCalculatedAttributes();
        assertTrue(attrs.containsKey("suggested_quantity"));
        verify(quantityRules).getSuggestedQuantity(p, consumer);
    }

    @Test(expected = ForbiddenException.class)
    public void testUnauthorizedUserRequestingPool() {
        Owner owner2 = createOwner();
        ownerCurator.create(owner2);

        attrUtil.addCalculatedAttributes(pool1, consumer.getUuid(),
            setupPrincipal(owner2, Access.NONE));
    }

    @Test
    public void testQuantityIncrement() {
        Product product2 = new Product("blah", "blah");
        product2.addAttribute(new ProductAttribute("instance_multiplier", "12"));
        productCurator.create(product2);

        Pool pool2 = createPoolAndSub(owner1, product2, 500L,
            TestUtil.createDate(2000, 1, 1), TestUtil.createDate(3000, 1, 1));

        when(quantityRules.getSuggestedQuantity(any(Pool.class), any(Consumer.class))).
            thenReturn(1L);

        Pool p = attrUtil.addCalculatedAttributes(pool2, consumer.getUuid(),
            adminPrincipal);
        Map<String, String> attrs = p.getCalculatedAttributes();
        assertEquals("12", attrs.get("quantity_increment"));
        verify(quantityRules).getSuggestedQuantity(p, consumer);
    }
}
