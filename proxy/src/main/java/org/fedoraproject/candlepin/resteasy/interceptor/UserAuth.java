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

import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.auth.Role;
import org.fedoraproject.candlepin.auth.UserPrincipal;
import org.fedoraproject.candlepin.exceptions.BadRequestException;
import org.fedoraproject.candlepin.exceptions.NotFoundException;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.OwnerCurator;
import org.fedoraproject.candlepin.service.UserServiceAdapter;
import org.jboss.resteasy.spi.HttpRequest;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Injector;

/**
 * UserAuth
 */
public class UserAuth implements AuthProvider {

    protected UserServiceAdapter userServiceAdapter;
    protected OwnerCurator ownerCurator;
    protected Injector injector;
    protected I18n i18n;

    public UserAuth(UserServiceAdapter userServiceAdapter,
        OwnerCurator ownerCurator, Injector injector) {
        this.userServiceAdapter = userServiceAdapter;
        this.ownerCurator = ownerCurator;
        this.injector = injector;
        i18n = this.injector.getInstance(I18n.class);
    }

    public Principal getPrincipal(HttpRequest request) {
        return null;
    }

    /**
     * Retrieve a header, or the empty string if it is not there.
     * 
     * @return the header or a blank string (no nils)
     */
    public String getHeader(HttpRequest request, String name) {
        String headerValue = "";
        List<String> header = null;
        for (String key : request.getHttpHeaders().getRequestHeaders().keySet()) {
            if (key.equalsIgnoreCase(name)) {
                header = request.getHttpHeaders().getRequestHeader(key);
                break;
            }
        }
        if (null != header && header.size() > 0) {
            headerValue = header.get(0);
        }
        return headerValue;
    }

    /**
     * Creates a user principal for a given username
     */
    protected Principal createPrincipal(String username) {
        Owner owner = this.userServiceAdapter.getOwner(username);
        UserPrincipal principal = null;
        if (owner == null) {
            String msg = i18n.tr("No owner found for user {0}", username);
            throw new BadRequestException(msg);
        }
        else {
            owner = lookupOwner(owner);
            List<Role> roles = this.userServiceAdapter.getRoles(username);
            principal = new UserPrincipal(username, owner, roles);
        }
        return principal;
    }

    /**
     * Ensure that an owner exists in the db, and throw an exception if not
     * found.
     */
    protected Owner lookupOwner(Owner owner) {
        Owner o = this.ownerCurator.lookupByKey(owner.getKey());
        if (o == null) {
            if (owner.getKey() == null) {
                throw new NotFoundException(
                    i18n.tr("An owner does not exist for a null org id"));
            }
        }

        return o;
    }

}
