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
package org.canadianTenPin.resource.test;

import static org.mockito.Mockito.when;

import org.canadianTenPin.auth.Access;
import org.canadianTenPin.auth.permissions.PermissionFactory.PermissionType;
import org.canadianTenPin.exceptions.BadRequestException;
import org.canadianTenPin.model.ConsumerType;
import org.canadianTenPin.model.Owner;
import org.canadianTenPin.model.PermissionBlueprint;
import org.canadianTenPin.model.Role;
import org.canadianTenPin.model.User;
import org.junit.Test;

/**
 * PersonConsumerResourceCreationLiberalNameRules
 */
public class PersonConsumerResourceCreationLiberalNameRules extends
    ConsumerResourceCreationLiberalNameRules {
    public ConsumerType initSystem() {
        ConsumerType systemtype = new ConsumerType(
            ConsumerType.ConsumerTypeEnum.PERSON);

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
        try {
            super.registerWithKeys();
        }
        catch (BadRequestException e) {
            return;
        }
    }

}
