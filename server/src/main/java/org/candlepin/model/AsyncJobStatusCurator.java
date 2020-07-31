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

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaDelete;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;



/**
 * AsyncJobStatusCurator
 */
@Component
public class AsyncJobStatusCurator extends AbstractHibernateCurator<AsyncJobStatus> {

    /**
     * Container object for providing various arguments to the job status lookup method(s).
     */
    public static class AsyncJobStatusQueryBuilder {
        private Collection<String> jobIds;
        private Collection<String> jobKeys;
        private Collection<JobState> jobStates;
        private Collection<String> ownerIds;
        private Collection<String> principals;
        private Collection<String> origins;
        private Collection<String> executors;

        private Date startDate;
        private Date endDate;

        public AsyncJobStatusQueryBuilder setJobIds(Collection<String> jobIds) {
            this.jobIds = jobIds;
            return this;
        }

        public AsyncJobStatusQueryBuilder setJobIds(String... jobIds) {
            return this.setJobIds(jobIds != null ? Arrays.asList(jobIds) : null);
        }

        public Collection<String> getJobIds() {
            return this.jobIds;
        }

        public AsyncJobStatusQueryBuilder setJobKeys(Collection<String> jobKeys) {
            this.jobKeys = jobKeys;
            return this;
        }

        public AsyncJobStatusQueryBuilder setJobKeys(String... jobKeys) {
            return this.setJobKeys(jobKeys != null ? Arrays.asList(jobKeys) : null);
        }

        public Collection<String> getJobKeys() {
            return this.jobKeys;
        }

        public AsyncJobStatusQueryBuilder setJobStates(Collection<JobState> jobStates) {
            this.jobStates = jobStates;
            return this;
        }

        public AsyncJobStatusQueryBuilder setJobStates(JobState... jobStates) {
            return this.setJobStates(jobStates != null ? Arrays.asList(jobStates) : null);
        }

        public Collection<JobState> getJobStates() {
            return this.jobStates;
        }

        public AsyncJobStatusQueryBuilder setOwnerIds(Collection<String> ownerIds) {
            this.ownerIds = ownerIds;
            return this;
        }

        public AsyncJobStatusQueryBuilder setOwnerIds(String... ownerIds) {
            return this.setOwnerIds(ownerIds != null ? Arrays.asList(ownerIds) : null);
        }

        public Collection<String> getOwnerIds() {
            return this.ownerIds;
        }

        public AsyncJobStatusQueryBuilder setPrincipalNames(Collection<String> principals) {
            this.principals = principals;
            return this;
        }

        public AsyncJobStatusQueryBuilder setPrincipalNames(String... principals) {
            return this.setPrincipalNames(principals != null ? Arrays.asList(principals) : null);
        }

        public Collection<String> getPrincipalNames() {
            return this.principals;
        }

        public AsyncJobStatusQueryBuilder setOrigins(Collection<String> origins) {
            this.origins = origins;
            return this;
        }

        public AsyncJobStatusQueryBuilder setOrigins(String... origins) {
            return this.setOrigins(origins != null ? Arrays.asList(origins) : null);
        }

        public Collection<String> getOrigins() {
            return this.origins;
        }

        public AsyncJobStatusQueryBuilder setExecutors(Collection<String> executors) {
            this.executors = executors;
            return this;
        }

        public AsyncJobStatusQueryBuilder setExecutors(String... executors) {
            return this.setExecutors(executors != null ? Arrays.asList(executors) : null);
        }

        public Collection<String> getExecutors() {
            return this.executors;
        }

        public AsyncJobStatusQueryBuilder setStartDate(Date startDate) {
            this.startDate = startDate;
            return this;
        }

        public Date getStartDate() {
            return this.startDate;
        }

        public AsyncJobStatusQueryBuilder setEndDate(Date endDate) {
            this.endDate = endDate;
            return this;
        }

        public Date getEndDate() {
            return this.endDate;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }

            if (obj instanceof AsyncJobStatusQueryBuilder) {
                AsyncJobStatusQueryBuilder that = (AsyncJobStatusQueryBuilder) obj;

                EqualsBuilder builder = new EqualsBuilder()
                    .append(this.getJobIds(), that.getJobIds())
                    .append(this.getJobKeys(), that.getJobKeys())
                    .append(this.getJobStates(), that.getJobStates())
                    .append(this.getOwnerIds(), that.getOwnerIds())
                    .append(this.getPrincipalNames(), that.getPrincipalNames())
                    .append(this.getOrigins(), that.getOrigins())
                    .append(this.getExecutors(), that.getExecutors())
                    .append(this.getStartDate(), that.getStartDate())
                    .append(this.getEndDate(), that.getEndDate());

                return builder.isEquals();
            }

            return false;
        }

