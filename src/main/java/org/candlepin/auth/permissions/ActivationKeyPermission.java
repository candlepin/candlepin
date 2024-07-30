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
import org.candlepin.model.activationkeys.ActivationKey;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.From;
import javax.persistence.criteria.Predicate;



/**
 * Grants permissions to manage activation keys under a given organization.
 */
public class ActivationKeyPermission extends TypedPermission<ActivationKey> {

    private final Owner owner;
    private final String ownerId;

    /**
     * Creates a new permission which provides potential access to API endpoints which verify on the
     * activation key. Note that this permission itself does not provide access to endpoints which
     * verify on the owner with a subresource of activation key.
     *
     * @param owner
     *  the owner/org for which activation key management permissions should be granted
     *
     * @param access
     *  the access granted by this permission
     *
     * @throws IllegalArgumentException
     *  if the provided owner is null or lacks an owner ID, or the provided access is null
     */
    public ActivationKeyPermission(Owner owner, Access access) {
        if (owner == null || owner.getId() == null) {
            throw new IllegalArgumentException("owner is null or lacks an ID");
        }

        if (access == null) {
            throw new IllegalArgumentException("access is null");
        }

        this.owner = owner;
        this.ownerId = owner.getId();

        this.access = access;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<ActivationKey> getTargetType() {
        return ActivationKey.class;
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
    public boolean canAccessTarget(ActivationKey target, SubResource subresource, Access required) {
        return target != null && this.ownerId.equals(target.getOwnerId()) &&
            this.access.provides(required);
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
