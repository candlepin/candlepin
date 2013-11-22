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
package org.candlepin.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.junit.Before;
import org.junit.Test;

/**
 * ConsumerPrincipalTest
 */
public class ConsumerPrincipalTest {

    private ConsumerPrincipal principal;
    private Consumer consumer;
    private Owner o;

    @Before
    public void init() {
        o = mock(Owner.class);
        when(o.getKey()).thenReturn("donaldduck");

        consumer = mock(Consumer.class);
        when(consumer.getUuid()).thenReturn("consumer-uuid");
        when(consumer.getOwner()).thenReturn(o);

        principal = new ConsumerPrincipal(consumer);
    }
    @Test
    public void noFullAccess() {
        assertFalse(new ConsumerPrincipal(consumer).hasFullAccess());
    }

    @Test
    public void type() {
        assertEquals("consumer", principal.getType());
    }

    @Test
    public void name() {
        when(consumer.getUuid()).thenReturn("ae5ba-some-name");
        assertEquals("ae5ba-some-name", principal.getPrincipalName());
    }

    @Test
    public void equalsNull() {
        assertFalse(principal.equals(null));
    }

    @Test
    public void equalsOtherObject() {
        assertFalse(principal.equals(new Object()));
    }

    @Test
    public void equalsAnotherConsumerPrincipal() {
        // create a new one with same consumer
        ConsumerPrincipal cp = new ConsumerPrincipal(consumer);
        assertTrue(principal.equals(cp));
    }

    @Test
    public void equalsDifferentConsumer() {
        Consumer c = mock(Consumer.class);
        ConsumerPrincipal cp = new ConsumerPrincipal(c);
        assertFalse(principal.equals(cp));
    }

    @Test
    public void getConsumer() {
        assertTrue(principal.getConsumer().equals(consumer));
    }

    @Test
    public void accessToConsumer() {
        Consumer c = mock(Consumer.class);
        when(c.getUuid()).thenReturn("consumer-uuid");
        assertTrue(principal.canAccess(consumer, SubResource.NONE, Access.ALL));
    }

    @Test
    public void accessToConsumerEntitlement() {
        Consumer c = mock(Consumer.class);
        when(c.getUuid()).thenReturn("consumer-uuid");
        Entitlement e = mock(Entitlement.class);
        when(e.getConsumer()).thenReturn(c);

        assertTrue(principal.canAccess(e, SubResource.NONE, Access.ALL));
    }

    @Test
    public void accessToPools() {
        Pool p = mock(Pool.class);
        when(p.getOwner()).thenReturn(o);

        when(consumer.getOwner()).thenReturn(o);

        assertTrue(principal.canAccess(p, SubResource.ENTITLEMENTS, Access.CREATE));
    }

    @Test
    public void accessToBindToPool() {
        Pool p = mock(Pool.class);
        when(p.getOwner()).thenReturn(o);

        when(consumer.getOwner()).thenReturn(o);

        assertTrue(principal.canAccess(p, SubResource.ENTITLEMENTS, Access.CREATE));
        assertFalse(principal.canAccess(p, SubResource.ENTITLEMENTS, Access.READ_ONLY));
    }

    @Test
    public void noAaccessToListEntitlementsInPool() {
        Pool p = mock(Pool.class);
        when(p.getOwner()).thenReturn(o);

        when(consumer.getOwner()).thenReturn(o);

        assertFalse(principal.canAccess(p, SubResource.ENTITLEMENTS, Access.READ_ONLY));
    }
}
