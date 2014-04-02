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
package org.candlepin.model;

import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.pinsetter.core.PinsetterKernel;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.candlepin.pinsetter.core.model.JobStatus.JobState;
import org.candlepin.pinsetter.core.model.JobStatus.TargetType;
import org.candlepin.pinsetter.tasks.KingpinJob;
import org.hibernate.Query;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projections;
import org.hibernate.Criteria;
import org.hibernate.criterion.Restrictions;

import com.google.inject.Inject;

import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 *
 */
public class JobCurator extends AbstractHibernateCurator<JobStatus> {

    private Config config;

    @Inject
    public JobCurator(Config config) {
        super(JobStatus.class);
        this.config = config;
    }

    public JobStatus cancel(String jobId) {
        this.cancelNoReturn(jobId);
        JobStatus result = this.find(jobId);
        if (result != null) {
            this.refresh(result);
        }
        return result;
    }

    public void cancelNoReturn(String jobId) {
        String hql = "update JobStatus j " +
            "set j.state = :canceled " +
            "where j.id = :jobid";
        Query query = this.currentSession().createQuery(hql)
            .setParameter("jobid", jobId)
            .setInteger("canceled", JobState.CANCELED.ordinal());
        int updated = query.executeUpdate();
        if (updated == 0) {
            throw new NotFoundException("job not found");
        }
    }

    public int cleanupAllOldJobs(Date deadline) {
        return this.currentSession().createQuery(
            "delete from JobStatus where updated <= :date")
               .setTimestamp("date", deadline)
               .executeUpdate();
    }

    public int cleanUpOldCompletedJobs(Date deadLineDt) {
        return this.currentSession().createQuery(
            "delete from JobStatus where updated <= :date and " +
            "(state = :completed or state = :canceled)")
               .setTimestamp("date", deadLineDt)
               .setInteger("completed", JobState.FINISHED.ordinal())
               .setInteger("canceled", JobState.CANCELED.ordinal())
               .executeUpdate();
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

    /**
     * This implementation allows us to avoid looping through all canceled jobs.
     * Finds all jobs marked as CANCELED which have an ID in the input list
     * so we can remove the scheduled job.
     *
     * @param activeJobs Names of jobs that are currently active
     * @return JobStatus list to have quartz job canceled
     */
    @SuppressWarnings("unchecked")
    public List<JobStatus> findCanceledJobs(Set<String> activeJobs) {
        if (activeJobs.isEmpty()) {
            //query will fail with an empty list
            return new LinkedList<JobStatus>();
        }
        Criteria c = this.currentSession().createCriteria(JobStatus.class)
            .add(Restrictions.eq("state", JobState.CANCELED))
            .add(Restrictions.in("id", activeJobs));
        return c.list();
    }

    @SuppressWarnings("unchecked")
    public List<JobStatus> findWaitingJobs() {
        // Perhaps unique jobClass/target combinations, However
        // we're already in a weird state if that makes a difference
        return this.currentSession().createCriteria(JobStatus.class)
        .add(Restrictions.eq("state", JobState.WAITING)).list();
    }

    public long findNumRunningByOwnerAndClass(
            String ownerKey, Class<? extends KingpinJob> jobClass) {
        return (Long) this.currentSession().createCriteria(JobStatus.class)
            .add(Restrictions.ge("updated", getBlockingCutoff()))
            .add(Restrictions.eq("state", JobState.RUNNING))
            .add(Restrictions.eq("targetId", ownerKey))
            .add(Restrictions.eq("jobClass", jobClass))
            .setProjection(Projections.count("id"))
            .uniqueResult();
    }

    public JobStatus getByClassAndOwner(
            String ownerKey, Class<? extends KingpinJob> jobClass) {

        return (JobStatus) this.currentSession().createCriteria(JobStatus.class)
            .addOrder(Order.desc("created"))
            .add(Restrictions.ge("updated", getBlockingCutoff()))
            .add(Restrictions.ne("state", JobState.FINISHED))
            .add(Restrictions.ne("state", JobState.FAILED))
            .add(Restrictions.ne("state", JobState.CANCELED))
            .add(Restrictions.eq("targetId", ownerKey))
            .add(Restrictions.eq("jobClass", jobClass))
            .setMaxResults(1)
            .uniqueResult();
    }

    /*
     * Cancel jobs that should have a quartz job (but don't),
     * and have not been updated within the last 2 minutes.
     */
    public int cancelOrphanedJobs(List<String> activeIds) {
        return cancelOrphanedJobs(activeIds, 1000L * 60L * 2L); //2 minutes
    }

    public int cancelOrphanedJobs(List<String> activeIds, Long millis) {
        Date before = new Date(new Date().getTime() - millis);
        String hql = "update JobStatus j " +
            "set j.state = :canceled " +
            "where j.jobGroup = :async and " +
            "j.state != :canceled and " +
            "j.state != :finished and " +
            "j.state != :failed and " +
            "j.updated <= :date";
        // Must trim out activeIds if the list is empty, otherwise the
        // statement will fail.
        if (!activeIds.isEmpty()) {
            hql += " and j.id not in (:activeIds)";
        }
        Query query = this.currentSession().createQuery(hql)
            .setTimestamp("date", before)
            .setParameter("async", PinsetterKernel.SINGLE_JOB_GROUP)
            .setInteger("finished", JobState.FINISHED.ordinal())
            .setInteger("failed", JobState.FAILED.ordinal())
            .setInteger("canceled", JobState.CANCELED.ordinal());
        if (!activeIds.isEmpty()) {
            query.setParameterList("activeIds", activeIds);
        }
        return query.executeUpdate();
    }

    private Date getBlockingCutoff() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.SECOND, -1 * config.getInt(
            ConfigProperties.PINSETTER_ASYNC_JOB_TIMEOUT));
        return calendar.getTime();
    }
}
