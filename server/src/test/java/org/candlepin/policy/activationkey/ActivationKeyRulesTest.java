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
package org.candlepin.policy.activationkey;

import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.policy.ValidationResult;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ActivationKeyRulesTest
 */
public class ActivationKeyRulesTest {

    private ActivationKeyRules actKeyRules;
    private static int poolid = 0;
    private I18n i18n;
    private Owner owner = TestUtil.createOwner();

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        Locale locale = new Locale("en_US");
        i18n = I18nFactory.getI18n(getClass(), "org.candlepin.i18n.Messages", locale,
            I18nFactory.FALLBACK);
        actKeyRules = new ActivationKeyRules(i18n);
    }

    @Test
    public void testActivationKeyRules() {
        ActivationKey key = new ActivationKey();
        key.addPool(genPool(), new Long(1));
        key.addPool(genPool(), new Long(1));

        Pool pool = genPool();
        ValidationResult result = actKeyRules.runPoolValidationForActivationKey(key, pool, new Long(1));
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void testActivationKeyRulesNoPools() {
        ActivationKey key = new ActivationKey();
        Pool pool = genPool();
        ValidationResult result = actKeyRules.runPoolValidationForActivationKey(key, pool, new Long(1));
        assertTrue(result.getErrors().isEmpty());
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
        ValidationResult result = actKeyRules.runPoolValidationForActivationKey(key, pool, new Long(1));
        assertTrue(result.getErrors().isEmpty());
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
        ValidationResult result = actKeyRules.runPoolValidationForActivationKey(key, pool, new Long(1));
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void testActivationKeyRulesBadQuantity() {
        ActivationKey key = new ActivationKey();
        Pool pool = genPool();
        ValidationResult result = actKeyRules.runPoolValidationForActivationKey(key, pool, new Long(-1));
        assertEquals(1, result.getErrors().size());
        assertEquals(ActivationKeyRules.ErrorKeys.INVALID_QUANTITY, result.getErrors().get(0));
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
        ValidationResult result = actKeyRules.runPoolValidationForActivationKey(key, pool, new Long(2));
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void testActivationKeyRulesUnlimitedQuantity() {
        ActivationKey key = new ActivationKey();
        Pool pool = genPool();
        // Unlimited
        pool.setQuantity(-1L);
        pool.setConsumed(4L);

        ValidationResult result = actKeyRules.runPoolValidationForActivationKey(key, pool, new Long(2));
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void testDoesntAllowPeoplePools() {
        ActivationKey key = new ActivationKey();
        Pool pool = genPoolForType("person");

        ValidationResult result = actKeyRules.runPoolValidationForActivationKey(key, pool, new Long(1));
        assertEquals(1, result.getErrors().size());
        assertEquals(ActivationKeyRules.ErrorKeys.CANNOT_USE_PERSON_POOLS, result.getErrors().get(0));
    }

    @Test
    public void testDoesAllowSameConsumerTypePools() {
        ActivationKey key = new ActivationKey();
        key.addPool(genPoolForType("system"), 1L);

        Pool pool = genPoolForType("system");
        ValidationResult result = actKeyRules.runPoolValidationForActivationKey(key, pool, new Long(1));
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void testDoesAllowSameHostRequires() {
        ActivationKey key = new ActivationKey();
        key.addPool(genHostRestricted("host1"), 1L);

        Pool pool = genHostRestricted("host1");
        ValidationResult result = actKeyRules.runPoolValidationForActivationKey(key, pool, new Long(1));
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void testNonMultientOne() {
        ActivationKey key = new ActivationKey();

        Pool pool = genNonMultiEnt();
        ValidationResult result = actKeyRules.runPoolValidationForActivationKey(key, pool, new Long(1));
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void testNonMultientMultipleQuantity() {
        ActivationKey key = new ActivationKey();

        Pool pool = genNonMultiEnt();
        ValidationResult result = actKeyRules.runPoolValidationForActivationKey(key, pool, new Long(2));
        assertEquals(1, result.getErrors().size());
        assertEquals(ActivationKeyRules.ErrorKeys.INVALID_NON_MULTIENT_QUANTITY,
            result.getErrors().get(0));
    }

    @Test
    public void testNonMultientOneTwice() {
        ActivationKey key = new ActivationKey();
        Pool pool = genNonMultiEnt();
        key.addPool(pool, 1L);

        ValidationResult result = actKeyRules.runPoolValidationForActivationKey(key, pool, new Long(1));
        assertEquals(1, result.getErrors().size());
        assertEquals(ActivationKeyRules.ErrorKeys.ALREADY_EXISTS, result.getErrors().get(0));
    }

    @Test
    public void testNullQuantityInstanceAndPhysicalOnly() {
        ActivationKey key = new ActivationKey();
        key.addPool(genInstanceBased(), null);

        ValidationResult result = actKeyRules.runPoolValidationForActivationKey(
            key, genPhysOnlyPool(), new Long(1));
        assertTrue(result.getErrors().isEmpty());
    }

    private Pool genPool() {
        Pool pool = new Pool();
        pool.setId("" + poolid++);
        pool.setQuantity(10L);
        pool.setConsumed(4L);
        pool.setAttribute(Pool.Attributes.MULTI_ENTITLEMENT, "yes");
        pool.setOwner(owner);
        pool.setProduct(TestUtil.createProduct());
        return pool;
    }

    private Pool genNonMultiEnt() {
        Pool pool = genPool();
        pool.setAttribute(Pool.Attributes.MULTI_ENTITLEMENT, "no");
        return pool;
    }

    private Pool genVirtOnlyPool() {
        Pool pool = genPool();
        pool.setAttribute(Product.Attributes.VIRT_ONLY, "true");
        return pool;
    }

    private Pool genPhysOnlyPool() {
        Pool pool = genPool();
        pool.setAttribute(Pool.Attributes.PHYSICAL_ONLY, "true");
        return pool;
    }

    private Pool genInstanceBased() {
        Pool pool = genPool();
        pool.setAttribute(Product.Attributes.INSTANCE_MULTIPLIER, "2");
        return pool;
    }

    private Pool genPoolForType(String type) {
        Pool pool = genPool();
        pool.setAttribute(Pool.Attributes.REQUIRES_CONSUMER_TYPE, type);
        return pool;
    }

    private Pool genHostRestricted(String host) {
        Pool pool = genPool();
        pool.setAttribute(Pool.Attributes.REQUIRES_HOST, host);
        return pool;
    }
}
