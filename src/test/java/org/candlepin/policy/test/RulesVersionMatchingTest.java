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
package org.candlepin.policy.test;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collection;

import org.candlepin.model.Rules;
import org.candlepin.policy.js.RuleParseException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * RulesVersionmatchingTest
 */
@RunWith(Parameterized.class)
public class RulesVersionMatchingTest {

    private String version;
    private String expectedVersion;
    private boolean expectedToBeValid;

    @Parameters
    public static Collection<Object[]> data() {
        Object[][] parameters = new Object[][] {
            // Valid version strings
            { "// Version: 1.0", "1.0", true },
            { "// version: 1.0", "1.0", true },
            { "# Version: 1.0", "1.0", true },
            { "# version: 1.0", "1.0", true },
            { "//Version: 1.0", "1.0", true },
            { "//version: 1.0", "1.0", true },
            { "// Version:    1.0  ", "1.0", true },
            { "// Version: 1.0.0", "1.0.0", true },
            { "//version:1.0.0", "1.0.0", true },
            { "// Version: 1.0.0\n// This is a new line", "1.0.0", true },

            // invalid version strings.
            { "", "", false },
            { "// Version:  ", "", false },
            { "// Version 1.0", "", false },
            { "Version 1.0", "", false },
            { "// Version: version", "", false },
            { "// Version: 1.0.", "", false },
            { "// Version: 1.0 RC", "", false },
            { "// Version: 1.x", "", false },
            { "THISisNOTaVALIDVersion: 1", "", false },
        };
        return Arrays.asList(parameters);
    }

    public RulesVersionMatchingTest(String version, String expectedVersion,
        boolean expectedToBeValid) {
        this.version = version;
        this.expectedToBeValid = expectedToBeValid;
        this.expectedVersion = expectedVersion;
    }

    @Test
    public void ensureWorking() {
        Rules rules = null;
        try {
            rules = new Rules(version);
            if (!expectedToBeValid) {
                throw new RuntimeException("Expected rule parsing to have failed.");
            }
            assertEquals(expectedVersion, rules.getVersion());
        }
        catch (RuleParseException e) {
            if (expectedToBeValid) {
                throw e;
            }
        }
    }

}
