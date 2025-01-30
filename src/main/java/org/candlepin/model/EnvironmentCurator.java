/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import org.candlepin.util.NonNullLinkedHashSet;

import org.hibernate.query.NativeQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.Query;
import javax.persistence.TypedQuery;

@Singleton
public class EnvironmentCurator extends AbstractHibernateCurator<Environment> {
    private static final Logger log = LoggerFactory.getLogger(EnvironmentCurator.class);

    public EnvironmentCurator() {
        super(Environment.class);
    }

    /**
     * Fetches the consumers associated with the specified environment.
     *
     * @param environment
     *  The environment for which to fetch consumers
     *
     * @return
     *  A CandlepinQuery to iterate the consumers associated with the specified environment
     */
    public List<Consumer> getEnvironmentConsumers(Environment environment) {
        return this.getEnvironmentConsumers(environment != null ? environment.getId() : null);
    }

    /**
     * Fetches the consumers associated with the specified environment ID.
     *
     * @param environmentId
     *  The ID of the environment for which to fetch consumers
     *
     * @return
     *  A CandlepinQuery to iterate the consumers associated with the specified environment ID
     */
    public List<Consumer> getEnvironmentConsumers(String environmentId) {
        String jpql = "SELECT c FROM Consumer c " +
            "JOIN c.environmentIds e " +
            "WHERE e = :environmentId ";

        return this.getEntityManager()
            .createQuery(jpql, Consumer.class)
            .setParameter("environmentId", environmentId)
            .getResultList();
    }

    /**
     * Retrieves a list on environments based on the criteria and given types
     * @param owner
     *  The owner of the environments
     * @param envName
     *  An exact name for the environments
     * @param type
     *  A list of types to filter the environments
     * @return
     *  A List of environments based on the criteria
     */
    public List<Environment> listByType(Owner owner, String envName, List<String> type) {
        if (owner == null) {
            throw new IllegalArgumentException("Owner is null");
        }
        StringBuilder jpql = new StringBuilder("SELECT e FROM Environment e WHERE e.owner.id = :owner_id ");
        if (envName != null &&  !envName.isEmpty()) {
            jpql.append("AND e.name = :envName ");
        }
        if (type == null || type.isEmpty()) {
            jpql.append("AND e.type is null");
        }
        else {
            jpql.append("AND e.type in :type");
        }

        TypedQuery query =  this.getEntityManager().createQuery(jpql.toString(), Environment.class);
        query.setParameter("owner_id", owner.getId());
        if (envName != null && !envName.isEmpty()) {
            query.setParameter("envName", envName);
        }
        if (type != null && !type.isEmpty()) {
            query.setParameter("type", type.stream().map(x -> x.toLowerCase()).toList());
        }
        return query.getResultList();
    }

    /**
     * Retrieves a list on environments based on the criteria and given types
     * @param owner
     *  The owner of the environments
     * @param envName
     *  An exact name for the environments
     * @return
     *  A List of environments based on the criteria
     */
    public List<Environment> listAllTypes(Owner owner, String envName) {
        if (owner == null) {
            throw new IllegalArgumentException("Owner is null");
        }
        StringBuilder jpql = new StringBuilder("SELECT e FROM Environment e  WHERE e.owner.id = :owner_id ");
        if (envName != null && !envName.isEmpty()) {
            jpql.append(" AND e.name = :envName");
        }

        TypedQuery query =  this.getEntityManager().createQuery(jpql.toString(), Environment.class);
        query.setParameter("owner_id", owner.getId());
        if (envName != null && !envName.isEmpty()) {
            query.setParameter("envName", envName);
        }
        return query.getResultList();
    }

    /**
     * Performs a bulk deletion of all environment objects for the given owner.
     *
     * @param owner
     *  The owner for which to delete environments
     *
     * @return
     *  the number of environments deleted
     */
    public int deleteEnvironmentsForOwner(Owner owner) {
        String jpql = "DELETE FROM Environment env WHERE env.owner.id = :owner_id";

        int count = this.getEntityManager()
            .createQuery(jpql)
            .setParameter("owner_id", owner.getId())
            .executeUpdate();

        log.info("{} environments deleted", count);
        return count;
    }

