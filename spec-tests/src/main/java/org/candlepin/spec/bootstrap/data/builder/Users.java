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
package org.candlepin.spec.bootstrap.data.builder;

import org.candlepin.dto.api.client.v1.UserDTO;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

/**
 * Class meant to provide fully randomized instances of users.
 *
 * Individual tests can then modify the instance according to their needs.
 */
public final class Users {

    private Users() {
        throw new UnsupportedOperationException();
    }

    public static UserDTO random() {
        UserDTO newUser = new UserDTO();
        newUser.setSuperAdmin(false);
        newUser.setUsername(StringUtil.random("username"));
        newUser.setPassword(StringUtil.random("password"));
        return newUser;
    }
}
