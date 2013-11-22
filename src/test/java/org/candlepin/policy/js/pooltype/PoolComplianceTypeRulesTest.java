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
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.Date;

import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.policy.js.JsRunnerProvider;
import org.candlepin.util.Util;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * PoolTypeRulesTest test class for testing raw
 * untranslated pool type responses from poolType
 * rules
 */
public class PoolComplianceTypeRulesTest {

    private PoolComplianceTypeRules poolTypeRules;

    @Mock private EntitlementCurator entCurator;
    @Mock private RulesCurator rulesCuratorMock;
    private JsRunnerProvider provider;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        // Load the default production rules:
        InputStream is = this.getClass().getResourceAsStream(
            RulesCurator.DEFAULT_RULES_FILE);
        Rules rules = new Rules(Util.readFile(is));
        when(rulesCuratorMock.getUpdated()).thenReturn(new Date());
        when(rulesCuratorMock.getRules()).thenReturn(rules);
        provider = new JsRunnerProvider(rulesCuratorMock);
        poolTypeRules = new PoolComplianceTypeRules(provider.get());
    }

    /*
     * Standard pools have no stacking id, multi ent,
     * or instance multiplier.
     */
    @Test
    public void testStandardPool() {
        Pool p = new Pool();
        PoolComplianceType pt = poolTypeRules.getPoolType(p);
        assertEquals("standard", pt.getRawPoolType());
    }

    @Test
    public void testStackablePool() {
        Pool p = new Pool();
        p.setProductAttribute("stacking_id", "5", "test");
        p.setProductAttribute("multi-entitlement", "yes", "test");
        PoolComplianceType pt = poolTypeRules.getPoolType(p);
        assertEquals("stackable", pt.getRawPoolType());
    }

    /*
     * Tests pools that only stack with other subscriptions,
     * but not themselves.
     */
    @Test
    public void testUniqueStackablePool() {
        Pool p = new Pool();
        p.setProductAttribute("stacking_id", "5", "test");
        PoolComplianceType pt = poolTypeRules.getPoolType(p);
        assertEquals("unique stackable", pt.getRawPoolType());
    }

    @Test
    public void testInstanceBasedPool() {
        Pool p = new Pool();
        p.setProductAttribute("stacking_id", "5", "test");
        p.setProductAttribute("multi-entitlement", "yes", "test");
        p.setProductAttribute("instance_multiplier", "2", "test");
        PoolComplianceType pt = poolTypeRules.getPoolType(p);
        assertEquals("instance based", pt.getRawPoolType());
    }

    /*
     * instance multiplier with no multi-ent, and no stacking id isn't
     * currently a known pool type
     */
    @Test
    public void testUnknownPool() {
        Pool p = new Pool();
        p.setProductAttribute("instance_multiplier", "2", "test");
        PoolComplianceType pt = poolTypeRules.getPoolType(p);
        assertEquals("unknown", pt.getRawPoolType());
    }
}
