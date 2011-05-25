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

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import org.fedoraproject.candlepin.auth.Access;
import org.fedoraproject.candlepin.auth.permissions.Permission;
import org.hibernate.annotations.GenericGenerator;

/**
 * A permission represents an owner to be accessed in some fashion, and a verb which
 * the permissions is granting.
 */
@Entity
@Table(name = "cp_owner_permission",
    uniqueConstraints = { @UniqueConstraint(columnNames = {"owner", "verb"}) })
public class OwnerPermission extends AbstractHibernateObject implements Permission {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    private String id;

    private Owner owner;
    
    private Access verb;

    public OwnerPermission(Owner owner, Access roles) {
        this.owner = owner;
        this.verb = roles;
    }

    protected OwnerPermission() {
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

    public Access getVerb() {
        return verb;
    }

    public void setVerb(Access verb) {
        this.verb = verb;
    }

    @Override
    public boolean canAccess(Object target, Access access) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
