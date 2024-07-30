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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;

import java.util.stream.Stream;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.From;



public class ActivationKeyPermissionTest {

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
        assertThrows(IllegalArgumentException.class,
            () -> new OwnerActivationKeyPermission(null, Access.ALL));
    }

    @Test
    public void testRequiresOwnerWithID() {
        Owner owner = new Owner()
            .setKey("test_org");

        assertThrows(IllegalArgumentException.class,
            () -> new OwnerActivationKeyPermission(owner, Access.ALL));
    }

    @Test
    public void testRequiresAccess() {
        Owner owner = this.createOwner("test_org");

        assertThrows(IllegalArgumentException.class,
            () -> new OwnerActivationKeyPermission(owner, null));
    }

    @Test
    public void testTargetType() {
        Owner owner = this.createOwner("test_org");
        ActivationKeyPermission perm = new ActivationKeyPermission(owner, Access.ALL);

        // This should always be an ActivationKey class instance
        assertEquals(ActivationKey.class, perm.getTargetType());
    }

    @Test
    public void testGetOwner() {
        Owner owner = this.createOwner("test_org");
        ActivationKeyPermission perm = new ActivationKeyPermission(owner, Access.ALL);

        // This should always be an Owner class instance
        assertEquals(owner, perm.getOwner());
    }

    @Test
    public void testCriteriaRestrictionsAreDisabled() {
        Owner owner = this.createOwner("test_org");
        ActivationKeyPermission perm = new ActivationKeyPermission(owner, Access.ALL);

        // These should always return null
        assertNull(perm.getQueryRestriction(ActivationKey.class, mock(CriteriaBuilder.class),
            mock(From.class)));
    }

    @Test
    public void testRequiresMatchingOwner() {
        Owner owner1 = this.createOwner("test_org-1");
        Owner owner2 = this.createOwner("test_org-2");
        ActivationKey key1 = this.createActivationKey(owner1);
        ActivationKey key2 = this.createActivationKey(owner2);

        ActivationKeyPermission perm = new ActivationKeyPermission(owner1, Access.ALL);

        boolean result1 = perm.canAccessTarget(key2, null, Access.NONE);
        assertFalse(result1);

        boolean result2 = perm.canAccessTarget(key1, null, Access.NONE);
        assertTrue(result2);
    }

    @ParameterizedTest
    @NullSource
    @EnumSource(SubResource.class)
    public void testIgnoresSubResource(SubResource subresource) {
        Owner owner = this.createOwner("test_org");
        ActivationKey key = this.createActivationKey(owner);

        ActivationKeyPermission perm = new ActivationKeyPermission(owner, Access.ALL);

        boolean result = perm.canAccessTarget(key, subresource, Access.NONE);
        assertTrue(result);
    }

    public static Stream<Arguments> accessVerificationDataSource() {
        return Stream.of(
            Arguments.of(Access.ALL, Access.NONE, true),
            Arguments.of(Access.ALL, Access.READ_ONLY, true),
            Arguments.of(Access.ALL, Access.CREATE, true),
            Arguments.of(Access.ALL, Access.ALL, true),
            Arguments.of(Access.CREATE, Access.NONE, true),
            Arguments.of(Access.CREATE, Access.READ_ONLY, true),
            Arguments.of(Access.CREATE, Access.CREATE, true),
            Arguments.of(Access.CREATE, Access.ALL, false),
            Arguments.of(Access.READ_ONLY, Access.NONE, true),
            Arguments.of(Access.READ_ONLY, Access.READ_ONLY, true),
            Arguments.of(Access.READ_ONLY, Access.CREATE, false),
            Arguments.of(Access.READ_ONLY, Access.ALL, false),
            Arguments.of(Access.NONE, Access.NONE, true),
            Arguments.of(Access.NONE, Access.READ_ONLY, false),
            Arguments.of(Access.NONE, Access.CREATE, false),
            Arguments.of(Access.NONE, Access.ALL, false));
    }

    @ParameterizedTest
    @MethodSource("accessVerificationDataSource")
    public void testVerifiesRequiredAccess(Access provided, Access required, boolean granted) {
        Owner owner = this.createOwner("test_org");
        ActivationKey key = this.createActivationKey(owner);

        ActivationKeyPermission perm = new ActivationKeyPermission(owner, provided);

        boolean result = perm.canAccessTarget(key, null, required);
        assertEquals(granted, result);
    }

}