        @Override
        public int hashCode() {
            HashCodeBuilder builder = new HashCodeBuilder(37, 7)
                .append(this.getJobIds())
                .append(this.getJobKeys())
                .append(this.getJobStates())
                .append(this.getOwnerIds())
                .append(this.getPrincipalNames())
                .append(this.getOrigins())
                .append(this.getExecutors())
                .append(this.getStartDate())
                .append(this.getEndDate());

            return builder.toHashCode();
        }
    }



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
    public List<AsyncJobStatus> getJobsInState(Collection<JobState> states) {
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
    public List<AsyncJobStatus> getJobsInState(JobState... states) {
        return states != null ? this.getJobsInState(Arrays.asList(states)) : new ArrayList<>();
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

    /**
     * Fetches a collection of jobs based on the provided filter data in the query builder. If the
     * query builder is null or contains no arguments, this method will return all known async jobs.
     *
     * @param queryBuilder
     *  an AsyncJobStatusQueryBuilder instance containing the various arguments or filters to use
     *  to select jobs
     *
     * @return
     *  a list of jobs matching the provided query arguments/filters
     */
    public List<AsyncJobStatus> findJobs(AsyncJobStatusQueryBuilder queryBuilder) {
        EntityManager entityManager = this.getEntityManager();
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<AsyncJobStatus> query = criteriaBuilder.createQuery(AsyncJobStatus.class);

        Root<AsyncJobStatus> job = query.from(AsyncJobStatus.class);
        List<Predicate> predicates = this.buildJobQueryPredicates(criteriaBuilder, job, queryBuilder);

        query.select(job);

        if (predicates.size() > 0) {
            Predicate[] predicateArray = new Predicate[predicates.size()];
            query.where(predicates.toArray(predicateArray));
        }

        return entityManager.createQuery(query)
            .getResultList();
    }

    /**
     * Deletes a number of jobs based on the provided filter data in the query builder. If the
     * query builder is null or contains no arguments, this method does nothing.
     *
     * @param queryBuilder
     *  an AsyncJobStatusQueryBuilder instance containing the various arguments or filters to use
     *  to select jobs
     *
     * @return
     *  the number of jobs deleted as a result of a call to this method
     */
    public int deleteJobs(AsyncJobStatusQueryBuilder queryBuilder) {
        EntityManager entityManager = this.getEntityManager();
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaDelete<AsyncJobStatus> query = criteriaBuilder.createCriteriaDelete(AsyncJobStatus.class);

        Root<AsyncJobStatus> job = query.from(AsyncJobStatus.class);
        List<Predicate> predicates = this.buildJobQueryPredicates(criteriaBuilder, job, queryBuilder);

        // Sanity check: Don't execute a deletion if we haven't provided at least *some*
        // restrictions.
        if (predicates.size() > 0) {
            Predicate[] predicateArray = new Predicate[predicates.size()];
            query.where(predicates.toArray(predicateArray));

            return entityManager.createQuery(query)
                .executeUpdate();
        }

        return 0;
    }

    /**
     * Builds a collection of predicates to be used for querying jobs using the JPA criteria query
     * API.
     *
     * @param critBuilder
     *  the CriteriaBuilder instance to use to create predicates

     * @param root
     *  the root of the query, should be a reference to the AsyncJobStatus root
     *
     * @param queryBuilder
     *  an AsyncJobStatusQueryBuilder instance containing the various arguments or filters to use
     *  to select jobs
     *
     * @return
     *  a list of predicates to select jobs based on the query parameters provided
     */
    private List<Predicate> buildJobQueryPredicates(CriteriaBuilder criteriaBuilder,
        Root<AsyncJobStatus> root, AsyncJobStatusQueryBuilder queryBuilder) {

        List<Predicate> predicates = new ArrayList<>();

        if (queryBuilder != null) {
            if (queryBuilder.getJobIds() != null && !queryBuilder.getJobIds().isEmpty()) {
                predicates.add(root.get("id").in(queryBuilder.getJobIds()));
            }

            if (queryBuilder.getJobKeys() != null && !queryBuilder.getJobKeys().isEmpty()) {
                predicates.add(root.get("jobKey").in(queryBuilder.getJobKeys()));
            }

            if (queryBuilder.getJobStates() != null && !queryBuilder.getJobStates().isEmpty()) {
                predicates.add(root.get("state").in(queryBuilder.getJobStates()));
            }

            Collection<String> ownerIds = queryBuilder.getOwnerIds();
            if (ownerIds != null && !ownerIds.isEmpty()) {
                Predicate ownerPredicate = root.get("ownerId").in(queryBuilder.getOwnerIds());

                if (ownerIds.contains(null)) {
                    // While it'd be nice to remove nulls here, we can't guarantee every collection
                    // provided will support the remove operation, so we'll just leave it.

                    ownerPredicate = ownerIds.size() > 1 ?
                        criteriaBuilder.or(ownerPredicate, root.get("ownerId").isNull()) :
                        root.get("ownerId").isNull();
                }

                predicates.add(ownerPredicate);
            }

            if (queryBuilder.getPrincipalNames() != null && !queryBuilder.getPrincipalNames().isEmpty()) {
                predicates.add(root.get("principal").in(queryBuilder.getPrincipalNames()));
            }

            if (queryBuilder.getOrigins() != null && !queryBuilder.getOrigins().isEmpty()) {
                predicates.add(root.get("origin").in(queryBuilder.getOrigins()));
            }

            if (queryBuilder.getExecutors() != null && !queryBuilder.getExecutors().isEmpty()) {
                predicates.add(root.get("executor").in(queryBuilder.getExecutors()));
            }

            if (queryBuilder.getStartDate() != null) {
                predicates.add(
                    criteriaBuilder.greaterThanOrEqualTo(root.get("updated"), queryBuilder.getStartDate()));
            }

            if (queryBuilder.getEndDate() != null) {
                predicates.add(
                    criteriaBuilder.lessThanOrEqualTo(root.get("updated"), queryBuilder.getEndDate()));
            }
        }

        return predicates;
    }

}
