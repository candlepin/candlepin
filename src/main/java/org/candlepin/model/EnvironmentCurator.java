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

import org.hibernate.annotations.QueryHints;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Restrictions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.Query;



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

    public CandlepinQuery<Environment> listForOwner(Owner o) {
        DetachedCriteria criteria = this.createSecureDetachedCriteria()
            .add(Restrictions.eq("owner", o));

        return this.cpQueryFactory.buildQuery(this.currentSession(), criteria);
    }

    public CandlepinQuery<Environment> listForOwnerByName(Owner o, String envName) {
        DetachedCriteria criteria = this.createSecureDetachedCriteria()
            .add(Restrictions.eq("owner", o))
            .add(Restrictions.eq("name", envName));

        return this.cpQueryFactory.buildQuery(this.currentSession(), criteria);
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

    public Map<String, List<String>> findEnvironmentsOf(List<String> consumerIds) {
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

            List<String> nativeSpaces = List.of(
                Environment.class.getName(), EnvironmentContent.class.getName());

            Query query = entityManager.createNativeQuery(sql)
                .setHint(QueryHints.NATIVE_SPACES, nativeSpaces);

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

}
