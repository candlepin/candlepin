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
package org.candlepin.resource.test;

import static org.junit.Assert.assertEquals;

import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.resource.RulesResource;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;
import org.apache.commons.codec.binary.Base64;

/**
 * RulesResourceTest
 */
public class RulesResourceTest extends DatabaseTestFixture {

    private RulesResource rulesResource;

    @Before
    public void setUp() {
        rulesResource = injector.getInstance(RulesResource.class);
    }

    @Test
    public void testUpload() {
        String rulesBuffer = new String(Base64.encodeBase64String((
            TestUtil.createRulesBlob(10000).getBytes())));
        rulesResource.upload(rulesBuffer);
        Rules rules = rulesCurator.getRules();
        String expected = "" + RulesCurator.RULES_API_VERSION + "." + 10000;
        assertEquals(expected, rules.getVersion());
    }

    @Test
    public void testGet() {
        String rulesBuffer = new String(Base64.encodeBase64String((
            TestUtil.createRulesBlob(10000).getBytes())));
        rulesResource.upload(rulesBuffer);
        String rulesBlob = rulesResource.get();
        assertEquals(rulesBlob, rulesBuffer);
    }

    @Test
    public void testDelete() {
        String origRules = rulesResource.get();
        String rulesBuffer = new String(Base64.encodeBase64String((
            TestUtil.createRulesBlob(10000).getBytes())));
        rulesResource.upload(rulesBuffer);
        rulesResource.delete();
        String rulesAfterDelete = rulesResource.get();
        assertEquals(rulesAfterDelete, origRules);
    }
}