    /**
     * Get environment Id by owner id and environment name.
     *
     * @param ownerId owner Id.
     * @param envName environment name.
     * @return environment Id
     */
    public String getEnvironmentIdByName(String ownerId, String envName) {
        String jpql = "SELECT e.id FROM Environment e WHERE e.owner.id = :owner_id and e.name = :env_name";
        String envId = null;

        if (ownerId != null && envName != null) {
            try {
                envId = this.getEntityManager()
                    .createQuery(jpql, String.class)
                    .setParameter("owner_id", ownerId)
                    .setParameter("env_name", envName)
                    .getSingleResult();
            }
            catch (NoResultException e) {
                log.trace("Unable to find environment Id by envName {}", envName, e);
            }
        }

        return envId;
    }

    /**
     * Fetches the multiple environments for the specified consumer. If the consumer does not have a defined
     * environments, this method returns an empty list. If the consumer has an invalid environments,
     * this method throws an exception.
     *
     * @param consumer
     *  The consumer for which to fetch multiple environment object
     *
     * @throws IllegalArgumentException
     *  if consumer is null
     *
     * @return
     *  A list of environment instance for the specified consumer, or empty list
     *  if the consumer does not have a defined environments.
     */
    public List<Environment> getConsumerEnvironments(Consumer consumer) {
        if (consumer == null) {
            throw new IllegalArgumentException("consumer is null");
        }

        String jpql = "SELECT environment " +
            "FROM Consumer c " +
            "JOIN c.environmentIds e " +
            "JOIN Environment environment on environment.id = e " +
            "WHERE c.id = :consumerId " +
            "ORDER BY key(e) ASC";

        return this.getEntityManager()
            .createQuery(jpql, Environment.class)
            .setParameter("consumerId", consumer.getId())
            .getResultList();
    }

    public Map<String, List<String>> findEnvironmentsOf(Collection<String> consumerIds) {
        if (consumerIds.isEmpty()) {
            throw new IllegalArgumentException("Consumers must not be empty!");
        }

        String jpql = "SELECT c.id, e" +
            " FROM Consumer c" +
            " JOIN c.environmentIds e" +
            " WHERE c.id IN (:consumerIds)" +
            " ORDER BY c.uuid, key(e) ASC";

        Query query = this.getEntityManager()
            .createQuery(jpql);

        Map<String, List<String>> consumerEnvironments = new HashMap<>(consumerIds.size());
        for (List<String> ids : partition(consumerIds)) {
            List<Object[]> result = query.setParameter("consumerIds", ids).getResultList();
            Map<String, List<String>> map = toMap(result);
            consumerEnvironments.putAll(map);
        }

        return consumerEnvironments;
    }

    private Map<String, List<String>> toMap(List<Object[]> rawData) {
        Map<String, List<String>> result = new HashMap<>();
        for (Object[] obj : rawData) {
            String consumerId = obj[0].toString();
            String environmentId = obj[1].toString();
            List<String> environments = result.getOrDefault(consumerId, new ArrayList<>());
            environments.add(environmentId);
            result.put(consumerId, environments);
        }
        return result;
    }

