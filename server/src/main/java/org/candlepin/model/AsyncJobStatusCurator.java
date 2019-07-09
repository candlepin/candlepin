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

import org.candlepin.model.AsyncJobStatus.JobState;

import com.google.inject.persist.Transactional;

import org.hibernate.type.IntegerType;
import org.hibernate.type.TimestampType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Singleton;



/**
 * AsyncJobStatusCurator
 */
@Singleton
public class AsyncJobStatusCurator extends AbstractHibernateCurator<AsyncJobStatus> {

    public AsyncJobStatusCurator() {
        super(AsyncJobStatus.class);
    }

    /**
     * Fetches a collection of jobs in the given states. If no jobs can be found in the states
     * specified, this method returns an empty collection.
     *
     * @param states
     *  a collection of states to use for filtering jobs
     *
     * @return
     *  a collection of jobs in the provided states
     */
    public List<AsyncJobStatus> getJobsInState(Collection<AsyncJobStatus.JobState> states) {
        if (states != null && !states.isEmpty()) {
            String jpql = "SELECT aj FROM AsyncJobStatus aj WHERE aj.state IN (:states)";

            return this.getEntityManager()
                .createQuery(jpql, AsyncJobStatus.class)
                .setParameter("states", states)
                .getResultList();
        }

        return new ArrayList<>();
    }

    /**
     * Fetches a collection of jobs in the given states. If no jobs can be found in the states
     * specified, this method returns an empty collection.
     *
     * @param states
     *  an array of states to use for filtering jobs
     *
     * @return
     *  a collection of jobs in the provided states
     */
    public List<AsyncJobStatus> getJobsInState(AsyncJobStatus.JobState... states) {
        return states != null ? this.getJobsInState(Arrays.asList(states)) : new ArrayList();
    }

    /**
     * Fetches a collection of jobs currently in non-terminal states
     *
     * @return
     *  a collection of jobs in non-terminal states
     */
    public List<AsyncJobStatus> getNonTerminalJobs() {
        Collection<JobState> states = Arrays.stream(JobState.values())
            .filter(s -> !s.isTerminal())
            .collect(Collectors.toSet());

        return this.getJobsInState(states);
    }

    @Transactional
    public int cleanupAllOldJobs(Date deadline) {
        return this.currentSession().createQuery(
            "delete from AsyncJobStatus where updated <= :date")
            .setParameter("date", deadline, TimestampType.INSTANCE)
            .executeUpdate();
    }

    @Transactional
    public int cleanUpOldCompletedJobs(Date deadLineDt) {
        return this.currentSession().createQuery(
            "delete from AsyncJobStatus where updated <= :date and " +
                "(state = :completed or state = :canceled)")
            .setParameter("date", deadLineDt, TimestampType.INSTANCE)
            .setParameter("completed", JobState.COMPLETED.ordinal(), IntegerType.INSTANCE)
            .setParameter("canceled", JobState.CANCELED.ordinal(), IntegerType.INSTANCE)
            .executeUpdate();
    }

}
