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
package org.candlepin.auth.permissions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.candlepin.auth.Access;
import org.candlepin.auth.SubResource;
import org.candlepin.model.Owner;
import org.candlepin.model.activationkeys.ActivationKey;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.From;



public class ActivationKeyManagementPermissionTest {

    private Owner createOwner(String key) {
        return new Owner()
            .setId(key)
            .setKey(key);
    }

    private ActivationKey createActivationKey(Owner owner) {
        return new ActivationKey()
            .setOwner(owner);
    }

    @Test
    public void testRequiresOwnerInstance() {
        assertThrows(IllegalArgumentException.class, () -> new ActivationKeyManagementPermission(null));
    }

    @Test
    public void testRequiresOwnerWithID() {
        Owner owner = new Owner()
            .setKey("test_org");

        assertThrows(IllegalArgumentException.class, () -> new ActivationKeyManagementPermission(owner));
    }

    @Test
    public void testTargetType() {
        Owner owner = this.createOwner("test_org");
        ActivationKeyManagementPermission perm = new ActivationKeyManagementPermission(owner);

        // This should always be an ActivationKey class instance
        assertEquals(ActivationKey.class, perm.getTargetType());
    }

    @Test
    public void testGetOwner() {
        Owner owner = this.createOwner("test_org");
        ActivationKeyManagementPermission perm = new ActivationKeyManagementPermission(owner);

        // This should always be an Owner class instance
        assertEquals(owner, perm.getOwner());
    }

    @Test
    public void testCriteriaRestrictionsAreDisabled() {
        Owner owner = this.createOwner("test_org");
        ActivationKeyManagementPermission perm = new ActivationKeyManagementPermission(owner);

        // These should always return null
        assertNull(perm.getCriteriaRestrictions(ActivationKey.class));
        assertNull(perm.getQueryRestriction(ActivationKey.class, mock(CriteriaBuilder.class),
            mock(From.class)));
    }

    // TODO: This should eventually change to *not* blanket permit all access, but at the time of
    // writing, we don't have much more granularity with our permissions with respect to the
    // operation requirements.
    @ParameterizedTest
    @EnumSource(Access.class)
    public void testProvidesAllLevelAccess(Access required) {
        Owner owner = this.createOwner("test_org");
        ActivationKey key = this.createActivationKey(owner);

        ActivationKeyManagementPermission perm = new ActivationKeyManagementPermission(owner);

        boolean result = perm.canAccessTarget(key, SubResource.ACTIVATION_KEYS, required);
        assertTrue(result);
    }

    @ParameterizedTest
    @NullSource
    @EnumSource(SubResource.class)
    public void testIgnoresSubResource(SubResource subresource) {
        Owner owner = this.createOwner("test_org");
        ActivationKey key = this.createActivationKey(owner);

        ActivationKeyManagementPermission perm = new ActivationKeyManagementPermission(owner);

        boolean result = perm.canAccessTarget(key, subresource, Access.NONE);
        assertTrue(result);
    }

    @Test
    public void testRequiresMatchingOwner() {
        Owner owner1 = this.createOwner("test_org-1");
        Owner owner2 = this.createOwner("test_org-2");
        ActivationKey key1 = this.createActivationKey(owner1);
        ActivationKey key2 = this.createActivationKey(owner2);

        ActivationKeyManagementPermission perm = new ActivationKeyManagementPermission(owner1);

        boolean result1 = perm.canAccessTarget(key2, SubResource.ACTIVATION_KEYS, Access.NONE);
        assertFalse(result1);

        boolean result2 = perm.canAccessTarget(key1, SubResource.ACTIVATION_KEYS, Access.NONE);
        assertTrue(result2);
    }

}
