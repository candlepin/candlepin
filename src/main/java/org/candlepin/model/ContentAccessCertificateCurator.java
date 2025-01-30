/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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

import com.google.common.collect.Iterables;
import com.google.inject.persist.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.NoResultException;
import javax.persistence.Query;


@Singleton
public class ContentAccessCertificateCurator extends AbstractHibernateCurator<SCACertificate> {

    private static final Logger log = LoggerFactory.getLogger(ContentAccessCertificateCurator.class);

    @Inject
    public ContentAccessCertificateCurator() {
        super(SCACertificate.class);
    }

    /**
     * Retrieves the {@link SCACertificate} for the provided consumer.
     *
     * @param consumer
     *  the consumer to retrieve a SCA certificate for
     *
     * @return the {@link SCACertificate} for the provided consumer, or null if one does not exist
     */
    public SCACertificate getForConsumer(Consumer consumer) {
        log.debug("Retrieving content access certificate for consumer: {}",
            consumer == null ? "null" : consumer.getId());
        if (consumer == null || consumer.getId() == null || consumer.getId().isBlank()) {
            return null;
        }

        String query = "SELECT c FROM SCACertificate c WHERE c.consumer.id = :id";

        try {
            return this.entityManager.get()
                .createQuery(query, SCACertificate.class)
                .setParameter("id", consumer.getId())
                .getSingleResult();
        }
        catch (NoResultException e) {
            return null;
        }
    }

    /**
     * Delete SCA certs of all consumers that belong to the given org.
     *
     * @return the number of rows deleted.
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public int deleteForOwner(Owner owner) {
        if (owner == null || owner.getKey() == null || owner.getKey().isBlank()) {
            return 0;
        }
        // So we must get ids for this owner, and then delete them
        String hql = " SELECT cac.id, s.id " +
            "          FROM Consumer c, Owner o" +
            "              JOIN c.contentAccessCert cac" +
            "              JOIN  cac .serial s" +
            "          WHERE o.key=:ownerkey" +
            "                and o.id = c.ownerId";
        Query query = this.getEntityManager().createQuery(hql);
        List<Object[]> rows = query.setParameter("ownerkey", owner.getKey()).getResultList();

        return deleteCerts(rows);
    }

    private int deleteCerts(List<Object[]> rows) {
        Set<String> certsToDelete = new HashSet<>();
        Set<Long> certSerialsToRevoke = new HashSet<>();
        for (Object[] row : rows) {
            if (row[0] != null) {
                certsToDelete.add((String) row[0]);
            }

            if (row[1] != null) {
                certSerialsToRevoke.add((Long) row[1]);
            }
        }

        // First ensure that we've marked all of the certificate serials as revoked.
        // Normally we would let the @PreRemove on CertificateSertial do this for us
        // when the certificate record is deleted, but since there's a potential for
        // a lot of certificates to exist for an Owner, we'll batch these updates.
        log.debug("Marked {} certificate serials as revoked.", revokeCertificateSerials(certSerialsToRevoke));

        int removed = deleteContentAccessCerts(certsToDelete);
        log.debug("Deleted {} content access certificates.", removed);
        return removed;
    }

    /**
     * Mark the specified content access certificate serials as revoked.
     *
     * @param serialIdsToRevoke the ids of the serials to mark as revoked.
     * @return the number of serials that were marked as revoked.
     */
    private int revokeCertificateSerials(Set<Long> serialIdsToRevoke) {
        String revokeHql = "UPDATE CertificateSerial SET revoked = true WHERE id IN (:serialsToRevoke)";
        Query revokeQuery = this.getEntityManager().createQuery(revokeHql);
        int revokedCount = 0;
        for (List<Long> block : Iterables.partition(serialIdsToRevoke, getInBlockSize())) {
            revokedCount += revokeQuery.setParameter("serialsToRevoke", block).executeUpdate();
        }
        return revokedCount;
    }

