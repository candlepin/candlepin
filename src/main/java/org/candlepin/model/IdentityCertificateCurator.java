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

import com.google.inject.persist.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.Query;

/**
 * IdentityCertificateCurator
 */
@Singleton
public class IdentityCertificateCurator extends AbstractHibernateCurator<IdentityCertificate> {

    @Inject
    public IdentityCertificateCurator() {
        super(IdentityCertificate.class);
    }

    /**
     * Lists all expired identity certificates that are not revoked.
     *  Upstream consumer certificates are not retrieved.
     *  Manifest consumers are also excluded.
     *
     * @return a list of expired certificates
     */
    @SuppressWarnings("unchecked")
    public List<CertSerial> listAllExpired() {
        String hql = "SELECT new org.candlepin.model.CertSerial(c.id, s.id)" +
            " FROM IdentityCertificate c" +
            " INNER JOIN c.serial s" +
            " INNER JOIN Consumer con on con.idCert = c.id" +
            " INNER JOIN ConsumerType type on con.typeId = type.id" +
            " WHERE s.expiration < :nowDate" +
            " AND type.manifest <> true";

        Query query = this.getEntityManager().createQuery(hql, CertSerial.class);

        return (List<CertSerial>) query
            .setParameter("nowDate", new Date())
            .getResultList();
    }

    /**
     * Deletes identity certificates belonging to the given ids
     *
     * @param idsToDelete ids to be deleted
     * @return a number of deleted certificates
     */
    @Transactional
    public int deleteByIds(Collection<String> idsToDelete) {
        if (idsToDelete == null || idsToDelete.isEmpty()) {
            return 0;
        }

        String query = "DELETE FROM IdentityCertificate c WHERE c.id IN (:idsToDelete)";

        int deleted = 0;
        for (Collection<String> idsToDeleteBlock : this.partition(idsToDelete)) {
            deleted += this.getEntityManager().createQuery(query)
                .setParameter("idsToDelete", idsToDeleteBlock)
                .executeUpdate();
        }

        return deleted;
    }

    /**
     * Takes a list of consumer ids and lists certificate serials of their identity certificates.
     *
     * @param consumerIds consumers to list serials for
     * @return a list of certificate serials
     */
    public List<CertSerial> listCertSerials(Collection<String> consumerIds) {
        if (consumerIds == null || consumerIds.isEmpty()) {
            return new ArrayList<>();
        }

        String hql = """
            SELECT new org.candlepin.model.CertSerial(c.idCert.id, c.idCert.serial.id)
            FROM Consumer c WHERE id IN (:consumerIds)""";
        Query query = entityManager.get().createQuery(hql, CertSerial.class);

        List<CertSerial> serials = new ArrayList<>(consumerIds.size());
        for (Collection<String> idBlock : this.partition(consumerIds)) {
            serials.addAll(query.setParameter("consumerIds", idBlock).getResultList());
        }

        return serials;
    }

}
