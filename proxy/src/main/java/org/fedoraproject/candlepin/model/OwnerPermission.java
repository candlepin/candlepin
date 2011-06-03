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
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import org.fedoraproject.candlepin.audit.Event;

import org.fedoraproject.candlepin.auth.Access;
import org.fedoraproject.candlepin.auth.permissions.Permission;
import org.hibernate.annotations.ForeignKey;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Index;

/**
 * A permission represents an owner to be accessed in some fashion, and a verb which
 * the permissions is granting.
 */
@Entity
@Table(name = "cp_owner_permission")
//    uniqueConstraints = { @UniqueConstraint(columnNames = {"owner_id", "access"}) })
public class OwnerPermission extends AbstractHibernateObject implements Permission {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    private String id;

    @ManyToOne
    @ForeignKey(name = "fk_permission_owner")
    @JoinColumn(nullable = false)
    @Index(name = "cp_permission_owner_fk_idx")
    private Owner owner;
    
    private Access access;

    public OwnerPermission(Owner owner, Access access) {
        this.owner = owner;
        this.access = access;
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

    public Access getAccess() {
        return access;
    }

    public void setAccess(Access access) {
        this.access = access;
    }

    @Override
    public boolean canAccess(Object target, Access access) {
        if (target instanceof Owned) {
            // TODO:  Just check the key here?
            return this.owner.equals(((Owned) target).getOwner());
        }
        // special case for events
        else if (target instanceof Event) {
            return this.owner.getId().equals(((Event) target).getOwnerId());
        }

        return false;
    }
}
