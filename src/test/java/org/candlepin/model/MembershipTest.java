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

import org.candlepin.test.DatabaseTestFixture;

import org.junit.jupiter.api.Test;



public class MembershipTest extends DatabaseTestFixture {

    @Test
    public void testCreate() throws Exception {
        Owner owner = this.createOwner("testowner");

        String username = "TESTUSER";
        String password = "sekretpassword";
        User user = new User(username, password);
        userCurator.create(user);

        // TODO: Finish up here...
    }

}
