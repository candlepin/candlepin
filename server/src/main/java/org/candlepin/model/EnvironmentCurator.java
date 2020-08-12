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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.NoResultException;


/**
 * EnvironmentCurator
 */
@Component
@Transactional
public class EnvironmentCurator extends AbstractHibernateCurator<Environment> {
    private static Logger log = LoggerFactory.getLogger(OwnerContentCurator.class);

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
    public CandlepinQuery<Consumer> getEnvironmentConsumers(Environment environment) {
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
    public CandlepinQuery<Consumer> getEnvironmentConsumers(String environmentId) {
        DetachedCriteria criteria = DetachedCriteria.forClass(Consumer.class)
            .add(Restrictions.eq("environmentId", environmentId));

        return this.cpQueryFactory.<Consumer>buildQuery(this.currentSession(), criteria);
    }

    @SuppressWarnings("unchecked")
    public CandlepinQuery<Environment> listForOwner(Owner o) {
        DetachedCriteria criteria = this.createSecureDetachedCriteria()
            .add(Restrictions.eq("owner", o));

        return this.cpQueryFactory.<Environment>buildQuery(this.currentSession(), criteria);
    }

    @SuppressWarnings("unchecked")
    public CandlepinQuery<Environment> listForOwnerByName(Owner o, String envName) {
        DetachedCriteria criteria = this.createSecureDetachedCriteria()
            .add(Restrictions.eq("owner", o))
            .add(Restrictions.eq("name", envName));

        return this.cpQueryFactory.<Environment>buildQuery(this.currentSession(), criteria);
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
        String jpql = "SELECT e.id FROM Environment e WHERE e.owner.id = :owner_id";

        List<String> ids = this.getEntityManager()
            .createQuery(jpql, String.class)
            .setParameter("owner_id", owner.getId())
            .getResultList();

        int count = 0;

        if (ids != null && !ids.isEmpty()) {
            // We have some matching content, delete environment content first...
            Map<String, Object> criteria = new HashMap<>();
            criteria.put("environment_id", owner.getId());

            count = this.bulkSQLDelete(EnvironmentContent.DB_TABLE, criteria);
            log.info("{} environment-content relations updated", count);

            // Cleanup the environment objects themselves...
            criteria.clear();
            criteria.put("id", ids);

            count = this.bulkSQLDelete(Environment.DB_TABLE, criteria);
            log.info("{} environments deleted", count);
        }
        else {
            log.info("0 environments deleted");
        }

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
            catch (NoResultException exp) {
                log.trace("Unable to find environment Id by envName {}", envName);
            }
        }

        return envId;
    }
}
