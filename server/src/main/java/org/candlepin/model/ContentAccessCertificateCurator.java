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

import com.google.common.collect.Iterables;

import org.hibernate.criterion.Restrictions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.Query;



/**
 * ContentAccessCertificateCurator
 */
@Component
public class ContentAccessCertificateCurator extends AbstractHibernateCurator<ContentAccessCertificate> {

    private static Logger log = LoggerFactory.getLogger(ContentAccessCertificateCurator.class);

    @Autowired
    public ContentAccessCertificateCurator() {
        super(ContentAccessCertificate.class);
    }

    @SuppressWarnings("unchecked")
    @Transactional
    public ContentAccessCertificate getForConsumer(Consumer c) {
        return (ContentAccessCertificate) currentSession().createCriteria(ContentAccessCertificate.class)
            .add(Restrictions.eq("consumer", c))
            .uniqueResult();
    }

    /**
     * Delete unneeded content access certs.
     *
     * @return the number of rows deleted.
     */
    @Transactional
    public int deleteForOwner(Owner owner) {
        // So we must get ids for this owner, and then delete them
        @SuppressWarnings("unchecked")

        String hql = " SELECT cac.id, s.id " +
            "          FROM Consumer c, Owner o" +
            "              JOIN c.contentAccessCert cac" +
            "              JOIN  cac .serial s" +
            "          WHERE o.key=:ownerkey" +
            "                and o.id = c.ownerId";
        Query query = this.getEntityManager().createQuery(hql);
        List<Object[]> rows = query.setParameter("ownerkey", owner.getKey()).getResultList();

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
        String hql = "DELETE from ContentAccessCertificate WHERE id IN (:certsToDelete)";
        Query query = this.getEntityManager().createQuery(hql);

        String hql2 = "UPDATE Consumer set contentAccessCert = null WHERE " +
            "contentAccessCert.id IN (:certsToDelete)";
        Query query2 = this.getEntityManager().createQuery(hql2);

        int removed = 0;
        for (List<String> block : Iterables.partition(certIdsToDelete, getInBlockSize())) {
            String param = block.toString();
            query2.setParameter("certsToDelete", block).executeUpdate();
            removed += query.setParameter("certsToDelete", block).executeUpdate();
        }
        return removed;
    }
}
