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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaDelete;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.CriteriaUpdate;
import javax.persistence.criteria.MapJoin;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;



/**
 * AsyncJobStatusCurator
 */
@Singleton
public class AsyncJobStatusCurator extends AbstractHibernateCurator<AsyncJobStatus> {

    /** Defines the maximum number of job arguments that can be provided for a single query */
    public static final int MAX_JOB_ARGUMENTS_PER_QUERY = 10;

    /**
     * Container object for providing various arguments to the job status lookup method(s).
     */
    public static class AsyncJobStatusQueryArguments extends QueryArguments<AsyncJobStatusQueryArguments> {

        private Collection<String> jobIds;
        private Collection<String> jobKeys;
        private Collection<JobState> jobStates;
        private Collection<String> ownerIds;
        private Collection<String> principals;
        private Collection<String> origins;
        private Collection<String> executors;

        private Date startDate;
        private Date endDate;

        public AsyncJobStatusQueryArguments setJobIds(Collection<String> jobIds) {
            this.jobIds = jobIds;
            return this;
        }

        public AsyncJobStatusQueryArguments setJobIds(String... jobIds) {
            return this.setJobIds(jobIds != null ? Arrays.asList(jobIds) : null);
        }

        public Collection<String> getJobIds() {
            return this.jobIds;
        }

        public AsyncJobStatusQueryArguments setJobKeys(Collection<String> jobKeys) {
            this.jobKeys = jobKeys;
            return this;
        }

        public AsyncJobStatusQueryArguments setJobKeys(String... jobKeys) {
            return this.setJobKeys(jobKeys != null ? Arrays.asList(jobKeys) : null);
        }

        public Collection<String> getJobKeys() {
            return this.jobKeys;
        }

        public AsyncJobStatusQueryArguments setJobStates(Collection<JobState> jobStates) {
            this.jobStates = jobStates;
            return this;
        }

        public AsyncJobStatusQueryArguments setJobStates(JobState... jobStates) {
            return this.setJobStates(jobStates != null ? Arrays.asList(jobStates) : null);
        }

        public Collection<JobState> getJobStates() {
            return this.jobStates;
        }

        public AsyncJobStatusQueryArguments setOwnerIds(Collection<String> ownerIds) {
            this.ownerIds = ownerIds;
            return this;
        }

        public AsyncJobStatusQueryArguments setOwnerIds(String... ownerIds) {
            return this.setOwnerIds(ownerIds != null ? Arrays.asList(ownerIds) : null);
        }

        public Collection<String> getOwnerIds() {
            return this.ownerIds;
        }

        public AsyncJobStatusQueryArguments setPrincipalNames(Collection<String> principals) {
            this.principals = principals;
            return this;
        }

        public AsyncJobStatusQueryArguments setPrincipalNames(String... principals) {
            return this.setPrincipalNames(principals != null ? Arrays.asList(principals) : null);
        }

        public Collection<String> getPrincipalNames() {
            return this.principals;
        }

        public AsyncJobStatusQueryArguments setOrigins(Collection<String> origins) {
            this.origins = origins;
            return this;
        }

        public AsyncJobStatusQueryArguments setOrigins(String... origins) {
            return this.setOrigins(origins != null ? Arrays.asList(origins) : null);
        }

        public Collection<String> getOrigins() {
            return this.origins;
        }

        public AsyncJobStatusQueryArguments setExecutors(Collection<String> executors) {
            this.executors = executors;
            return this;
        }

        public AsyncJobStatusQueryArguments setExecutors(String... executors) {
            return this.setExecutors(executors != null ? Arrays.asList(executors) : null);
        }

        public Collection<String> getExecutors() {
            return this.executors;
        }

        public AsyncJobStatusQueryArguments setStartDate(Date startDate) {
            this.startDate = startDate;
            return this;
        }

        public Date getStartDate() {
            return this.startDate;
        }

