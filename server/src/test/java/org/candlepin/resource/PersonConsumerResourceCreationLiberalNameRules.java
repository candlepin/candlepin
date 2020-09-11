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
package org.candlepin.resource;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import org.candlepin.auth.Access;
import org.candlepin.auth.permissions.PermissionFactory.PermissionType;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Owner;
import org.candlepin.model.PermissionBlueprint;
import org.candlepin.model.Role;
import org.candlepin.model.User;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;


/**
 * PersonConsumerResourceCreationLiberalNameRules
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class PersonConsumerResourceCreationLiberalNameRules extends
    ConsumerResourceCreationLiberalNameRules {

    @Override
    public ConsumerType initConsumerType() {
        ConsumerType systemtype = new ConsumerType(ConsumerType.ConsumerTypeEnum.PERSON);

        // create an owner, an ownerperm, and roles for the user we provide
        // as coming from userService
        owner = new Owner("test_owner");
        PermissionBlueprint p = new PermissionBlueprint(PermissionType.OWNER, owner,
            Access.ALL);
        User user = new User("anyuser", "");
        role = new Role();
        role.addPermission(p);
        role.addUser(user);
        when(userService.findByLogin("anyuser")).thenReturn(user);

        return systemtype;

    }

    @Test
    public void registerWithKeys() {
        // we expect a BadRequestException here
        assertThrows(BadRequestException.class, super::registerWithKeys);
    }

    @Test
    public void passIfOnlyOneActivationKeyDoesNotExistForOrg() {
        // person consumer needs to by-pass this test.
    }
}
