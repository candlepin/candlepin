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
package org.candlepin.auth;

/**
 * A simple principal implementation for OAuth connections with a user header.
 * In this scenario we have an authenticated principal with a username, and we
 * can assume the calling application has validated the request, so whatever
 * it is we can allow full access.
 *
 * Most commonly used for Katello deployments, which manage their own
 * users/roles/perms.
 */
public class TrustedUserPrincipal extends Principal {

    private String username;

    public TrustedUserPrincipal(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final TrustedUserPrincipal other = (TrustedUserPrincipal) obj;
        if ((this.username == null) ?
            (other.username != null) : !this.username.equals(other.username)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 47 * hash + (this.username != null ? this.username.hashCode() : 0);
        return hash;
    }

    @Override
    public String getType() {
        return "trusteduser";
    }

    @Override
    public String getPrincipalName() {
        return username;
    }

    @Override
    public boolean hasFullAccess() {
        return true;
    }

    @Override
    public boolean canAccess(Object target, SubResource subResource, Access access) {
        return true;
    }

}
