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

import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Pool.PoolComplianceType;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.test.DatabaseTestFixture;

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
    private I18n i18n;

    @Mock private Pool mockPool;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        Locale locale = new Locale("en_US");
        i18n = I18nFactory.getI18n(getClass(), "org.candlepin.i18n.Messages", locale,
            I18nFactory.FALLBACK);

        attrUtil = new CalculatedAttributesUtil(i18n);
    }

    @Test
    public void testCalculatedAttributesPresent() {
        for (PoolComplianceType type : PoolComplianceType.values()) {
            when(mockPool.getComplianceType()).thenReturn(type);

            Date date = new Date();
            Map<String, String> attrs = attrUtil.buildCalculatedAttributes(mockPool, date);

            assertTrue(attrs.containsKey("compliance_type"));
        }
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
}
