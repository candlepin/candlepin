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
import org.candlepin.model.Owner;

import org.hibernate.criterion.Criterion;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.From;
import javax.persistence.criteria.Predicate;



/**
 * Grants permission to create activation keys under the specified org.
 *
 * Note: this permission's existence is a function of the way the API verifies on the org during
 * key creation. The creation API is scoped by org, but the management APIs are not, which means
 * creation must be verified as a subresource of Owner, where management is a verify on the keys
 * directly.
 */
public class ActivationKeyCreationPermission extends TypedPermission<Owner> {

    private final Owner owner;
    private final String ownerId;

    /**
     * Creates a new activation key management permission providing write access to create or modify
     * activation keys within the specified owner (organization).
     *
     * @param owner
     *  the owner/org for which activation key creation permissions should be granted
     *
     * @throws IllegalArgumentException
     *  if the provided owner is null or lacks an owner ID
     */
    public ActivationKeyCreationPermission(Owner owner) {
        if (owner == null || owner.getId() == null) {
            throw new IllegalArgumentException("owner is null or lacks an ID");
        }

        this.owner = owner;
        this.ownerId = owner.getId();

        // TODO: FIXME: this should be set by calling a constructor in our superclass rather than
        // explicitly setting the field directly.
        this.access = Access.CREATE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<Owner> getTargetType() {
        return Owner.class;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Owner getOwner() {
        return this.owner;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canAccessTarget(Owner target, SubResource subresource, Access required) {
        return target != null && this.ownerId.equals(target.getId()) &&
            subresource == SubResource.ACTIVATION_KEYS &&
            this.access.provides(required);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Criterion getCriteriaRestrictions(Class entityClass) {
        // deprecated functionality; never return anything from this, as dynamically modifying
        // arbitrary queries is error prone and a maintenance nightmare.
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> Predicate getQueryRestriction(Class<T> entityClass, CriteriaBuilder builder, From<?, T> path) {
        // deprecated functionality; never return anything from this, as dynamically modifying
        // arbitrary queries is error prone and a maintenance nightmare.
        return null;
    }

}
