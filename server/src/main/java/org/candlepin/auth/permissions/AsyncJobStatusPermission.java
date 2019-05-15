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



/**
 * Determines users and consumers specific access to AsyncJobStatus.
 *
 * Users can view all jobs in their org, but only cancel jobs they started.
 * Consumers can view and cancel jobs they started, but only those.
 */
public class AsyncJobStatusPermission extends TypedPermission<AsyncJobStatus> {

    private String principalName;
    private String principalType;

    public AsyncJobStatusPermission(PrincipalData principalData) {
        this.principalName = principalData.getName();
        this.principalType = principalData.getType();
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

    @Override
    public boolean canAccessTarget(AsyncJobStatus target, SubResource subResource, Access required) {
        String principal = target.getPrincipal();
        boolean principalMatch = principal != null && principal.equals(this.principalName);

        if ("user".equalsIgnoreCase(principalType)) {
            // a user can only cancel the job if it started it
            Access allowed = principalMatch ? Access.ALL : Access.READ_ONLY;
            return allowed.provides(required);
        }
        else if ("consumer".equalsIgnoreCase(principalType)) {
            // if this is a consumer it must match the principle name access
            return principalMatch && Access.ALL.provides(required);
        }

        return false;
    }

}
