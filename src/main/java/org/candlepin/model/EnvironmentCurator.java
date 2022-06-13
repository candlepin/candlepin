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

import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Restrictions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Singleton;
import javax.persistence.NoResultException;
import javax.persistence.Query;


@Singleton
public class EnvironmentCurator extends AbstractHibernateCurator<Environment> {
    private static final Logger log = LoggerFactory.getLogger(EnvironmentCurator.class);

    public EnvironmentCurator() {
        super(Environment.class);
    }

    /**
     * Fetches the environment for the specified consumer. If the consumer does not have a defined
     * environment ID, this method returns null. If the consumer has an invalid environment ID,
     * this method throws an exception.
     *
     * @param consumer
     *  The consumer for which to fetch an Environment object
     *
     * @throws IllegalArgumentException
     *  if consumer is null
     *
     * @throws IllegalStateException
     *  if the consumer's defined environment ID is invalid
     *
     * @return
     *  An Environment instance for the specified consumer, or null if the consumer does not have a
     *  defined environment ID
     */
    public Environment getConsumerEnvironment(Consumer consumer) {
        if (consumer == null) {
            throw new IllegalArgumentException("consumer is null");
        }

        Environment environment = null;

        if (consumer.getEnvironmentId() != null) {
            environment = this.get(consumer.getEnvironmentId());

            if (environment == null) {
                throw new IllegalStateException(
                    "consumer is not associated with a valid environment: " + consumer);
            }
        }

        return environment;
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

    @SuppressWarnings("unchecked")
    public List<Environment> listWithContent(Set<String> contentIds) {
        return currentSession().createCriteria(Environment.class)
            .createCriteria("environmentContent")
            .createCriteria("content")
            .add(Restrictions.in("id", contentIds))
            .list();
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

}
