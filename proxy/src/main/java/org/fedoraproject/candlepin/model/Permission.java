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
package org.fedoraproject.candlepin.model;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

import org.fedoraproject.candlepin.auth.Role;
import org.hibernate.annotations.GenericGenerator;

/**
 * A permission represents an owner to be accessed in some fashion, and a verb which
 * the permissions is granting.
 */
public class Permission extends AbstractHibernateObject {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    private String id;

    private Owner owner;
    private Set<Role> roles = new HashSet<Role>();
    private NewRole membershipGroup;

    public Permission(Owner owner, NewRole membershipGroup, Set<Role> roles) {
        this.owner = owner;
        this.roles = roles;
        this.membershipGroup = membershipGroup;
    }

    private Permission() {
        // JPA
    }

    public Owner getOwner() {
        return owner;
    }

    public void setOwner(Owner owner) {
        this.owner = owner;
    }

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = roles;
    }

    public NewRole getMembershipGroup() {
        return membershipGroup;
    }

    public void setMembershipGroup(NewRole membershipGroup) {
        this.membershipGroup = membershipGroup;
    }
}
