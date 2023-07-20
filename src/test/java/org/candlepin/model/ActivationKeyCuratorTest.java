/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.test.DatabaseTestFixture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;



class ActivationKeyCuratorTest extends DatabaseTestFixture {

    private Owner owner;

    @BeforeEach
    public void setUp() {
        owner = new Owner()
            .setKey("test-owner")
            .setDisplayName("Test Owner");

        owner = ownerCurator.create(owner);
    }

    @Test
    void noActivationKeysFound() {
        List<ActivationKey> foundKeys = activationKeyCurator
            .findByKeyNames(owner.getKey(), Set.of("test-key1"));

        assertTrue(foundKeys.isEmpty());
    }

    @Test
    void keysFound() {
        activationKeyCurator.create(new ActivationKey("test-key1", owner));
        activationKeyCurator.create(new ActivationKey("test-key2", owner));

        List<ActivationKey> foundKeys = activationKeyCurator
            .findByKeyNames(owner.getKey(), Set.of("test-key1"));

        assertEquals(1, foundKeys.size());
        assertTrue(foundKeys.stream().map(ActivationKey::getName).allMatch("test-key1"::equals));
    }

}
