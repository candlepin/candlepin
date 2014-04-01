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
package org.candlepin.policy.js.activationkey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.Date;
import java.util.Locale;

import org.candlepin.exceptions.BadRequestException;
import org.candlepin.model.Pool;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.policy.ValidationResult;
import org.candlepin.policy.js.JsRunnerProvider;
import org.candlepin.util.Util;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

/**
 * ActivationKeyRulesTest
 */
public class ActivationKeyRulesTest {

    private ActivationKeyRules actKeyRules;
    private static int poolid = 0;

    @Mock private RulesCurator rulesCuratorMock;
    private I18n i18n;
    private JsRunnerProvider provider;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        Locale locale = new Locale("en_US");
        i18n = I18nFactory.getI18n(getClass(), "org.candlepin.i18n.Messages", locale,
            I18nFactory.FALLBACK);
        // Load the default production rules:
        InputStream is = this.getClass().getResourceAsStream(
            RulesCurator.DEFAULT_RULES_FILE);
        Rules rules = new Rules(Util.readFile(is));
        when(rulesCuratorMock.getUpdated()).thenReturn(new Date());
        when(rulesCuratorMock.getRules()).thenReturn(rules);
        provider = new JsRunnerProvider(rulesCuratorMock);
        actKeyRules = new ActivationKeyRules(provider.get(), i18n);
    }

    @Test
    public void testActivationKeyRules() {
        ActivationKey key = new ActivationKey();
        key.addPool(genPool(), new Long(1));
        key.addPool(genPool(), new Long(1));

        Pool pool = genPool();
        ValidationResult result = actKeyRules.runPreActKey(key, pool, new Long(1));
        assertTrue(result.getErrors().isEmpty());
        assertTrue(result.getWarnings().isEmpty());
    }

    @Test
    public void testActivationKeyRulesNoPools() {
        ActivationKey key = new ActivationKey();
        Pool pool = genPool();
        ValidationResult result = actKeyRules.runPreActKey(key, pool, new Long(1));
        assertTrue(result.getErrors().isEmpty());
        assertTrue(result.getWarnings().isEmpty());
    }

    @Test
    public void testVirtOnlyOnKeyWithPhysical() {
        ActivationKey key = new ActivationKey();
        key.addPool(genPhysOnlyPool(), new Long(1));

        Pool pool = genVirtOnlyPool();
        ValidationResult result = actKeyRules.runPreActKey(key, pool, new Long(1));
        assertTrue(result.getWarnings().isEmpty());
        assertEquals(1, result.getErrors().size());
        String expected = "rulefailed.virtonly.on.physical.key";
        assertEquals(expected, result.getErrors().get(0).getResourceKey());
    }

    @Test
    public void testPhysicalOnlyOnKeyWithVirt() {
        ActivationKey key = new ActivationKey();
        key.addPool(genVirtOnlyPool(), new Long(1));

        Pool pool = genPhysOnlyPool();
        ValidationResult result = actKeyRules.runPreActKey(key, pool, new Long(1));
        assertTrue(result.getWarnings().isEmpty());
        assertEquals(1, result.getErrors().size());
        String expected = "rulefailed.physicalonly.on.virt.key";
        assertEquals(expected, result.getErrors().get(0).getResourceKey());
        try {
            actKeyRules.validatePoolForActKey(key, pool, new Long(1));
            fail("Should have thrown an exception");
        }
        catch (BadRequestException bre) {
            String expectedMsg = "Cannot add physical pool '" + pool.getId() +
                "' to activation key for virtual systems.";
            assertEquals(expectedMsg, bre.getMessage());
        }
    }

    @Test
    public void testNullQuantityEmptyPool() {
        ActivationKey key = new ActivationKey();
        Pool existing = genPool();
        existing.setConsumed(existing.getQuantity());
        key.addPool(genVirtOnlyPool(), null);

        Pool pool = genPhysOnlyPool();
        ValidationResult result = actKeyRules.runPreActKey(key, pool, new Long(1));
        assertTrue(result.getWarnings().isEmpty());
        assertEquals(1, result.getErrors().size());
        String expected = "rulefailed.physicalonly.on.virt.key";
        assertEquals(expected, result.getErrors().get(0).getResourceKey());
    }

    /*
     * This should be identical to the previous test.  Instance based pool
     * with quantity one can only be used on virtual systems, so the key
     * should be treated as virt only.
     */
    @Test
    public void testPhysicalOnlyOnKeyWithOneInstanceBased() {
        ActivationKey key = new ActivationKey();
        key.addPool(genInstanceBased(), new Long(1));

        Pool pool = genPhysOnlyPool();
        ValidationResult result = actKeyRules.runPreActKey(key, pool, new Long(1));
        assertTrue(result.getWarnings().isEmpty());
        assertEquals(1, result.getErrors().size());
        String expected = "rulefailed.physicalonly.on.virt.key";
        assertEquals(expected, result.getErrors().get(0).getResourceKey());
    }

    /*
     * Similar to above, but because quantity of the instance based
     * subscription is null, it will attempt to bind the correct
     * amount for physical or virtual systems.
     */
    @Test
    public void testPhysicalOnlyOnKeyWithNullInstanceBased() {
        ActivationKey key = new ActivationKey();
        key.addPool(genInstanceBased(), null);

        Pool pool = genPhysOnlyPool();
        // Should be a valid combination
        ValidationResult result = actKeyRules.runPreActKey(key, pool, new Long(1));
        assertTrue(result.getWarnings().isEmpty());
        assertTrue(result.getErrors().isEmpty());
    }

    /*
     * Activation key already has a physical only pool, adding an invalid quantity
     * of an instance based subscription (for physical) should cause a failure.
     */
    @Test
    public void testAlreadyHasPhysAddingVirtQuantityInstanceBased() {
        ActivationKey key = new ActivationKey();
        key.addPool(genPhysOnlyPool(), new Long(1));

        Pool pool = genInstanceBased();
        ValidationResult result = actKeyRules.runPreActKey(key, pool, new Long(1));
        assertTrue(result.getWarnings().isEmpty());
        assertEquals(1, result.getErrors().size());
        String expected = "rulefailed.invalid.quantity.instancebased.physical";
        assertEquals(expected, result.getErrors().get(0).getResourceKey());
        try {
            actKeyRules.validatePoolForActKey(key, pool, new Long(1));
            fail("Should have thrown an exception");
        }
        catch (BadRequestException bre) {
            String expectedMsg = "Activation key with for physical systems can only" +
                " use quantities of pool '" + pool.getId() + "' evenly divisible by 2";
            assertEquals(expectedMsg, bre.getMessage());
        }
    }

    /*
     * Adding an invalid (physical) quantity of an instance based subscription
     * should not cause a failure if there are no physical pools.
     */
    @Test
    public void testAllowsAddingVirtQuantityInstanceBased() {
        ActivationKey key = new ActivationKey();
        key.addPool(genPool(), new Long(1));

        Pool pool = genInstanceBased();
        ValidationResult result = actKeyRules.runPreActKey(key, pool, new Long(1));
        assertTrue(result.getWarnings().isEmpty());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void testActivationKeyRulesBadQuantity() {
        ActivationKey key = new ActivationKey();
        Pool pool = genPool();
        ValidationResult result = actKeyRules.runPreActKey(key, pool, new Long(-1));
        assertTrue(result.getWarnings().isEmpty());
        assertEquals(1, result.getErrors().size());
        String expected = "rulefailed.invalid.quantity";
        assertEquals(expected, result.getErrors().get(0).getResourceKey());
    }

    /*
     * the number of available subscriptions shouldn't matter, only
     * the total number
     */
    @Test
    public void testActivationKeyRulesSufficientQuantity() {
        ActivationKey key = new ActivationKey();
        Pool pool = genPool();
        pool.setQuantity(5L);
        pool.setConsumed(4L);

        // Attempting to overconsume the pool
        ValidationResult result = actKeyRules.runPreActKey(key, pool, new Long(2));
        assertTrue(result.getWarnings().isEmpty());
        assertTrue(result.getErrors().isEmpty());
    }

    /*
     * Because the key can only be used on physical consumers, null quantity
     * should be evaluated to two, and there are at most one available.
     */
    @Test
    public void testActivationKeyRulesinSufficientQuantity() {
        ActivationKey key = new ActivationKey();
        Pool pool = genPhysOnlyPool();
        key.addPool(pool, 1L);

        Pool instanceBased = this.genInstanceBased();
        instanceBased.setQuantity(1L);
        instanceBased.setConsumed(0L);

        // Attempting to overconsume the pool
        ValidationResult result = actKeyRules.runPreActKey(key, instanceBased, null);
        assertTrue(result.getWarnings().isEmpty());
        assertEquals(1, result.getErrors().size());
        String expected = "rulefailed.insufficient.quantity";
        assertEquals(expected, result.getErrors().get(0).getResourceKey());
    }

    @Test
    public void testActivationKeyRulesUnlimitedQuantity() {
        ActivationKey key = new ActivationKey();
        Pool pool = genPool();
        // Unlimited
        pool.setQuantity(-1L);
        pool.setConsumed(4L);

        ValidationResult result = actKeyRules.runPreActKey(key, pool, new Long(2));
        assertTrue(result.getWarnings().isEmpty());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void testDoesntAllowPeoplePools() {
        ActivationKey key = new ActivationKey();
        Pool pool = genPoolForType("person");

        ValidationResult result = actKeyRules.runPreActKey(key, pool, new Long(1));
        assertTrue(result.getWarnings().isEmpty());
        assertEquals(1, result.getErrors().size());
        String expected = "rulefailed.actkey.cannot.use.person.pools";
        assertEquals(expected, result.getErrors().get(0).getResourceKey());
    }

    @Test
    public void testDoesntAllowMismatchedConsumerTypePools() {
        ActivationKey key = new ActivationKey();
        key.addPool(genPoolForType("system"), 1L);

        Pool pool = genPoolForType("hypervisor");

        ValidationResult result = actKeyRules.runPreActKey(key, pool, new Long(1));
        assertTrue(result.getWarnings().isEmpty());
        assertEquals(1, result.getErrors().size());
        String expected = "rulefailed.actkey.single.consumertype";
        assertEquals(expected, result.getErrors().get(0).getResourceKey());
    }

    @Test
    public void testDoesAllowSameConsumerTypePools() {
        ActivationKey key = new ActivationKey();
        key.addPool(genPoolForType("system"), 1L);

        Pool pool = genPoolForType("system");
        ValidationResult result = actKeyRules.runPreActKey(key, pool, new Long(1));
        assertTrue(result.getWarnings().isEmpty());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void testDoesntAllowMismatchedHostRequires() {
        ActivationKey key = new ActivationKey();
        key.addPool(genHostRestricted("host1"), 1L);

        Pool pool = genHostRestricted("host2");

        ValidationResult result = actKeyRules.runPreActKey(key, pool, new Long(1));
        assertTrue(result.getWarnings().isEmpty());
        assertEquals(1, result.getErrors().size());
        String expected = "rulefailed.multiple.host.restrictions";
        assertEquals(expected, result.getErrors().get(0).getResourceKey());
    }

    @Test
    public void testDoesAllowSameHostRequires() {
        ActivationKey key = new ActivationKey();
        key.addPool(genHostRestricted("host1"), 1L);

        Pool pool = genHostRestricted("host1");
        ValidationResult result = actKeyRules.runPreActKey(key, pool, new Long(1));
        assertTrue(result.getWarnings().isEmpty());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void testNonMultientOne() {
        ActivationKey key = new ActivationKey();

        Pool pool = genNonMultiEnt();
        ValidationResult result = actKeyRules.runPreActKey(key, pool, new Long(1));
        assertTrue(result.getWarnings().isEmpty());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void testNonMultientMultipleQuantity() {
        ActivationKey key = new ActivationKey();

        Pool pool = genNonMultiEnt();
        ValidationResult result = actKeyRules.runPreActKey(key, pool, new Long(2));
        assertTrue(result.getWarnings().isEmpty());
        assertEquals(1, result.getErrors().size());
        String expected = "rulefailed.invalid.nonmultient.quantity";
        assertEquals(expected, result.getErrors().get(0).getResourceKey());
    }

    @Test
    public void testNonMultientOneTwice() {
        ActivationKey key = new ActivationKey();
        Pool pool = genNonMultiEnt();
        key.addPool(pool, 1L);

        ValidationResult result = actKeyRules.runPreActKey(key, pool, new Long(1));
        assertTrue(result.getWarnings().isEmpty());
        assertEquals(1, result.getErrors().size());
        String expected = "rulefailed.already.exists";
        assertEquals(expected, result.getErrors().get(0).getResourceKey());
    }

    @Test
    public void testNullQuantityInstanceAndPhysicalOnly() {
        ActivationKey key = new ActivationKey();
        key.addPool(genInstanceBased(), null);

        ValidationResult result = actKeyRules.runPreActKey(key, genPhysOnlyPool(), new Long(1));
        assertTrue(result.getWarnings().isEmpty());
        assertTrue(result.getErrors().isEmpty());
    }

    private Pool genPool() {
        Pool pool = new Pool();
        pool.setId("" + poolid++);
        pool.setQuantity(10L);
        pool.setConsumed(4L);
        pool.setAttribute("multi-entitlement", "yes");
        return pool;
    }

    private Pool genNonMultiEnt() {
        Pool pool = genPool();
        pool.setAttribute("multi-entitlement", "no");
        return pool;
    }

    private Pool genVirtOnlyPool() {
        Pool pool = genPool();
        pool.setAttribute("virt_only", "true");
        return pool;
    }

    private Pool genPhysOnlyPool() {
        Pool pool = genPool();
        pool.setAttribute("physical_only", "true");
        return pool;
    }

    private Pool genInstanceBased() {
        Pool pool = genPool();
        pool.setAttribute("instance_multiplier", "2");
        return pool;
    }

    private Pool genPoolForType(String type) {
        Pool pool = genPool();
        pool.setAttribute("requires_consumer_type", type);
        return pool;
    }

    private Pool genHostRestricted(String host) {
        Pool pool = genPool();
        pool.setAttribute("requires_host", host);
        return pool;
    }
}
