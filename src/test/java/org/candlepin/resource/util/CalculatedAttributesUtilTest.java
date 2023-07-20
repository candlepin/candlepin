/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

import org.candlepin.dto.rules.v1.SuggestedQuantityDTO;
import org.candlepin.model.Consumer;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Pool.PoolComplianceType;
import org.candlepin.model.Product;
import org.candlepin.policy.js.quantity.QuantityRules;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;



public class CalculatedAttributesUtilTest extends DatabaseTestFixture {

    private CalculatedAttributesUtil attrUtil;
    private Owner owner1;
    private Product product1;
    private Pool pool1;
    private Consumer consumer;

    private I18n i18n;

    @Mock
    private QuantityRules quantityRules;
    @Mock
    private Pool mockPool;

    @BeforeEach
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        owner1 = createOwner();
        ownerCurator.create(owner1);

        product1 = TestUtil.createProduct("xyzzy", "xyzzy");
        productCurator.create(product1);

        pool1 = createPool(owner1, product1, 500L,
            TestUtil.createDate(2000, 1, 1), TestUtil.createDate(3000, 1, 1));

        Locale locale = new Locale("en_US");
        i18n = I18nFactory.getI18n(getClass(), "org.candlepin.i18n.Messages", locale,
            I18nFactory.FALLBACK);

        consumer = createConsumer(owner1);
        attrUtil = new CalculatedAttributesUtil(quantityRules, i18n);
    }

    @Test
    public void testCalculatedAttributesPresent() {
        for (PoolComplianceType type : PoolComplianceType.values()) {
            when(mockPool.getComplianceType()).thenReturn(type);

            Date date = new Date();
            Map<String, String> attrs = attrUtil.buildCalculatedAttributes(mockPool, date);

            assertTrue(attrs.containsKey("compliance_type"));
        }

        SuggestedQuantityDTO suggested = new SuggestedQuantityDTO();
        suggested.setSuggested(1L);
        suggested.setIncrement(1L);
        Map<String, SuggestedQuantityDTO> suggestedMap = new HashMap<>();
        suggestedMap.put(pool1.getId(), suggested);
        when(quantityRules.getSuggestedQuantities(anyList(),
            any(Consumer.class), any(Date.class))).thenReturn(suggestedMap);

        Date date = new Date();
        attrUtil.setQuantityAttributes(pool1, consumer, date);
        assertTrue(pool1.getCalculatedAttributes().containsKey("suggested_quantity"));
        assertTrue(pool1.getCalculatedAttributes().containsKey("quantity_increment"));
    }

    @Test
    public void testCalculatedAttributesTemporary() {
        for (PoolComplianceType type : PoolComplianceType.values()) {
            when(mockPool.getComplianceType()).thenReturn(type);
            when(mockPool.isUnmappedGuestPool()).thenReturn(true);

            Date date = new Date();
            Map<String, String> attrs = attrUtil.buildCalculatedAttributes(mockPool, date);

            assertTrue(attrs.containsKey("compliance_type"));
            assertEquals(type.getDescription() + " (Temporary)", attrs.get("compliance_type"));
        }
    }

    @Test
    public void testCalculatedAttributesNotSoTemporary() {
        for (PoolComplianceType type : PoolComplianceType.values()) {
            when(mockPool.getComplianceType()).thenReturn(type);
            when(mockPool.isUnmappedGuestPool()).thenReturn(false);

            Date date = new Date();
            Map<String, String> attrs = attrUtil.buildCalculatedAttributes(mockPool, date);

            assertTrue(attrs.containsKey("compliance_type"));
            assertEquals(type.getDescription(), attrs.get("compliance_type"));
        }
    }

    @Test
    public void testQuantityIncrement() {
        Product product2 = TestUtil.createProduct("blah", "blah");
        product2.setAttribute(Product.Attributes.INSTANCE_MULTIPLIER, "12");
        productCurator.create(product2);

        Pool pool2 = createPool(owner1, product2, 500L,
            TestUtil.createDate(2000, 1, 1), TestUtil.createDate(3000, 1, 1));

        SuggestedQuantityDTO suggested = new SuggestedQuantityDTO();
        suggested.setSuggested(1L);
        suggested.setIncrement(12L);
        Map<String, SuggestedQuantityDTO> suggestedMap = new HashMap<>();
        suggestedMap.put(pool2.getId(), suggested);
        when(quantityRules.getSuggestedQuantities(anyList(),
            any(Consumer.class), any(Date.class))).thenReturn(suggestedMap);

        Date date = new Date();
        attrUtil.setQuantityAttributes(pool2, consumer, date);
        assertEquals("1", pool2.getCalculatedAttributes().get("suggested_quantity"));
        assertEquals("12", pool2.getCalculatedAttributes().get("quantity_increment"));
    }
}
