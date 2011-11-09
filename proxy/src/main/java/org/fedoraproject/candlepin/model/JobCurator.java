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

import org.fedoraproject.candlepin.exceptions.NotFoundException;
import org.fedoraproject.candlepin.pinsetter.core.model.JobStatus;
import org.fedoraproject.candlepin.pinsetter.core.model.JobStatus.JobState;
import org.fedoraproject.candlepin.pinsetter.core.model.JobStatus.TargetType;

import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;

import java.util.Date;
import java.util.List;

/**
 *
 */
public class JobCurator extends AbstractHibernateCurator<JobStatus> {

    public JobCurator() {
        super(JobStatus.class);
    }

    public JobStatus cancel(String jobId) {
        JobStatus j = this.find(jobId);
        if (j == null) {
            throw new NotFoundException("job not found");
        }
        j.setState(JobState.CANCELLED);
        merge(j);
        return j;
    }

    public int cleanupFailedJobs(Date deadline) {
        return this.currentSession().createQuery(
            "delete from JobStatus where startTime <= :date and " +
            "state = :failed")
               .setDate("date", deadline)
               .setInteger("failed", JobState.FAILED.ordinal())
               .executeUpdate();
    }

    public int cleanUpOldJobs(Date deadLineDt) {
        return this.currentSession().createQuery(
            "delete from JobStatus where finishTime <= :date and " +
            "(state = :completed or state = :cancelled)")
               .setDate("date", deadLineDt)
               .setInteger("completed", JobState.FINISHED.ordinal())
               .setInteger("cancelled", JobState.CANCELLED.ordinal())
               .executeUpdate();
    }

    @SuppressWarnings("unchecked")
    public List<JobStatus> findActiveByOwnerKeyAndName(
        String ownerKey, String name) {

        JobState[] states = new JobState[3];
        states[0] = JobState.CREATED;
        states[1] = JobState.PENDING;
        states[2] = JobState.RUNNING;

        return currentSession().createCriteria(JobStatus.class)
            .add(Restrictions.eq("targetId", ownerKey))
            .add(Restrictions.eq("targetType", TargetType.OWNER))
            .add(Restrictions.in("state", states))
            .add(Restrictions.ilike("id", "%" + name + "%")).list();
    }

    @SuppressWarnings("unchecked")
    public List<JobStatus> findPendingByOwnerKeyAndName(
        String ownerKey, String name) {

        JobState[] states = new JobState[2];
        states[0] = JobState.CREATED;
        states[1] = JobState.PENDING;

        return currentSession().createCriteria(JobStatus.class)
            .addOrder(Order.asc("created"))
            .add(Restrictions.eq("targetId", ownerKey))
            .add(Restrictions.eq("targetType", TargetType.OWNER))
            .add(Restrictions.in("state", states))
            .add(Restrictions.ilike("id", "%" + name + "%")).list();
    }

    public List<JobStatus> findByOwnerKey(String ownerKey) {
        return findByTarget(JobStatus.TargetType.OWNER, ownerKey);
    }

    public List<JobStatus> findByConsumerUuid(String uuid) {
        return findByTarget(JobStatus.TargetType.CONSUMER, uuid);
    }

    @SuppressWarnings("unchecked")
    public List<JobStatus> findByPrincipalName(String principalName) {
        return this.currentSession().createCriteria(JobStatus.class)
        .add(Restrictions.eq("principalName", principalName)).list();
    }

    @SuppressWarnings("unchecked")
    private List<JobStatus> findByTarget(TargetType type, String tgtid) {
        return currentSession().createCriteria(JobStatus.class)
            .add(Restrictions.eq("targetId", tgtid))
            .add(Restrictions.eq("targetType", type)).list();
    }

    @SuppressWarnings("unchecked")
    public List<JobStatus> findCanceledJobs() {
        return this.currentSession().createCriteria(JobStatus.class)
        .add(Restrictions.eq("state", JobState.CANCELLED)).list();
    }
}
