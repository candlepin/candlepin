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
package org.candlepin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.candlepin.model.Owner;

import org.junit.jupiter.api.Test;


public class CloudConsumerPrincipalTest {

    @Test
    public void principalShouldFailWithNullOwner() {
        assertThrows(NullPointerException.class, () -> new CloudConsumerPrincipal(null));
    }

    @Test
    public void principalShouldHaveNoPermissions() {
        Owner owner = createOwner();

        CloudConsumerPrincipal principal = new CloudConsumerPrincipal(owner);

        assertThat(principal.permissions)
            .isEmpty();
    }

    @Test
    public void testGetType() {
        Owner owner = createOwner();

        CloudConsumerPrincipal principal = new CloudConsumerPrincipal(owner);

        assertThat(principal)
            .returns("cloudconsumer", Principal::getType);
    }

    @Test
    public void testHasFullAccess() {
        Owner owner = createOwner();

        CloudConsumerPrincipal principal = new CloudConsumerPrincipal(owner);

        assertFalse(principal.hasFullAccess());
    }

    @Test
    public void testGetName() {
        Owner owner = createOwner();

        CloudConsumerPrincipal principal = new CloudConsumerPrincipal(owner);

        assertThat(principal)
            .returns(owner.getKey(), Principal::getName);
    }

    @Test
    public void testEqualsWithSelf() {
        Owner owner = createOwner();

        CloudConsumerPrincipal principal = new CloudConsumerPrincipal(owner);

        assertEquals(principal, principal);
    }

    private Owner createOwner() {
        Owner owner = new Owner()
            .setId("id")
            .setKey("test_key")
            .setDisplayName("test owner");
        return owner;
    }
}
