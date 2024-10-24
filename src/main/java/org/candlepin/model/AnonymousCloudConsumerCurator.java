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


import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import javax.inject.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

@Singleton
public class AnonymousCloudConsumerCurator extends AbstractHibernateCurator<AnonymousCloudConsumer> {

    public AnonymousCloudConsumerCurator() {
        super(AnonymousCloudConsumer.class);
    }

    /**
     * Retrieves an anonymous cloud consumer by the UUID.
     *
     * @param uuid
     *     the UUID that corresponds to an anonymous consumer
     *
     * @return the anonymous consumer based on the provided UUID, or null if no matching anonymous cloud
     * consumer is found
     */
    public AnonymousCloudConsumer getByUuid(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return null;
        }

        EntityManager em = getEntityManager();
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<AnonymousCloudConsumer> cq = cb.createQuery(AnonymousCloudConsumer.class);
        Root<AnonymousCloudConsumer> root = cq.from(AnonymousCloudConsumer.class);

        Predicate uuidPredicate = cb.equal(root.get("uuid"), uuid);
        Predicate securityPredicate = getSecurityPredicate(AnonymousCloudConsumer.class, cb, root);

        if (securityPredicate != null) {
            cq.where(cb.and(uuidPredicate, securityPredicate));
        }
        else {
            cq.where(uuidPredicate);
        }

        try {
            return em.createQuery(cq)
                .getSingleResult();
        }
        catch (NoResultException e) {
            return null;
        }
    }

    /**
     * Retrieves anonymous cloud consumers based on the provided UUIDs.
     *
     * @param uuids
     *  the UUIDs that corresponds to anonymous consumers
     *
     * @return the anonymous consumers based on the provided UUIDs, or empty list if none are found
     */
    public List<AnonymousCloudConsumer> getByUuids(Collection<String> uuids) {
        List<AnonymousCloudConsumer> consumers = new ArrayList<>();
        if (uuids == null || uuids.isEmpty()) {
            return consumers;
        }

        EntityManager em = getEntityManager();
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<AnonymousCloudConsumer> cq = cb.createQuery(AnonymousCloudConsumer.class);
        Root<AnonymousCloudConsumer> root = cq.from(AnonymousCloudConsumer.class);

        Predicate securityPredicate = getSecurityPredicate(AnonymousCloudConsumer.class, cb, root);

        for (List<String> block : this.partition(uuids)) {

            Predicate uuidPredicate = root.get("uuid").in(block);

            if (securityPredicate != null) {
                cq.where(cb.and(uuidPredicate, securityPredicate));
            }
            else {
                cq.where(uuidPredicate);
            }

            consumers.addAll(em.createQuery(cq).getResultList());
        }

        return consumers;
    }

    /**
     * Retrieves an anonymous cloud consumer using the provided cloud instance ID.
     *
     * @param instanceId
     *     the ID of the cloud instance that used to retrieve an anonymous cloud consumer
     *
     * @return the anonymous consumer based on the cloud instance ID, or null if no matching anonymous
     * cloud consumer is found
     */
    public AnonymousCloudConsumer getByCloudInstanceId(String instanceId) {
        if (instanceId == null || instanceId.isBlank()) {
            return null;
        }

        EntityManager em = getEntityManager();
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<AnonymousCloudConsumer> cq = cb.createQuery(AnonymousCloudConsumer.class);
        Root<AnonymousCloudConsumer> root = cq.from(AnonymousCloudConsumer.class);

        Predicate instanceIdPredicate = cb.equal(root.get("cloudInstanceId"), instanceId);
        Predicate securityPredicate = getSecurityPredicate(AnonymousCloudConsumer.class, cb, root);

        if (securityPredicate != null) {
            cq.where(cb.and(instanceIdPredicate, securityPredicate));
        }
        else {
            cq.where(instanceIdPredicate);
        }

        try {
            return em.createQuery(cq)
                .getSingleResult();
        }
        catch (NoResultException e) {
            return null;
        }
    }

    /**
     * Retrieves an anonymous cloud consumer using the provided cloud instance ID.
     *
     * @param accountId
     *     the ID of the cloud instance that used to retrieve an anonymous cloud consumer
     *
     * @return the anonymous consumer based on the cloud instance ID, or null if no matching anonymous
     * cloud consumer is found
     */
    public List<AnonymousCloudConsumer> getByCloudAccountId(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return new ArrayList<>();
        }

        String query = "SELECT c FROM AnonymousCloudConsumer c WHERE c.cloudAccountId =:accountId";
        return this.getEntityManager().createQuery(query, AnonymousCloudConsumer.class)
            .setParameter("accountId", accountId)
            .getResultList();
    }

    /**
     * Takes a list of anonymous content access certificate ids and unlinks them from anonymous consumers.
     *
     * @param certIds
     *     certificate ids to be unlinked
     * @return the number of unlinked anonymous consumers
     */
    public int unlinkAnonymousCertificates(Collection<String> certIds) {
        if (certIds == null || certIds.isEmpty()) {
            return 0;
        }

        String query = "UPDATE AnonymousCloudConsumer c" +
            " SET c.contentAccessCert = NULL, c.updated = :date" +
            " WHERE c.contentAccessCert.id IN (:cert_ids)";

        int updated = 0;
        Date updateTime = new Date();
        for (Collection<String> certIdBlock : this.partition(certIds)) {
            updated += this.getEntityManager().createQuery(query)
                .setParameter("date", updateTime)
                .setParameter("cert_ids", certIdBlock)
                .executeUpdate();
        }

        return updated;
    }
}
