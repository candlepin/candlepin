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

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import java.util.Collection;
import java.util.Date;
import java.util.List;

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
     *
     * @return a list of expired certificates
     */
    @SuppressWarnings("unchecked")
    public List<ExpiredCertificate> listAllExpired() {
        String hql = "SELECT new org.candlepin.model.ExpiredCertificate(c.id, s.id)" +
            " FROM IdentityCertificate c" +
            " INNER JOIN c.serial s" +
            " INNER JOIN Consumer con on con.idCert = c.id" +
            " WHERE s.expiration < :nowDate";

        Query query = this.getEntityManager().createQuery(hql, ExpiredCertificate.class);

        return (List<ExpiredCertificate>) query
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
            deleted += this.currentSession().createQuery(query)
                .setParameter("idsToDelete", idsToDeleteBlock)
                .executeUpdate();
        }

        return deleted;
    }

}
