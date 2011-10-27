/**
 * Copyright (c) 2009 Red Hat, Inc.
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
        Rules rules = new Rules("//these are the new rules");
        Rules newRules = rulesCurator.update(rules);
        rulesCurator.delete(newRules);
        Rules latestRules = rulesCurator.getRules();
        assertEquals(origRules.getRules(), latestRules.getRules());
    }

    // @Test
    // public void deleteNullRules() {
    // rulesCurator.delete();
    // }

    @Test
    public void getRules() {
        Rules rules = rulesCurator.getRules();
    }

    @Test
    public void deleteDefaultRules() {
        Rules rules = rulesCurator.getRules();
        rulesCurator.delete(rules);
        Rules defaultRules = rulesCurator.getRules();
    }

    @Test
    public void uploadRules() {
        Rules rules = new Rules("//these are the new rules");
        Rules newRules = rulesCurator.update(rules);
        Rules updateRules = rulesCurator.getRules();
        assertEquals(rules.getRules(), updateRules.getRules());
    }

    @Test
    public void uploadMultipleRules() {
        Rules rules = new Rules("// rules1 ");
        Rules newRules = rulesCurator.update(rules);
        Rules rules2 = new Rules("// rules2 ");
        Rules newRules2 = rulesCurator.update(rules2);
        Rules updateRules = rulesCurator.getRules();
        assertEquals(rules2.getRules(), updateRules.getRules());
    }
}
