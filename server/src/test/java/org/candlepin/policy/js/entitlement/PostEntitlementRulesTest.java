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
package org.candlepin.policy.js.entitlement;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.config.ConfigProperties;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Pool;
import org.candlepin.policy.js.pool.PoolHelper;

import org.junit.Test;

/**
 * PostEntitlementRulesTest: Tests for post-entitlement rules, as well as the post-unbind
 * rules which tend to clean up after them.
 *
 * These tests only cover standalone/universal situations. See hosted specific test
 * suites for behaviour which is specific to hosted.
 */
public class PostEntitlementRulesTest extends EntitlementRulesTestFixture {

    @Test
    public void virtLimitSubPool() {
        Pool pool = setupVirtLimitPool();
        Entitlement e = new Entitlement(pool, consumer, 5);

        PoolHelper postHelper = mock(PoolHelper.class);
        when(postHelper.getFlattenedAttributes(eq(pool))).thenReturn(
            attrHelper.getFlattenedAttributes(pool));
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(true);
        enforcer.postEntitlement(consumer, postHelper, e);

        // Pool quantity should be virt_limit:
        verify(postHelper).createHostRestrictedPool(eq(pool.getProductId()),
            eq(pool), eq("10"));
    }

    @Test
    public void unlimitedVirtLimitSubPool() {
        Pool pool = setupVirtLimitPool();
        pool.setAttribute("virt_limit", "unlimited");
        Entitlement e = new Entitlement(pool, consumer, 5);

        PoolHelper postHelper = mock(PoolHelper.class);
        when(postHelper.getFlattenedAttributes(eq(pool))).thenReturn(
            attrHelper.getFlattenedAttributes(pool));
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(true);
        enforcer.postEntitlement(consumer, postHelper, e);

        // Pool quantity should be virt_limit * entitlement quantity:
        verify(postHelper).createHostRestrictedPool(eq(pool.getProductId()),
            eq(pool), eq("unlimited"));
    }

    // Sub-pools should not be created when distributors bind:
    @Test
    public void noSubPoolsForDistributorBinds() {
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(true);
        consumer.setType(new ConsumerType(ConsumerTypeEnum.CANDLEPIN));
        Pool pool = setupVirtLimitPool();
        Entitlement e = new Entitlement(pool, consumer, 1);

        PoolHelper postHelper = new PoolHelper(poolManagerMock, productCache, e);

        enforcer.postEntitlement(consumer, postHelper, e);
        verify(poolManagerMock, never()).createPool(any(Pool.class));
        verify(poolManagerMock, never()).updatePoolQuantity(any(Pool.class), anyInt());

        enforcer.postUnbind(consumer, postHelper, e);
        verify(poolManagerMock, never()).updatePoolQuantity(any(Pool.class), anyInt());
        verify(poolManagerMock, never()).setPoolQuantity(any(Pool.class), anyLong());
    }

    // Sub-pools should not be created when guests bind:
    @Test
    public void noSubPoolsForGuestBinds() {
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(true);
        Pool pool = setupVirtLimitPool();
        consumer.setFact("virt.is_guest", "true");
        Entitlement e = new Entitlement(pool, consumer, 1);

        PoolHelper postHelper = new PoolHelper(poolManagerMock, productCache, e);

        enforcer.postEntitlement(consumer, postHelper, e);
        verify(poolManagerMock, never()).createPool(any(Pool.class));
        verify(poolManagerMock, never()).updatePoolQuantity(any(Pool.class), anyInt());

        enforcer.postUnbind(consumer, postHelper, e);
        verify(poolManagerMock, never()).updatePoolQuantity(any(Pool.class), anyInt());
        verify(poolManagerMock, never()).setPoolQuantity(any(Pool.class), anyLong());
    }
}
