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
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

import org.candlepin.model.ConsumerType;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.KeyPair;
import org.candlepin.model.Owner;
import org.candlepin.model.UpstreamConsumer;
import org.junit.Before;
import org.junit.Test;

/**
 * UpstreamConsumerTest
 */
public class UpstreamConsumerTest {
    private UpstreamConsumer uc = null;

    @Before
    public void init() {
        uc = new UpstreamConsumer();
    }

    @Test
    public void ctor() {
        Owner o = mock(Owner.class);
        ConsumerType ct = mock(ConsumerType.class);
        UpstreamConsumer luc = new UpstreamConsumer("fake name", o, ct);
        assertNotNull(luc.getUuid());
        assertEquals("fake name", luc.getName());
        assertEquals(o.getId(), luc.getOwnerId());
        assertEquals(ct, luc.getType());
    }

    @Test
    public void defaultCtor() {
        UpstreamConsumer luc = new UpstreamConsumer();
        assertNotNull(luc.getUuid());
        assertEquals(null, luc.getName());
        assertEquals(null, luc.getOwnerId());
        assertEquals(null, luc.getType());
    }

    @Test
    public void uuidNotAffectedAfterBeingSet() {
        uc.setUuid("test-uuid");
        uc.ensureUUID();

        // make sure ensureUUID doesn't touch uuid if
        // it has already been set
        assertEquals("test-uuid", uc.getUuid());
    }

    @Test
    public void owner() {
        Owner o = mock(Owner.class);
        uc.setOwnerId(o.getId());
        assertEquals(o.getId(), uc.getOwnerId());
    }

    @Test
    public void name() {
        uc.setName("fake name");
        assertEquals("fake name", uc.getName());
    }

    @Test
    public void id() {
        uc.setId("10");
        assertEquals("10", uc.getId());
    }

    @Test
    public void type() {
        ConsumerType ct = mock(ConsumerType.class);
        uc.setType(ct);
        assertEquals(ct, uc.getType());
    }

    @Test
    public void idCert() {
        IdentityCertificate ic = mock(IdentityCertificate.class);
        uc.setIdCert(ic);
        assertEquals(ic, uc.getIdCert());
    }

    @Test
    public void webUrl() {
        uc.setWebUrl("some-fake-url");
        assertEquals("some-fake-url", uc.getWebUrl());
    }

    @Test
    public void apiUrl() {
        uc.setApiUrl("some-fake-url");
        assertEquals("some-fake-url", uc.getApiUrl());
    }

    @Test
    public void keypair() {
        KeyPair kp = mock(KeyPair.class);
        uc.setKeyPair(kp);
        assertEquals(kp, uc.getKeyPair());
    }
}
