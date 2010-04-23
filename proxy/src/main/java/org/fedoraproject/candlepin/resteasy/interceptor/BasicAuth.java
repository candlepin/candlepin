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
package org.fedoraproject.candlepin.resteasy.interceptor;

import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.auth.Role;
import org.fedoraproject.candlepin.auth.UserPrincipal;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.OwnerCurator;
import org.fedoraproject.candlepin.service.UserServiceAdapter;
import org.fedoraproject.candlepin.service.UserServiceAdapter.OwnerInfo;
import org.jboss.resteasy.spi.HttpRequest;

import com.google.inject.Inject;

/**
 * BasicAuth
 */
class BasicAuth {
    
    private UserServiceAdapter userServiceAdapter;
    private OwnerCurator ownerCurator;
    
    @Inject
    BasicAuth(UserServiceAdapter userServiceAdapter,
            OwnerCurator ownerCurator) {
        this.userServiceAdapter = userServiceAdapter;
        this.ownerCurator = ownerCurator;
    }

    Principal getPrincipal(HttpRequest request) throws Exception {

        List<String> header = request.getHttpHeaders().getRequestHeader("Authorization");
        String auth = null;
        if (null != header && header.size() > 0) {
            auth = header.get(0);
        }

        if (auth != null && auth.toUpperCase().startsWith("BASIC ")) {
            String userpassEncoded = auth.substring(6);
            String[] userpass = new String(Base64.decodeBase64(userpassEncoded))
                    .split(":");

            String username = userpass[0];
            String password = userpass[1];

            if (userServiceAdapter.validateUser(username, password)) {
                return createPrincipal(username);
            }
        }

        return null;
    }

    private Principal createPrincipal(String username) {
        OwnerInfo ownerInfo = this.userServiceAdapter.getOwnerInfo(username);
        Owner owner = getOwnerForInfo(ownerInfo);
        List<Role> roles = this.userServiceAdapter.getRoles(username);

        return new UserPrincipal(username, owner, roles);
    }

    private Owner getOwnerForInfo(OwnerInfo info) {
        Owner owner = this.ownerCurator.lookupByKey(info.getKey());

        // create if not present
        if (owner == null) {
            owner = new Owner(info.getKey(), info.getName());
            this.ownerCurator.create(owner);
        }

        return owner;
    }
}
