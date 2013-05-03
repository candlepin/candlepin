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
package org.candlepin.policy.js.entitlement.test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Pool;
import org.candlepin.model.Subscription;
import org.candlepin.policy.js.JsRunnerProvider;
import org.candlepin.policy.js.entitlement.Enforcer;
import org.candlepin.policy.js.entitlement.ManifestEntitlementRules;
import org.candlepin.policy.js.pool.PoolHelper;
import org.candlepin.util.DateSourceImpl;
import org.junit.Test;
import org.xnap.commons.i18n.I18nFactory;

/**
 * PostEntitlementRulesTest: Tests for post-entitlement rules, as well as the post-unbind
 * rules which tend to clean up after them.
 *
 * These tests only cover standalone/universal situations. See hosted specific test
 * suites for behaviour which is specific to hosted.
 */
public class PostEntitlementRulesTest extends EntitlementRulesTextFixture {

    @Test
    public void userLicensePostCreatesSubPool() {
        Pool pool = setupUserLicensedPool();
        consumer.setType(new ConsumerType(ConsumerTypeEnum.PERSON));
        Entitlement e = new Entitlement(pool, consumer, new Date(), new Date(),
            1);

        PoolHelper postHelper = mock(PoolHelper.class);
        when(postHelper.getFlattenedAttributes(eq(pool))).thenReturn(
            attrHelper.getFlattenedAttributes(pool));
        enforcer.postEntitlement(consumer, postHelper, e);
        verify(postHelper).createUserRestrictedPool(pool.getProductId(), pool,
            "unlimited");
    }

    @Test
    public void testUserLicensePostForDifferentProduct() {
        Pool pool = setupUserLicensedPool();
        String subProductId = "subProductId";
        pool.setAttribute("user_license_product", subProductId);

        consumer.setType(new ConsumerType(ConsumerTypeEnum.PERSON));
        Entitlement e = new Entitlement(pool, consumer, new Date(), new Date(),
            1);

        PoolHelper postHelper = mock(PoolHelper.class);
        when(postHelper.getFlattenedAttributes(eq(pool))).thenReturn(
            attrHelper.getFlattenedAttributes(pool));
        enforcer.postEntitlement(consumer, postHelper, e);
        verify(postHelper).createUserRestrictedPool(subProductId, pool,
            "unlimited");
    }

    @Test
    public void virtLimitSubPool() {
        Pool pool = setupVirtLimitPool();
        Entitlement e = new Entitlement(pool, consumer, new Date(), new Date(),
            5);

        PoolHelper postHelper = mock(PoolHelper.class);
        when(postHelper.getFlattenedAttributes(eq(pool))).thenReturn(
            attrHelper.getFlattenedAttributes(pool));
        when(config.standalone()).thenReturn(true);
        enforcer.postEntitlement(consumer, postHelper, e);

        // Pool quantity should be virt_limit * entitlement quantity:
        verify(postHelper).createHostRestrictedPool(eq(pool.getProductId()),
            eq(pool), eq("50"));
    }

    @Test
    public void unlimitedVirtLimitSubPool() {
        Pool pool = setupVirtLimitPool();
        pool.setAttribute("virt_limit", "unlimited");
        Entitlement e = new Entitlement(pool, consumer, new Date(), new Date(),
            5);

        PoolHelper postHelper = mock(PoolHelper.class);
        when(postHelper.getFlattenedAttributes(eq(pool))).thenReturn(
            attrHelper.getFlattenedAttributes(pool));
        when(config.standalone()).thenReturn(true);
        enforcer.postEntitlement(consumer, postHelper, e);

        // Pool quantity should be virt_limit * entitlement quantity:
        verify(postHelper).createHostRestrictedPool(eq(pool.getProductId()),
            eq(pool), eq("unlimited"));
    }

    /*
     * Bonus pools should not be created when performing distributor binds.
     */
    @Test
    public void noBonusPoolsForDistributorBinds() {
        when(config.standalone()).thenReturn(true);
        Consumer c = new Consumer("test consumer", "test user", owner,
            new ConsumerType(ConsumerTypeEnum.CANDLEPIN));
        Enforcer enf = new ManifestEntitlementRules(new DateSourceImpl(),
            new JsRunnerProvider(rulesCurator).get(),
            productCache, I18nFactory.getI18n(getClass(), Locale.US,
                I18nFactory.FALLBACK), config, consumerCurator);
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, "unlimited");
        List<Pool> pools = poolRules.createPools(s);
        assertEquals(1, pools.size());

        Pool physicalPool = pools.get(0);
        physicalPool.setId("physical");

        assertEquals(new Long(10), physicalPool.getQuantity());
        assertEquals(0, physicalPool.getAttributes().size());

        Entitlement e = new Entitlement(physicalPool, c, new Date(), new Date(),
            1);
        PoolHelper postHelper = new PoolHelper(poolManagerMock, productCache, e);

        enf.postEntitlement(c, postHelper, e);
        verify(poolManagerMock, never()).createPool(any(Pool.class));
        verify(poolManagerMock, never()).updatePoolQuantity(any(Pool.class), anyInt());

        enf.postUnbind(c, postHelper, e);
        verify(poolManagerMock, never()).updatePoolQuantity(any(Pool.class), anyInt());
        verify(poolManagerMock, never()).setPoolQuantity(any(Pool.class), anyLong());
    }
}
