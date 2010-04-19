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
package org.fedoraproject.candlepin.auth;

import java.util.List;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Owner;

/**
 * An entity interacting with Candlepin
 */
public abstract class Principal {

    private Owner owner;
    private List<Role> roles;

    public Principal(Owner owner, List<Role> roles) {
        this.owner = owner;
        this.roles = roles;
    }

    public Owner getOwner() {
        return owner;
    }

    public List<Role> getRoles() {
        return roles;
    }

    public abstract boolean canAccessConsumer(Consumer consumer);
}
