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
package org.fedoraproject.candlepin.model.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.security.KeyPair;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.KeyPairCurator;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.junit.Before;
import org.junit.Test;

/**
 * KeyPairCuratorTest
 */
public class KeyPairCuratorTest extends DatabaseTestFixture {

    private KeyPairCurator keyPairCurator;

    @Before
    public void setUp() {
        keyPairCurator = injector.getInstance(KeyPairCurator.class);
    }

    @Test
    public void testSameConsumerGetsSameKey() {
        Owner owner = createOwner();

        Consumer consumer = createConsumer(owner);

        KeyPair keyPair1 = keyPairCurator.getConsumerKeyPair(consumer);
        KeyPair keyPair2 = keyPairCurator.getConsumerKeyPair(consumer);

        assertEquals(keyPair1.getPrivate(), keyPair2.getPrivate());
    }

    @Test
    public void testTwoConsumersGetDifferentKeys() {
        Owner owner = createOwner();

        Consumer consumer1 = createConsumer(owner);
        Consumer consumer2 = createConsumer(owner);

        KeyPair keyPair1 = keyPairCurator.getConsumerKeyPair(consumer1);
        KeyPair keyPair2 = keyPairCurator.getConsumerKeyPair(consumer2);

        assertFalse(keyPair1.getPrivate().equals(keyPair2.getPrivate()));
    }

}