        public AsyncJobStatusQueryArguments setEndDate(Date endDate) {
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

            if (obj instanceof AsyncJobStatusQueryArguments && super.equals(obj)) {
                AsyncJobStatusQueryArguments that = (AsyncJobStatusQueryArguments) obj;

                return new EqualsBuilder()
                    .append(this.getJobIds(), that.getJobIds())
                    .append(this.getJobKeys(), that.getJobKeys())
                    .append(this.getJobStates(), that.getJobStates())
                    .append(this.getOwnerIds(), that.getOwnerIds())
                    .append(this.getPrincipalNames(), that.getPrincipalNames())
                    .append(this.getOrigins(), that.getOrigins())
                    .append(this.getExecutors(), that.getExecutors())
                    .append(this.getStartDate(), that.getStartDate())
                    .append(this.getEndDate(), that.getEndDate())
                    .isEquals();
            }

            return false;
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder(37, 7)
                .append(super.hashCode())
                .append(this.getJobIds())
                .append(this.getJobKeys())
                .append(this.getJobStates())
                .append(this.getOwnerIds())
                .append(this.getPrincipalNames())
                .append(this.getOrigins())
                .append(this.getExecutors())
                .append(this.getStartDate())
                .append(this.getEndDate())
                .toHashCode();
        }
    }

    /**
     * Creates a new AsyncJobStatusCurator instance
     */
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
     * @param queryArgs
     *  an AsyncJobStatusQueryArguments instance containing the various arguments or filters to use
     *  to select jobs
     *
     * @return
     *  a list of jobs matching the provided query arguments/filters
     */
    public List<AsyncJobStatus> findJobs(AsyncJobStatusQueryArguments queryArgs) {
        CriteriaBuilder criteriaBuilder = this.getEntityManager().getCriteriaBuilder();
        CriteriaQuery<AsyncJobStatus> criteriaQuery = criteriaBuilder.createQuery(AsyncJobStatus.class);

        Root<AsyncJobStatus> job = criteriaQuery.from(AsyncJobStatus.class);
        criteriaQuery.select(job);

        List<Predicate> predicates = this.buildJobQueryPredicates(criteriaBuilder, job, queryArgs);
        if (predicates.size() > 0) {
            Predicate[] predicateArray = new Predicate[predicates.size()];
            criteriaQuery.where(predicates.toArray(predicateArray));
        }

        List<Order> order = this.buildJPAQueryOrder(criteriaBuilder, job, queryArgs);
        if (order != null && order.size() > 0) {
            criteriaQuery.orderBy(order);
        }

        TypedQuery<AsyncJobStatus> query = this.getEntityManager()
            .createQuery(criteriaQuery);

        if (queryArgs != null) {
            Integer offset = queryArgs.getOffset();
            if (offset != null && offset > 0) {
                query.setFirstResult(offset);
            }

            Integer limit = queryArgs.getLimit();
            if (limit != null && limit > 0) {
                query.setMaxResults(limit);
            }
        }

        return query.getResultList();
    }

    /**
     * Fetches the count of jobs matching the provided filter data in the query builder. If the
     * query builder is null or contains no arguments, this method will return the count of all
     * known jobs.
     *
     * @param queryArgs
     *  an AsyncJobStatusQueryArguments instance containing the various arguments or filters to use
     *  to count jobs
     *
     * @return
     *  the number of jobs matching the provided query arguments/filters
     */
    public long getJobCount(AsyncJobStatusQueryArguments queryArgs) {
        CriteriaBuilder criteriaBuilder = this.getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Long> query = criteriaBuilder.createQuery(Long.class);

        Root<AsyncJobStatus> job = query.from(AsyncJobStatus.class);
        query.select(criteriaBuilder.count(job));

        List<Predicate> predicates = this.buildJobQueryPredicates(criteriaBuilder, job, queryArgs);
        if (predicates.size() > 0) {
            Predicate[] predicateArray = new Predicate[predicates.size()];
            query.where(predicates.toArray(predicateArray));
        }

        return this.getEntityManager()
            .createQuery(query)
            .getSingleResult();
    }

