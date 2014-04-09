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
package org.candlepin.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.LinkedList;
import java.util.List;

import org.candlepin.exceptions.BadRequestException;
import org.candlepin.model.ContentOverride;
import org.candlepin.policy.js.override.OverrideRules;
import org.candlepin.test.DatabaseTestFixture;
import org.junit.Before;
import org.junit.Test;

/**
 * ContentOverrideValidatorTest
 */
public class ContentOverrideValidatorTest extends DatabaseTestFixture  {

    private OverrideRules overrideRules;
    private ContentOverrideValidator validator;

    @Before
    public void setUp() {
        this.overrideRules = injector.getInstance(OverrideRules.class);
        validator = new ContentOverrideValidator(i18n, overrideRules);
    }

    @Test
    public void testValidateValidCollection() {
        List<ContentOverride> overrides = new LinkedList<ContentOverride>();
        overrides.add(new ContentOverride("label", "testname", "value"));
        overrides.add(new ContentOverride("other label", "other name", "other value"));

        validator.validate(overrides);
    }

    @Test
    public void testValidateValidOverride() {
        ContentOverride override = new ContentOverride("label", "testname", "value");
        validator.validate(override);
    }

    @Test
    public void testValidateSingleInvalid() {
        ContentOverride override = new ContentOverride("label", "baseurl", "value");

        boolean gotException = false;
        try {
            validator.validate(override);
        }
        catch (BadRequestException bre) {
            assertEquals("Not allowed to override values for: baseurl", bre.getMessage());
            gotException = true;
        }
        assertTrue(gotException);
    }

    @Test
    public void testValidateCollectionBothInvalid() {
        List<ContentOverride> overrides = new LinkedList<ContentOverride>();
        overrides.add(new ContentOverride("label", "baseurl", "value"));
        overrides.add(new ContentOverride("other label", "name", "other value"));

        boolean gotException = false;
        try {
            validator.validate(overrides);
        }
        catch (BadRequestException bre) {
            assertEquals("Not allowed to override values for:" +
                " name, baseurl", bre.getMessage());
            gotException = true;
        }
        assertTrue(gotException);
    }
}
