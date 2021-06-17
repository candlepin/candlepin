/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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

import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import javax.inject.Singleton;
import javax.persistence.Query;



/**
 * CertificateSerialCurator - Interface to request a unique certificate serial number.
 */
@Singleton
public class CertificateSerialCurator extends AbstractHibernateCurator<CertificateSerial> {

    public CertificateSerialCurator() {
        super(CertificateSerial.class);
    }

    private Date getExpiryRestriction() {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

        // Set to midnight first
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MILLISECOND, 0);

        // Subtract a day to put us in "yesterday" relative to midnight UTC of whatever "today" is
        cal.add(Calendar.DAY_OF_MONTH, -1);
        return cal.getTime();
    }

    /**
     * Deletes all revoked cert serials that have expired.
     *
     * @return the total number of serials that were deleted.
     */
    @Transactional
    public int deleteRevokedExpiredSerials() {
        String hql = "DELETE FROM CertificateSerial c WHERE c.revoked = true" +
            " AND c.expiration < :cutoff";
        Query query = this.getEntityManager().createQuery(hql);
        query.setParameter("cutoff", getExpiryRestriction());
        return query.executeUpdate();
    }

    @SuppressWarnings("unchecked")
    public List<Long> listEntitlementSerialIds(Consumer c) {
        String hql = "SELECT s.id" +
            "    FROM EntitlementCertificate ec" +
            "     JOIN ec.entitlement e" +
            "     JOIN e.consumer c" +
            "     JOIN ec.serial s" +
            "     JOIN e.pool p" +
            "    WHERE" +
            "       c.id=:consumerId" +
            "    AND" +
            "       p.endDate >= :nowDate";

        Query query = this.getEntityManager().createQuery(hql);

        return (List<Long>) query
            .setParameter("consumerId", c.getId())
            .setParameter("nowDate", new Date())
            .getResultList();
    }


    /**
     * Returns all serial ids that are revoked but not expired.
     *
     * @return a list of serial ids
     */
    @SuppressWarnings("unchecked")
    public List<Long> listNonExpiredRevokedSerialIds() {
        String hql = "SELECT s.id" +
            "    FROM CertificateSerial s" +
            "    WHERE" +
            "       s.revoked=true" +
            "    AND" +
            "       s.expiration >= :nowDate";

        Query query = this.getEntityManager().createQuery(hql);

        return (List<Long>) query
            .setParameter("nowDate", new Date())
            .getResultList();
    }

    /**
     * Revokes serial specified by the given serial id
     *
     * @param serialToRevoke id of the serial to be revoked
     */
    @Transactional
    public void revokeById(Long serialToRevoke) {
        String query = "UPDATE CertificateSerial s" +
            " SET s.revoked = true, s.updated = :updated" +
            " WHERE s.revoked = false AND s.id = :serial_id";

        this.currentSession().createQuery(query)
            .setParameter("updated", new Date())
            .setParameter("serial_id", serialToRevoke)
            .executeUpdate();
    }

    /**
     * Revokes all serials specified by the given serial ids
     *
     * @param serialsToRevoke ids of the serials to be revoked
     * @return a number of revoked serials
     */
    @Transactional
    public int revokeByIds(Collection<Long> serialsToRevoke) {
        if (serialsToRevoke == null || serialsToRevoke.isEmpty()) {
            return 0;
        }

        String query = "UPDATE CertificateSerial s SET s.revoked = true" +
            " WHERE s.revoked = false AND s.id IN (:serials)";

        int updated = 0;
        for (Collection<Long> serialsToRevokeBlock : this.partition(serialsToRevoke)) {
            updated += this.currentSession().createQuery(query)
                .setParameter("serials", serialsToRevokeBlock)
                .executeUpdate();
        }

        return updated;
    }
}