    /**
     * Removes the content references from environments in the given organization. Called as part
     * of the removeOwnerContentReferences operation.
     *
     * @param owner
     *  the owner/organization in which to remove content references from environments
     *
     * @param contentIds
     *  a collection of IDs of the content to remove from environments within the specified org
     *
     * @return
     *  the number of environment-content references removed as a result of this operation
     */
    public int removeEnvironmentContentReferences(Owner owner, Collection<String> contentIds) {
        EntityManager entityManager = this.getEntityManager();

        String jpql = "SELECT DISTINCT env.id FROM Environment env WHERE env.ownerId = :owner_id";
        List<String> envIds = entityManager.createQuery(jpql, String.class)
            .setParameter("owner_id", owner.getId())
            .getResultList();

        int count = 0;

        if (envIds != null && !envIds.isEmpty()) {
            // Delete the entries
            // Impl note: at the time of writing, JPA doesn't support doing this operation without
            // interacting with the objects directly. So, we're doing it with native SQL to avoid
            // even more work here.
            // Also note that MySQL/MariaDB doesn't like table aliases in a delete statement.
            String sql = "DELETE FROM cp_environment_content " +
                "WHERE environment_id IN (:env_ids) AND content_id IN (:content_ids)";

            int blockSize = Math.min(this.getQueryParameterLimit() / 2, this.getInBlockSize() / 2);
            Iterable<List<String>> eidBlocks = this.partition(envIds, blockSize);
            Iterable<List<String>> cidBlocks = this.partition(contentIds, blockSize);

            Query query = entityManager.createNativeQuery(sql)
                .unwrap(NativeQuery.class)
                .addSynchronizedEntityClass(Environment.class)
                .addSynchronizedEntityClass(EnvironmentContent.class);

            for (List<String> eidBlock : eidBlocks) {
                query.setParameter("env_ids", eidBlock);

                for (List<String> cidBlock : cidBlocks) {
                    count += query.setParameter("content_ids", cidBlock)
                        .executeUpdate();
                }
            }
        }

        log.debug("{} environment content reference(s) removed", count);
        return count;
    }

    /**
     * Determines if any of the provided {@link Environment} IDs does not exist or does not belong to the
     * provided {@link Owner}.
     *
     * @param owner
     *  the owner of the environment IDs
     *
     * @param envIds
     *  the environment IDs to check
     *
     * @return all of the environment IDs from the provided list that do not exist or do not belong to the
     *  provided owner
     */
    public Set<String> getNonExistentEnvironmentIds(Owner owner, Collection<String> envIds) {
        if (owner == null || envIds == null || envIds.isEmpty()) {
            return new HashSet<>();
        }

        String jpql = "SELECT DISTINCT env.id FROM Environment env " +
            "WHERE env.id IN (:envIds) AND env.ownerId = :ownerId";

        Set<String> distinctEnvIds = new HashSet<>(envIds);

        Query query = this.getEntityManager()
            .createQuery(jpql, String.class)
            .setParameter("ownerId", owner.getId());

        int blockSize = Math.min(this.getQueryParameterLimit() - 1, this.getInBlockSize());

        Set<String> actualEnvIds = new HashSet<>();
        for (List<String> block : partition(distinctEnvIds, blockSize)) {
            List<String> result = query.setParameter("envIds", block)
                .getResultList();

            actualEnvIds.addAll(result);
        }

        // Remove all of the existing environment IDs to determine the environments that are unknown
        distinctEnvIds.removeAll(actualEnvIds);

        return distinctEnvIds;
    }

    /**
     * Removes the {@link Consumer}s from all of the environments they currently exist in.
     *
     * @param consumerUuids
     *  the UUIDs for all the consumers that should be removed from their environments
     *
     * @return the number of consumers removed from all environments
     */
    private int removeConsumersFromAllEnvironments(Collection<String> consumerUuids) {
        if (consumerUuids == null || consumerUuids.isEmpty()) {
            return 0;
        }

        String statement = "DELETE FROM cp_consumer_environments " +
            "WHERE cp_consumer_id IN (SELECT id FROM cp_consumer WHERE uuid IN (:uuids))";

        Query query = this.getEntityManager()
            .createNativeQuery(statement);

        int updated = 0;

        for (List<String> block : partition(consumerUuids)) {
            updated += query.setParameter("uuids", block)
                .executeUpdate();
        }

        return updated;
    }

