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
import org.candlepin.model.Owner;
import org.candlepin.pinsetter.core.model.JobStatus;

import org.hibernate.criterion.Conjunction;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Restrictions;

import java.util.List;

/**
 * Determines users and consumers specific access to JobStatus.
 *
 * Users can view all jobs in their org, but only cancel jobs they started.
 * Consumers can view and cancel jobs they started, but only those.
 *
 */
public class JobStatusPermission extends TypedPermission<JobStatus> {

    private String principalName;
    private String principalType;
    private List<String> allowedOrgIds;

    public JobStatusPermission(PrincipalData principalData, List<String> allowedOrgIds) {
        this.principalName = principalData.getName();
        this.principalType = principalData.getType();
        this.allowedOrgIds = allowedOrgIds;
    }

    @Override
    public Class<JobStatus> getTargetType() {
        return JobStatus.class;
    }

    @Override
    public Owner getOwner() {
        // This permission is not specific to any owner.
        return null;
    }

    @Override
    @SuppressWarnings("checkstyle:indentation")
    public Criterion getCriteriaRestrictions(Class entityClass) {
        if (!entityClass.equals(JobStatus.class)) {
            return null;
        }

        Conjunction conjunction = Restrictions.conjunction();
        // Org has to match.
        conjunction.add(Restrictions.in("ownerId", allowedOrgIds));

        conjunction.add(Restrictions.or(
            Restrictions.ne("targetType", JobStatus.TargetType.OWNER),
            Restrictions.and(
                Restrictions.eq("targetType", JobStatus.TargetType.OWNER),
                Restrictions.eqProperty("ownerId", "targetId"))
        ));

        // If the principal is not a user, make sure to enforce a principalName match.
        if (!"user".equalsIgnoreCase(principalType)) {
            conjunction.add(Restrictions.eq("principalName", principalName));
        }
        return conjunction;
    }

    @Override
    public boolean canAccessTarget(JobStatus target, SubResource subResource, Access required) {
        boolean principalNameMatch = target.getPrincipalName().equals(principalName);
        String ownerId = target.getOwnerId();
        boolean ownerOk = ownerId != null && allowedOrgIds.contains(ownerId);

        if (!ownerOk) {
            return false;
        }

        // Make sure that the owner matches that of the target.
        if (JobStatus.TargetType.OWNER.name().equalsIgnoreCase(target.getTargetType()) &&
            !ownerId.equals(target.getTargetId())) {
            return false;
        }

        if ("user".equalsIgnoreCase(principalType)) {
            // A user can only cancel the job if it started it
            if (principalNameMatch) {
                return Access.ALL.provides(required);
            }
            else {
                return Access.READ_ONLY.provides(required);
            }
        }
        else if ("consumer".equalsIgnoreCase(principalType)) {
            // if this is a consumer it must match the principle name access
            return Access.ALL.provides(required) && principalNameMatch;
        }

        return false;
    }

}
