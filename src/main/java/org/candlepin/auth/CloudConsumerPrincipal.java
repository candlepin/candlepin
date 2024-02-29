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
package org.candlepin.auth;

import org.candlepin.model.Owner;

import java.util.Objects;


public class CloudConsumerPrincipal extends Principal {

    private final Owner owner;

    public CloudConsumerPrincipal(Owner owner) {
        this.owner = Objects.requireNonNull(owner);
    }

    @Override
    public String getType() {
        return "cloudconsumer";
    }

    @Override
    public boolean hasFullAccess() {
        return false;
    }

    @Override
    public String getName() {
        return this.owner.getKey();
    }

    public String getOwnerKey() {
        return this.owner.getKey();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj instanceof CloudConsumerPrincipal other) {
            return this.owner.equals(other.owner);
        }

        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.owner);
    }

    @Override
    public AuthenticationMethod getAuthenticationMethod() {
        return AuthenticationMethod.CLOUD;
    }

}