    /**
     * Sets the {@Consumer}s in the provided {@link Environment}s. The consumers will first be cleared from
     * existing environments and then set to the provided environments. If null or empty environment IDs are
     * provided then the consumers will only be cleared from their existing environments. The ordering of the
     * provided environment IDs dictates the priority. The first environment ID in the list being the top
     * priority and the last environment ID in the list being the least priority.
     *
     * @param consumerUuids
     *  the UUIDs of the consumers to set the environments for
     *
     * @param envIds
     *  the IDs of the environments to set the consumers for
     *
     * @return the number of consumers updated
     */
    public int setConsumersEnvironments(Collection<String> consumerUuids,
        NonNullLinkedHashSet<String> envIds) {

        if (consumerUuids == null || consumerUuids.isEmpty()) {
            return 0;
        }

        // Remove duplicate consumer UUIDs to avoid constraint violations when inserting into the
        // cp_consumer_environments table
        Set<String> consumerUuidsSet = new HashSet<>(consumerUuids);

        int removedFromEnvs = removeConsumersFromAllEnvironments(consumerUuidsSet);
        if (envIds == null || envIds.isEmpty()) {
            return removedFromEnvs;
        }

        String insertStatement = "INSERT INTO cp_consumer_environments " +
            "(cp_consumer_id, environment_id, priority) ";

        String selectStatement =  "SELECT cids.consumer_id, ep.env_id, ep.priority " +
            "FROM consumer_ids cids " +
            "CROSS JOIN env_priorities ep";

        // We are using 2 query parameters per environment
        int envQueryParamSize = 2 * envIds.size();
        int maxDbQueryParamLimit = Math.min(this.getInBlockSize(), this.getQueryParameterLimit());

        /**
         * It is possible that the number of environment query parameters could be larger than the minimum
         * value between the IN block size and the query parameter limitations. If this occurs, then we cannot
         * use any positional query parameters for our consumer UUIDs. This should not happen in practice, but
         * we should consider this case.
         */
        if (envQueryParamSize >= maxDbQueryParamLimit) {
            throw new IllegalStateException("The number of environment query parameters equals or exceeds " +
                "the number of allowed query parameters");
        }

        int consumerUuidBlockSize = maxDbQueryParamLimit - envQueryParamSize;

        Query query = null;
        for (List<String> block : this.partition(consumerUuidsSet, consumerUuidBlockSize)) {
            // Hibernate does not allow parameter expansion for positional parameters, so we must replace
            // the positional parameter IN statement based on the partition size. Only create or re-create the
            // query if we have not already created the query or if we are on the last partition and the
            // number of remaining consumers is smaller than the block size.
            if (query == null || block.size() != consumerUuidBlockSize) {
                String uuidInStatement = String.join(", ", Collections.nCopies(block.size(), "?"));
                String consumerIdsSubClause = "consumer_ids (consumer_id) AS (" +
                    "SELECT id FROM cp_consumer WHERE uuid IN (" + uuidInStatement + "))";

                String cteSQL = new StringBuilder()
                    .append(insertStatement)
                    .append(" ")
                    .append("WITH env_priorities(env_id, priority) AS (")
                    .append("VALUES ")
                    .append(String.join(", ", Collections.nCopies(envIds.size(), "(?, ?)")))
                    .append("), ")
                    .append(consumerIdsSubClause)
                    .append(selectStatement)
                    .toString();

                query = this.getEntityManager()
                    .createNativeQuery(cteSQL)
                    .unwrap(NativeQuery.class)
                    .addSynchronizedEntityClass(Consumer.class)
                    .addSynchronizedEntityClass(Environment.class);

                int parameterIndex = 0;
                for (String envId : envIds) {
                    query.setParameter(++parameterIndex, envId)
                        .setParameter(++parameterIndex, parameterIndex + 1);
                }
            }

            // We used 2 query parameters per environment ID
            int consumerParamIndex = envIds.size() * 2;
            for (String consumerUuid : block) {
                query.setParameter(++consumerParamIndex, consumerUuid);
            }

            query.executeUpdate();
        }

        return consumerUuidsSet.size();
    }

}