    /**
     * Deletes a number of jobs based on the provided filter data in the query builder. If the
     * query builder is null or contains no arguments, this method does nothing.
     *
     * @param queryArgs
     *  an AsyncJobStatusQueryArguments instance containing the various arguments or filters to use
     *  to select jobs
     *
     * @return
     *  the number of jobs deleted as a result of a call to this method
     */
    public int deleteJobs(AsyncJobStatusQueryArguments queryArgs) {
        EntityManager entityManager = this.getEntityManager();
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaDelete<AsyncJobStatus> query = criteriaBuilder.createCriteriaDelete(AsyncJobStatus.class);

        Root<AsyncJobStatus> job = query.from(AsyncJobStatus.class);

        // Sanity check: Don't execute a deletion if we haven't provided at least *some* restrictions.
        List<Predicate> predicates = this.buildJobQueryPredicates(criteriaBuilder, job, queryArgs);
        if (predicates.size() > 0) {
            Predicate[] predicateArray = new Predicate[predicates.size()];
            query.where(predicates.toArray(predicateArray));

            return entityManager.createQuery(query)
                .executeUpdate();
        }

        return 0;
    }

    /**
     * Sets all jobs matching the parameters provided by the given query builder to the specified job
     * state without any state transition validation.
     * <p></p>
     * <strong>Warning:</strong> This method provides no state transition validation, and could put
     * jobs into invalid or desynced states.
     *
     * @param queryArgs
     *  an AsyncJobStatusQueryArguments instance containing the various arguments or filters to use
     *  to select jobs
     *
     * @param state
     *  the JobState to set to the given jobs
     *
     * @return
     *  the number of jobs updated as a result of a call to this method
     */
    public int updateJobState(AsyncJobStatusQueryArguments queryArgs, JobState state) {
        EntityManager entityManager = this.getEntityManager();
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaUpdate<AsyncJobStatus> update = criteriaBuilder.createCriteriaUpdate(AsyncJobStatus.class);
        Root<AsyncJobStatus> job = update.from(AsyncJobStatus.class);

        // Impl note: order of the assignments is important here.
        update.set(job.get(AsyncJobStatus_.previousState), job.get(AsyncJobStatus_.state))
            .set(job.get(AsyncJobStatus_.state), state);

        // Sanity check: Don't execute a state change if we haven't provided at least *some* restrictions.
        List<Predicate> predicates = this.buildJobQueryPredicates(criteriaBuilder, job, queryArgs);
        if (predicates.size() > 0) {
            Predicate[] predicateArray = new Predicate[predicates.size()];
            update.where(predicates.toArray(predicateArray));

            return entityManager.createQuery(update)
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
     * @param queryArgs
     *  an AsyncJobStatusQueryArguments instance containing the various arguments or filters to use
     *  to select jobs
     *
     * @return
     *  a list of predicates to select jobs based on the query parameters provided
     */
    private List<Predicate> buildJobQueryPredicates(CriteriaBuilder criteriaBuilder,
        Root<AsyncJobStatus> root, AsyncJobStatusQueryArguments queryArgs) {

        List<Predicate> predicates = new ArrayList<>();

        if (queryArgs != null) {
            if (this.checkQueryArgumentCollection(queryArgs.getJobIds())) {
                predicates.add(root.get(AsyncJobStatus_.id).in(queryArgs.getJobIds()));
            }

            if (this.checkQueryArgumentCollection(queryArgs.getJobKeys())) {
                predicates.add(root.get(AsyncJobStatus_.jobKey).in(queryArgs.getJobKeys()));
            }

            if (this.checkQueryArgumentCollection(queryArgs.getJobStates())) {
                predicates.add(root.get(AsyncJobStatus_.state).in(queryArgs.getJobStates()));
            }

            Collection<String> ownerIds = queryArgs.getOwnerIds();
            if (this.checkQueryArgumentCollection(ownerIds)) {
                Predicate ownerPredicate = root.get(AsyncJobStatus_.ownerId).in(ownerIds);

                if (ownerIds.contains(null)) {
                    // While it'd be nice to remove nulls here, we can't guarantee every collection
                    // provided will support the remove operation, so we'll just leave it.
                    Predicate nullPredicate = root.get(AsyncJobStatus_.ownerId).isNull();

                    ownerPredicate = ownerIds.size() > 1 ?
                        criteriaBuilder.or(ownerPredicate, nullPredicate) :
                        nullPredicate;
                }

                predicates.add(ownerPredicate);
            }

            if (this.checkQueryArgumentCollection(queryArgs.getPrincipalNames())) {
                predicates.add(root.get(AsyncJobStatus_.principal).in(queryArgs.getPrincipalNames()));
            }

            if (this.checkQueryArgumentCollection(queryArgs.getOrigins())) {
                predicates.add(root.get(AsyncJobStatus_.origin).in(queryArgs.getOrigins()));
            }

            if (this.checkQueryArgumentCollection(queryArgs.getExecutors())) {
                predicates.add(root.get(AsyncJobStatus_.executor).in(queryArgs.getExecutors()));
            }

            if (queryArgs.getStartDate() != null) {
                predicates.add(criteriaBuilder
                    .greaterThanOrEqualTo(root.get(AsyncJobStatus_.updated), queryArgs.getStartDate()));
            }

            if (queryArgs.getEndDate() != null) {
                predicates.add(criteriaBuilder
                    .lessThanOrEqualTo(root.get(AsyncJobStatus_.updated), queryArgs.getEndDate()));
            }
        }

        return predicates;
    }

    /**
     * Fetches a collection of job IDs for jobs in non-terminal states matching the given job key
     * and having all of the provided job arguments with the specified values.
     * <p></p>
     * This method is designed specifically for the unique-by-argument constraint family.
     *
     * @param jobKey
     *  the job key to restrict
     *
     * @param arguments
     *  a map containing the arguments to use for filtering jobs; cannot contain more than
     *  10 entries
     *
     * @throws IllegalArgumentException
     *  if jobKey is null or empty, or the arguments map is too large
     *
     * @return
     *  A collection of IDs of non-terminal jobs matching the given job key and using the specified
     *  arguments
     */
    public List<String> fetchJobIdsByArguments(String jobKey, Map<String, String> arguments) {
        if (jobKey == null || jobKey.isEmpty()) {
            throw new IllegalArgumentException("jobKey is null or empty");
        }

        EntityManager entityManager = this.getEntityManager();
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<String> query = criteriaBuilder.createQuery(String.class);
        Root<AsyncJobStatus> job = query.from(AsyncJobStatus.class);

        List<Predicate> predicates = new ArrayList<>();

        // Add the job key restriction
        predicates.add(criteriaBuilder.equal(job.get(AsyncJobStatus_.jobKey), jobKey));

        // Add the non-terminal state restriction
        Collection<JobState> states = Arrays.stream(JobState.values())
            .filter(s -> !s.isTerminal())
            .collect(Collectors.toSet());

        predicates.add(job.get(AsyncJobStatus_.state).in(states));

        // Add the argument restrictions if necessary
        if (arguments != null) {
            // Sanity check: make sure we don't have too many arguments for the backend to handle
            // in a single query. 10 is well below the technical limits, but if we're hitting that
            // we're probably doing something wrong.
            if (arguments.size() > MAX_JOB_ARGUMENTS_PER_QUERY) {
                throw new IllegalArgumentException("arguments map contains too many arguments");
            }

            for (Map.Entry<String, String> entry : arguments.entrySet()) {
                MapJoin<AsyncJobStatus, String, String> jobArguments = job.join(AsyncJobStatus_.arguments);

                predicates.add(criteriaBuilder.and(
                    criteriaBuilder.equal(jobArguments.key(), entry.getKey()),
                    criteriaBuilder.equal(jobArguments.value(), entry.getValue())
                ));
            }
        }

        query.select(job.get(AsyncJobStatus_.id));

        Predicate[] predicateArray = new Predicate[predicates.size()];
        query.where(predicates.toArray(predicateArray));

        return this.getEntityManager()
            .createQuery(query)
            .getResultList();
    }

}
