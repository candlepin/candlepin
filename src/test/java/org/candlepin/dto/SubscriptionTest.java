/*
 * Copyright (c) 2009 - 2025 Red Hat, Inc.
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
package org.candlepin.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.candlepin.model.Owner;
import org.candlepin.model.dto.Subscription;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

public class SubscriptionTest {

    @Test
    public void testIsOwnerAnonymousWithNullOwner() {
        Subscription subscription = new Subscription();

        assertFalse(subscription.isOwnerAnonymous());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = {true, false})
    public void testIsOwnerAnonymous(Boolean anonymous) {
        Owner owner = TestUtil.createOwner(TestUtil.randomString(), TestUtil.randomString())
            .setId(null)
            .setAnonymous(anonymous);

        Subscription subscription = new Subscription()
            .setOwner(owner);

        boolean expected = anonymous == null ? false : anonymous;
        assertEquals(expected, subscription.isOwnerAnonymous());
    }

}
