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
package org.candlepin.resource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.model.EntitlementFilterBuilder;
import org.candlepin.resource.util.EntitlementFinderUtil;

import org.junit.jupiter.api.Test;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.Locale;


public class EntitlementFinderUtilTest {



    @Test
    public void nullFilterTest() {
        I18n i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        EntitlementFilterBuilder filters = EntitlementFinderUtil.createFilter(i18n, null, null);
        assertFalse(filters.hasMatchFilters());
    }

    @Test
    public void matchesFilterTest() {
        I18n i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        EntitlementFilterBuilder filters = EntitlementFinderUtil.createFilter(
            i18n, "matchesFilterTest", null);
        assertTrue(filters.hasMatchFilters());
    }
}
