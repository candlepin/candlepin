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
package org.candlepin.policy.js.pooltype;

import static org.junit.Assert.assertEquals;

import java.util.Locale;

import org.junit.Before;
import org.junit.Test;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

/**
 * PoolTypeTest
 *
 * Test translation of raw pool types from rules.js
 * into more readable types.
 */
public class PoolComplianceTypeTest {

    private I18n i18n;

    @Before
    public void setUp() {
        Locale locale = new Locale("en_US");
        i18n = I18nFactory.getI18n(getClass(), "org.candlepin.i18n.Messages", locale,
            I18nFactory.FALLBACK);
    }

    @Test
    public void standardTypeTest() {
        PoolComplianceType pt = createPoolType("standard");
        assertEquals("Standard", pt.getPoolType());
    }

    @Test
    public void stackableTypeTest() {
        PoolComplianceType pt = createPoolType("stackable");
        assertEquals("Stackable", pt.getPoolType());
    }

    @Test
    public void unknownTypeTest() {
        PoolComplianceType pt = createPoolType("unknown");
        assertEquals("Other", pt.getPoolType());
    }

    @Test
    public void uniqueStackableTypeTest() {
        PoolComplianceType pt = createPoolType("unique stackable");
        assertEquals("Stackable only with other subscriptions", pt.getPoolType());
    }

    @Test
    public void multientTypeTest() {
        PoolComplianceType pt = createPoolType("multi entitlement");
        assertEquals("Multi-Entitleable", pt.getPoolType());
    }

    /*
     * If there are no translatable matches, we should
     * return the raw value as is.
     */
    @Test
    public void defaultTypeTest() {
        PoolComplianceType pt = createPoolType("not a real pool type");
        assertEquals("not a real pool type", pt.getPoolType());
    }

    /*
     * Creates a PoolType, as would be returned from
     * PoolTypeRules, and translates it.
     */
    private PoolComplianceType createPoolType(String rawType) {
        PoolComplianceType result = new PoolComplianceType();
        result.setRawPoolType(rawType);
        result.translatePoolType(i18n);
        return result;
    }
}
