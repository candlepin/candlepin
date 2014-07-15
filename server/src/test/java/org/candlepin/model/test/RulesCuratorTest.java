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
package org.candlepin.model.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.candlepin.model.Rules;
import org.candlepin.test.DatabaseTestFixture;
import org.junit.Test;

/**
 * RulesCuratorTest
 */
public class RulesCuratorTest extends DatabaseTestFixture {

    @Test
    public void deleteRules() {
        Rules origRules = rulesCurator.getRules();
        Rules rules = new Rules("// Version: 2.0\n//these are the new rules");
        Rules newRules = rulesCurator.update(rules);
        rulesCurator.delete(newRules);
        Rules latestRules = rulesCurator.getRules();
        assertEquals(origRules.getRules(), latestRules.getRules());
    }

    @Test
    public void ignoreOldRulesInDb() throws Exception {
        Rules oldRules = new Rules("// Version: 1.9\n//oldrules");
        rulesCurator.create(oldRules);
        Rules rules = rulesCurator.getRules();
        assertFalse("1.9".equals(rules.getVersion()));
    }

    /*
     * While this is a little unorthodox, we need to make sure we stop slipping in use
     * of "for each", which is not a part of standard Javascript and thus a problem for
     * those who are using our rules with other interpreters.
     */
    @Test
    public void noForEachInRules() throws Exception {
        Rules rules = rulesCurator.getRules();
        assertEquals(-1, rules.getRules().indexOf("for each"));
    }

    @Test
    public void ignoreOldRulesInDbDefaultVersion() throws Exception {
        // Default version set by upgrade script:
        Rules oldRules = new Rules("// Version: 0.0\n//oldrules");
        rulesCurator.create(oldRules);
        Rules rules = rulesCurator.getRules();
        assertFalse("0.0".equals(rules.getVersion()));
    }

    @Test
    public void getRules() {
        rulesCurator.getRules();
    }

    @Test
    public void deleteDefaultRules() {
        Rules rules = rulesCurator.getRules();
        rulesCurator.delete(rules);
        rulesCurator.getRules();
    }

    @Test
    public void uploadRules() {
        Rules rules = new Rules("// Version: 5.1000\n//these are the new rules");
        rulesCurator.update(rules);
        Rules updateRules = rulesCurator.getRules();
        assertEquals(rules.getRules(), updateRules.getRules());
    }

    @Test
    public void uploadMultipleRules() {
        Rules rules = new Rules("// Version: 5.1000\n// rules1 ");
        rulesCurator.update(rules);
        Rules rules2 = new Rules("// Version: 5.1001\n// rules2 ");
        rulesCurator.update(rules2);
        Rules updateRules = rulesCurator.getRules();
        assertEquals(rules2.getRules(), updateRules.getRules());
    }

}
