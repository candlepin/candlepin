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
package org.fedoraproject.candlepin.servlet.filter.auth;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;
import org.fedoraproject.candlepin.service.UserServiceAdapter;

import com.google.inject.Inject;

/**
 * BasicAuthViaUserServiceFilter
 */
public class BasicAuthViaUserServiceFilter extends AuthenticationFilter {

    private UserServiceAdapter userServiceAdapter;
    
    @Inject
    public BasicAuthViaUserServiceFilter(UserServiceAdapter userServiceAdapter) {
        this.userServiceAdapter = userServiceAdapter;
    }
    
    @Override
    protected String getUserName(HttpServletRequest request, HttpServletResponse response)
            throws Exception {

        String auth = request.getHeader("Authorization");

        if (auth != null && auth.toUpperCase().startsWith("BASIC ")) {
            String userpassEncoded = auth.substring(6);
            String[] userpass = new String(Base64.decodeBase64(userpassEncoded))
                    .split(":");

            String username = userpass[0];
            String password = userpass[1];

            if (doAuth(username, password)) {
                return username;
            }
        }

        return null;
    }

    private boolean doAuth(String username, String password) throws Exception {
        return userServiceAdapter.validateUser(username, password);
    }
}
