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
package org.candlepin.auth.permissions;

import org.candlepin.auth.Access;
import org.candlepin.auth.PrincipalData;
import org.candlepin.auth.SubResource;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.Owner;

import org.hibernate.criterion.Criterion;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.From;
import javax.persistence.criteria.Predicate;



/**
 * Determines users and consumers specific access to AsyncJobStatus.
 *
 * Users can view all jobs in their org, but only cancel jobs they started.
 * Consumers can view and cancel jobs they started, but only those.
 */
public class AsyncJobStatusPermission extends TypedPermission<AsyncJobStatus> {

    private final String principalName;
    private final String principalType;
    private final Set<String> ownerIds;

    public AsyncJobStatusPermission(PrincipalData principalData, Collection<String> ownerIds) {
        Objects.requireNonNull(principalData);

        this.principalName = principalData.getName();
        this.principalType = principalData.getType();

        this.ownerIds = new HashSet<>();
        if (ownerIds != null) {
            this.ownerIds.addAll(ownerIds);
        }
    }

    @Override
    public Class<AsyncJobStatus> getTargetType() {
        return AsyncJobStatus.class;
    }

    @Override
    public Owner getOwner() {
        // This permission is not specific to any owner.
        return null;
    }

    @Override
    public Criterion getCriteriaRestrictions(Class entityClass) {
        // This behavior is deprecated. Always return null.
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> Predicate getQueryRestriction(Class<T> entityClass, CriteriaBuilder builder, From<?, T> path) {
        // This behavior is deprecated. Always return null.
        return null;
    }

    @Override
    public boolean canAccessTarget(AsyncJobStatus target, SubResource subResource, Access required) {
        String principal = target.getPrincipalName();
        boolean principalMatch = principal != null && principal.equals(this.principalName);

        // If the job was created within the context of an org, access is only granted if
        // the principal owning this permission has access to that org
        String contextOwnerId = target.getContextOwnerId();
        if (contextOwnerId != null && !this.ownerIds.contains(contextOwnerId)) {
            return false;
        }

        if ("user".equalsIgnoreCase(principalType)) {
            // a user can only cancel the job if it started it
            Access allowed = principalMatch ? Access.ALL : Access.READ_ONLY;
            return allowed.provides(required);
        }
        else if ("consumer".equalsIgnoreCase(principalType)) {
            // if this is a consumer, it must match the principle name access
            return principalMatch && Access.ALL.provides(required);
        }

        return false;
    }

}
