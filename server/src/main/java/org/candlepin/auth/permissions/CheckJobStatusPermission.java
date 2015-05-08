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
 * Allows users/consumers access to any JobStatus from jobs that they
 * created/initiated.
 *
 */
public class CheckJobStatusPermission extends TypedPermission<JobStatus> {

    private String principalName;
    private String principalType;
    private List<String> allowedOrgKeys;

    public CheckJobStatusPermission(PrincipalData principalData, List<String> allowedOrgKeys) {
        this.principalName = principalData.getName();
        this.principalType = principalData.getType();
        this.allowedOrgKeys = allowedOrgKeys;
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
    public Criterion getCriteriaRestrictions(Class entityClass) {
        if (!entityClass.equals(JobStatus.class)) {
            return null;
        }

        Conjunction conjunction = Restrictions.conjunction();
        // Org has to match.
        conjunction.add(Restrictions.in("ownerId", allowedOrgKeys));

        conjunction.add(Restrictions.or(
            Restrictions.ne("targetType", JobStatus.TargetType.OWNER),
            Restrictions.and(
                Restrictions.eq("targetType", JobStatus.TargetType.OWNER),
                Restrictions.eqProperty("ownerId", "targetId")
            )
        ));

        // If the principal is not a user, make sure to enforce a principalName match.
        if (!"user".equalsIgnoreCase(principalType)) {
            conjunction.add(Restrictions.eq("principalName", principalName));
        }
        return conjunction;
    }

    @Override
    public boolean canAccessTarget(JobStatus target, SubResource subResource, Access required) {
        boolean requiredAccessMet = Access.READ_ONLY.provides(required);
        boolean principalNameMatch = principalName != null && principalName.equals(target.getPrincipalName());

        String ownerId = target.getOwnerId();
        boolean ownerOk = ownerId != null && allowedOrgKeys.contains(ownerId);

        if (!ownerOk) {
            return false;
        }

        // Make sure that the owner matches that of the target.
        if (JobStatus.TargetType.OWNER.name().equalsIgnoreCase(target.getTargetType()) &&
            !ownerId.equals(target.getTargetId())) {
            return false;
        }

        if ("user".equalsIgnoreCase(principalType)) {
            // Org was OK, but only allow a user to view consumer's status.
            return true;
        }

        return requiredAccessMet && principalNameMatch;
    }

}
