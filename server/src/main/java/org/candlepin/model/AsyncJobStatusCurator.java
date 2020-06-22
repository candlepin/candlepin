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
    public static class AsyncJobStatusQueryBuilder {

        public static final class Order {
            private final String column;
            private final boolean reverse;

            public Order(String column, boolean reverse) {
                if (column == null || column.isEmpty()) {
                    throw new IllegalArgumentException("column is null or empty");
                }

                this.column = column;
                this.reverse = reverse;
            }

            public String column() {
                return this.column;
            }

            public boolean reverse() {
                return this.reverse;
            }
        }

        private Collection<String> jobIds;
        private Collection<String> jobKeys;
        private Collection<JobState> jobStates;
        private Collection<String> ownerIds;
        private Collection<String> principals;
        private Collection<String> origins;
        private Collection<String> executors;

        private Date startDate;
        private Date endDate;

        private Integer offset;
        private Integer limit;
        private Collection<Order> order;

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

        public AsyncJobStatusQueryBuilder setOffset(Integer offset) {
            this.offset = offset;
            return this;
        }

        public Integer getOffset() {
            return this.offset;
        }

        public AsyncJobStatusQueryBuilder setLimit(Integer limit) {
            this.limit = limit;
            return this;
        }

        public Integer getLimit() {
            return this.limit;
        }

        public AsyncJobStatusQueryBuilder setOrder(Collection<Order> order) {
            this.order = order;
            return this;
        }

        public Collection<Order> getOrder() {
            return this.order;
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
                    .append(this.getEndDate(), that.getEndDate())
                    .append(this.getOffset(), that.getOffset())
                    .append(this.getLimit(), that.getLimit())
                    .append(this.getOrder(), that.getOrder());

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
                .append(this.getEndDate())
                .append(this.getOffset())
                .append(this.getLimit())
                .append(this.getOrder());

            return builder.toHashCode();
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
     * @param queryBuilder
     *  an AsyncJobStatusQueryBuilder instance containing the various arguments or filters to use
     *  to select jobs
     *
     * @return
     *  a list of jobs matching the provided query arguments/filters
     */
    public List<AsyncJobStatus> findJobs(AsyncJobStatusQueryBuilder queryBuilder) {
        CriteriaBuilder criteriaBuilder = this.getEntityManager().getCriteriaBuilder();
        CriteriaQuery<AsyncJobStatus> criteriaQuery = criteriaBuilder.createQuery(AsyncJobStatus.class);

        Root<AsyncJobStatus> job = criteriaQuery.from(AsyncJobStatus.class);
        criteriaQuery.select(job);

        List<Predicate> predicates = this.buildJobQueryPredicates(criteriaBuilder, job, queryBuilder);
        if (predicates.size() > 0) {
            Predicate[] predicateArray = new Predicate[predicates.size()];
            criteriaQuery.where(predicates.toArray(predicateArray));
        }

        List<Order> order = this.buildJobQueryOrder(criteriaBuilder, job, queryBuilder);
        if (order.size() > 0) {
            criteriaQuery.orderBy(order);
        }

        TypedQuery<AsyncJobStatus> query = this.getEntityManager()
            .createQuery(criteriaQuery);

        if (queryBuilder != null) {
            Integer offset = queryBuilder.getOffset();
            if (offset != null && offset > 0) {
                query.setFirstResult(offset);
            }

            Integer limit = queryBuilder.getLimit();
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
     * @param queryBuilder
     *  an AsyncJobStatusQueryBuilder instance containing the various arguments or filters to use
     *  to count jobs
     *
     * @return
     *  a list of jobs matching the provided query arguments/filters
     */
    public long getJobCount(AsyncJobStatusQueryBuilder queryBuilder) {
        CriteriaBuilder criteriaBuilder = this.getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Long> query = criteriaBuilder.createQuery(Long.class);

        Root<AsyncJobStatus> job = query.from(AsyncJobStatus.class);
        query.select(criteriaBuilder.count(job));

        List<Predicate> predicates = this.buildJobQueryPredicates(criteriaBuilder, job, queryBuilder);
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

        // Sanity check: Don't execute a deletion if we haven't provided at least *some*
        // restrictions.
        List<Predicate> predicates = this.buildJobQueryPredicates(criteriaBuilder, job, queryBuilder);
        if (predicates.size() > 0) {
            Predicate[] predicateArray = new Predicate[predicates.size()];
            query.where(predicates.toArray(predicateArray));

            return entityManager.createQuery(query)
                .executeUpdate();
        }

        return 0;
    }

    /**
     * Builds a collection of order instances to be used for querying jobs using the JPA criteria
     * query API.
     *
     * @param critBuilder
     *  the CriteriaBuilder instance to use to create order

     * @param root
     *  the root of the query, should be a reference to the AsyncJobStatus root
     *
     * @param queryBuilder
     *  an AsyncJobStatusQueryBuilder instance containing the order specification
     *  to select jobs
     *
     * @throws InvalidOrderKeyException
     *  if an order is provided referencing an attribute name (key) that does not exist
     *
     * @return
     *  a list of order instances to sort jobs based on the query ordering provided
     */
    private List<Order> buildJobQueryOrder(CriteriaBuilder criteriaBuilder, Root<AsyncJobStatus> root,
        AsyncJobStatusQueryBuilder queryBuilder) {

        List<Order> orderList = new ArrayList<>();

        if (queryBuilder != null) {
            if (queryBuilder.getOrder() != null) {
                for (AsyncJobStatusQueryBuilder.Order order : queryBuilder.getOrder()) {
                    try {
                        orderList.add(order.reverse() ?
                            criteriaBuilder.desc(root.get(order.column())) :
                            criteriaBuilder.asc(root.get(order.column())));
                    }
                    catch (IllegalArgumentException e) {
                        String errmsg = String.format("Invalid attribute key: %s", order.column());
                        throw new InvalidOrderKeyException(errmsg, e);
                    }
                }
            }
        }

        return orderList;
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
        predicates.add(criteriaBuilder.equal(job.get("jobKey"), jobKey));

        // Add the non-terminal state restriction
        Collection<JobState> states = Arrays.stream(JobState.values())
            .filter(s -> !s.isTerminal())
            .collect(Collectors.toSet());

        predicates.add(job.get("state").in(states));

        // Add the argument restrictions if necessary
        if (arguments != null) {
            // Sanity check: make sure we don't have too many arguments for the backend to handle
            // in a single query. 10 is well below the technical limits, but if we're hitting that
            // we're probably doing something wrong.
            if (arguments.size() > MAX_JOB_ARGUMENTS_PER_QUERY) {
                throw new IllegalArgumentException("arguments map contains too many arguments");
            }

            for (Map.Entry<String, String> entry : arguments.entrySet()) {
                MapJoin<AsyncJobStatus, String, String> jobArguments = job.joinMap("arguments");

                predicates.add(criteriaBuilder.and(
                    criteriaBuilder.equal(jobArguments.key(), entry.getKey()),
                    criteriaBuilder.equal(jobArguments.value(), entry.getValue())
                ));
            }
        }

        query.select(job.get("id"));

        Predicate[] predicateArray = new Predicate[predicates.size()];
        query.where(predicates.toArray(predicateArray));

        return this.getEntityManager()
            .createQuery(query)
            .getResultList();
    }

}
