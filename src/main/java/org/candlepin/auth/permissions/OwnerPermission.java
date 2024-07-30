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
package org.candlepin.auth.permissions;

import org.candlepin.auth.Access;
import org.candlepin.auth.SubResource;
import org.candlepin.model.Consumer;
import org.candlepin.model.Consumer_;
import org.candlepin.model.Environment;
import org.candlepin.model.Environment_;
import org.candlepin.model.Owned;
import org.candlepin.model.Owner;
import org.candlepin.model.Owner_;
import org.candlepin.model.Pool;
import org.candlepin.model.Pool_;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKey_;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.From;
import javax.persistence.criteria.Predicate;



/**
 * A permission represents an owner to be accessed in some fashion, and a verb which
 * the permission is granting.
 */
public class OwnerPermission implements Permission, Serializable {
    private static final long serialVersionUID = -8906113952952371238L;

    private final Owner owner;

    private final Access access;

    public OwnerPermission(Owner owner, Access access) {
        this.owner = owner;
        this.access = access;
    }

    @Override
    public boolean canAccess(Object target, SubResource subResource, Access requiredAccess) {
        if (target instanceof Owned) {
            // First make sure the owner matches:
            if (Objects.equals(owner.getId(), ((Owned) target).getOwnerId()) &&
                access.provides(requiredAccess)) {

                return true;
            }
        }

        // If asked to verify access to an object that does not implement Owned,
        // as far as this permission goes, we probably have to deny access.
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> Predicate getQueryRestriction(Class<T> entityClass, CriteriaBuilder builder, From<?, T> path) {
        if (Owner.class.equals(entityClass)) {
            return builder.equal(((From<?, Owner>) path).get(Owner_.key), this.getOwner().getKey());
        }
        else if (Consumer.class.equals(entityClass)) {
            return builder.equal(((From<?, Consumer>) path).get(Consumer_.ownerId), this.getOwner().getId());
        }
        else if (Pool.class.equals(entityClass)) {
            return builder.equal(((From<?, Pool>) path).get(Pool_.owner), this.getOwner());
        }
        else if (ActivationKey.class.equals(entityClass)) {
            return builder.equal(((From<?, ActivationKey>) path).get(ActivationKey_.owner), this.getOwner());
        }
        else if (Environment.class.equals(entityClass)) {
            return builder.equal(((From<?, Environment>) path).get(Environment_.owner), this.getOwner());
        }

        return null;
    }

    @Override
    public Owner getOwner() {
        return owner;
    }

    public Access getAccess() {
        return access;
    }

}
