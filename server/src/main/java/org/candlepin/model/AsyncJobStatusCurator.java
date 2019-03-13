/**
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.model;

import java.util.Collection;
import java.util.Collections;

import javax.inject.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Predicate;



/**
 * AsyncJobStatusCurator
 */
@Singleton
public class AsyncJobStatusCurator extends AbstractHibernateCurator<AsyncJobStatus> {

    public AsyncJobStatusCurator() {
        super(AsyncJobStatus.class);
    }

    /**
     * Fetches existing jobs matching the provided job name and constraint hash, optionally matching
     * the group and states
     *
     * @param jobName
     *  The name of the jobs to find
     *
     * @param jobGroup
     *  The group in which to search for jobs
     *
     * @param constraintHash
     *  A hash of the job constraints to find
     *
     * @param states
     *  A collection of states to use for filtering jobs
     *
     * @return
     *  A collection of jobs matching the input name, group, constraints and states
     */
    public Collection<AsyncJobStatus> findJobsByConstraints(String jobName, String jobGroup,
        Collection<AsyncJobStatus.JobState> states, String constraintHash) {

        // If we don't have a job name or a constraint hash, there's nothing to find here
        if (jobName == null || jobName.isEmpty() || constraintHash == null || constraintHash.isEmpty()) {
            return Collections.emptyList();
        }

        EntityManager entityManager = this.getEntityManager();
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<AsyncJobStatus> query = builder.createQuery(this.entityType());
        Root<AsyncJobStatus> root = query.from(this.entityType());

        Predicate whereClause = builder.and(
            builder.equal(root.get("name"), jobName),
            builder.equal(root.get("constraintHash"), constraintHash));

        if (jobGroup != null && !jobGroup.isEmpty()) {
            whereClause = builder.and(whereClause, builder.equal(root.get("group"), jobGroup));
        }

        if (states != null && !states.isEmpty()) {
            whereClause = builder.and(whereClause, root.get("state").in(states));
        }

        query.select(root).where(whereClause);
        return entityManager.createQuery(query).getResultList();
    }

}