    private int deleteContentAccessCerts(Set<String> certIdsToDelete) {
        String hql = "DELETE from SCACertificate WHERE id IN (:certsToDelete)";
        Query query = this.getEntityManager().createQuery(hql);

        String hql2 = "UPDATE Consumer set contentAccessCert = null WHERE " +
            "contentAccessCert.id IN (:certsToDelete)";
        Query query2 = this.getEntityManager().createQuery(hql2);

        int removed = 0;
        for (List<String> block : Iterables.partition(certIdsToDelete, getInBlockSize())) {
            query2.setParameter("certsToDelete", block).executeUpdate();
            removed += query.setParameter("certsToDelete", block).executeUpdate();
        }
        return removed;
    }

    /**
     * Lists all expired content access certificates that are not revoked.
     *
     * @return a list of expired certificates
     */
    @SuppressWarnings("unchecked")
    public List<CertSerial> listAllExpired() {
        String hql = "SELECT new org.candlepin.model.CertSerial(c.id, s.id)" +
            " FROM SCACertificate c" +
            " INNER JOIN c.serial s " +
            " WHERE s.expiration < :nowDate";

        Query query = this.getEntityManager().createQuery(hql, CertSerial.class);

        return (List<CertSerial>) query
            .setParameter("nowDate", new Date())
            .getResultList();
    }

    /**
     * Deletes content access certificates with the given ids
     *
     * @param idsToDelete ids to be deleted
     * @return a number of deleted certificates
     */
    @Transactional
    public int deleteByIds(Collection<String> idsToDelete) {
        if (idsToDelete == null || idsToDelete.isEmpty()) {
            return 0;
        }

        String query = "DELETE FROM SCACertificate c WHERE c.id IN (:idsToDelete)";

        int deleted = 0;
        for (Collection<String> idsToDeleteBlock : this.partition(idsToDelete)) {
            deleted += this.getEntityManager().createQuery(query)
                .setParameter("idsToDelete", idsToDeleteBlock)
                .executeUpdate();
        }

        return deleted;
    }

    /**
     * Takes a list of consumer ids and lists certificate serials of their content access certificates.
     *
     * @param consumerIds consumers to list serials for
     * @return a list of certificate serials
     */
    public List<CertSerial> listCertSerials(Collection<String> consumerIds) {
        if (consumerIds == null || consumerIds.isEmpty()) {
            return new ArrayList<>();
        }

        String hql = """
            SELECT new org.candlepin.model.CertSerial(c.contentAccessCert.id, c.contentAccessCert.serial.id)
            FROM Consumer c WHERE id IN (:consumerIds)""";
        Query query = entityManager.get().createQuery(hql, CertSerial.class);

        List<CertSerial> serials = new ArrayList<>(consumerIds.size());
        for (Collection<String> idBlock : this.partition(consumerIds)) {
            serials.addAll(query.setParameter("consumerIds", idBlock).getResultList());
        }

        return serials;
    }

    /**
     * Deletes the content access certificates for the provided consumers and also revokes the certificate
     * serials.
     *
     * @param consumerUuids
     *  the UUIDs of {@link Consumer}s to delete content access certificates for
     *
     * @return the number of content access certificates that were deleted
     */
    public int deleteForConsumers(Collection<String> consumerUuids) {
        if (consumerUuids == null || consumerUuids.isEmpty()) {
            return 0;
        }

        String hql = """
            SELECT cac.id, s.id
            FROM Consumer c
                JOIN c.contentAccessCert cac
                JOIN cac.serial s
            WHERE c.uuid IN (:uuids)
            """;

        Query query = this.getEntityManager()
            .createQuery(hql);

        List<Object[]> rows = new ArrayList<>();
        for (List<String> block : this.partition(consumerUuids)) {
            List<Object[]> result = query
                .setParameter("uuids", block)
                .getResultList();

            rows.addAll(result);
        }

        return deleteCerts(rows);
    }
}
