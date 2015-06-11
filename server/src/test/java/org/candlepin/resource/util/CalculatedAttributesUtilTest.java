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

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.candlepin.model.Consumer;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolAttribute;
import org.candlepin.model.Product;
import org.candlepin.model.ProductAttribute;
import org.candlepin.model.ProductCurator;
import org.candlepin.policy.js.pooltype.PoolComplianceType;
import org.candlepin.policy.js.pooltype.PoolComplianceTypeRules;
import org.candlepin.policy.js.quantity.QuantityRules;
import org.candlepin.policy.js.quantity.SuggestedQuantity;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.Date;
import java.util.Locale;
import java.util.Map;

import javax.inject.Inject;

/**
 * CalculatedAttributesUtilTest
 */
public class CalculatedAttributesUtilTest extends DatabaseTestFixture {
    @Inject private OwnerCurator ownerCurator;
    @Inject private ProductCurator productCurator;

    private CalculatedAttributesUtil attrUtil;
    private Owner owner1;
    private Product product1;
    private Pool pool1;
    private Consumer consumer;

    @Mock private QuantityRules quantityRules;
    @Mock private PoolComplianceTypeRules poolTypeRules;
    private I18n i18n;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        owner1 = createOwner();
        ownerCurator.create(owner1);

        product1 = new Product("xyzzy", "xyzzy", owner1);
        productCurator.create(product1);

        pool1 = createPool(owner1, product1, 500L,
            TestUtil.createDate(2000, 1, 1), TestUtil.createDate(3000, 1, 1));

        Locale locale = new Locale("en_US");
        i18n = I18nFactory.getI18n(getClass(), "org.candlepin.i18n.Messages", locale,
            I18nFactory.FALLBACK);

        attrUtil = new CalculatedAttributesUtil(quantityRules, poolTypeRules, i18n);

        consumer = createConsumer(owner1);
    }

    @Test
    public void testCalculatedAttributesPresent() {
        SuggestedQuantity suggested = new SuggestedQuantity();
        suggested.setSuggested(1L);
        suggested.setIncrement(1L);
        when(quantityRules.getSuggestedQuantity(any(Pool.class),
            any(Consumer.class), any(Date.class))).
            thenReturn(suggested);

        PoolComplianceType pt = new PoolComplianceType();
        pt.setRawPoolType("unknown");
        pt.translatePoolType(i18n);
        when(poolTypeRules.getPoolType(any(Pool.class))).
            thenReturn(pt);

        Date date = new Date();
        Map<String, String> attrs =
            attrUtil.buildCalculatedAttributes(pool1, consumer, date);
        assertTrue(attrs.containsKey("suggested_quantity"));
        verify(quantityRules).getSuggestedQuantity(pool1, consumer, date);
        assertTrue(attrs.containsKey("compliance_type"));
        verify(poolTypeRules).getPoolType(pool1);
        assertEquals("Other", attrs.get("compliance_type"));
    }

    @Test
    public void testCalculatedAttributesTemporary() {
        SuggestedQuantity suggested = new SuggestedQuantity();
        suggested.setSuggested(1L);
        suggested.setIncrement(1L);
        when(quantityRules.getSuggestedQuantity(any(Pool.class),
            any(Consumer.class), any(Date.class))).
            thenReturn(suggested);

        PoolComplianceType pt = new PoolComplianceType();
        pt.setRawPoolType("standard");
        pt.translatePoolType(i18n);

        when(poolTypeRules.getPoolType(any(Pool.class))).
            thenReturn(pt);
        pool1.addAttribute(new PoolAttribute("unmapped_guests_only", "true"));

        Date date = new Date();
        Map<String, String> attrs =
            attrUtil.buildCalculatedAttributes(pool1, consumer, date);
        verify(poolTypeRules).getPoolType(pool1);
        assertEquals("Standard (Temporary)", attrs.get("compliance_type"));
    }

    @Test
    public void testQuantityIncrement() {
        Product product2 = new Product("blah", "blah", owner1);
        product2.addAttribute(new ProductAttribute("instance_multiplier", "12"));
        productCurator.create(product2);

        Pool pool2 = createPool(owner1, product2, 500L,
            TestUtil.createDate(2000, 1, 1), TestUtil.createDate(3000, 1, 1));

        SuggestedQuantity suggested = new SuggestedQuantity();
        suggested.setSuggested(1L);
        suggested.setIncrement(12L);
        when(quantityRules.getSuggestedQuantity(any(Pool.class),
            any(Consumer.class), any(Date.class))).
            thenReturn(suggested);

        when(poolTypeRules.getPoolType(any(Pool.class))).
            thenReturn(new PoolComplianceType());

        Date date = new Date();
        Map<String, String> attrs =
            attrUtil.buildCalculatedAttributes(pool2, consumer, date);
        assertEquals("12", attrs.get("quantity_increment"));
        verify(quantityRules).getSuggestedQuantity(pool2, consumer, date);
    }
}
