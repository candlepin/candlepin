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

import org.hibernate.query.NativeQuery;

import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import javax.inject.Singleton;
import javax.persistence.EntityManager;
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
     * Deletes all cert serials that are both revoked AND expired, and are NOT referenced by any table.
     *
     * @return the total number of serials that were deleted.
     */
    @Transactional
    public int deleteRevokedExpiredSerials() {
        EntityManager entityManager = this.getEntityManager();

        // Impl. note: Under normal circumstances, we should not have to worry about deleting revoked
        // serials being referenced in any certificate table, because the corresponding certificate is
        // always deleted when the serial is revoked. However, this avoids bugs such as 2229095, where bad
        // data caused by accident/bug could make this query fail.
        //
        // Also, there doesn't seem to be a way to do this kind of query with JQPL, so we're using native
        // SQL, and MariaDB does not like table aliases in delete statements, so we have to do this
        // in 2 queries (select, then delete).
        String fetchSQL = "SELECT cs.id FROM cp_cert_serial cs " +
            "LEFT JOIN cp_certificate subc ON cs.id = subc.serial_id " +
            "LEFT JOIN cp_ent_certificate entc ON cs.id = entc.serial_id " +
            "LEFT JOIN cp_cdn_certificate cdnc ON cs.id = cdnc.serial_id " +
            "LEFT JOIN cp_id_cert idc ON cs.id = idc.serial_id " +
            "LEFT JOIN cp_cont_access_cert scac ON cs.id = scac.serial_id " +
            "LEFT JOIN cp_anonymous_certificates anonc ON cs.id = anonc.serial_id " +
            "LEFT JOIN cp_ueber_cert uebc ON cs.id = uebc.serial_id " +
            "WHERE cs.revoked = true " +
            "AND cs.expiration < :expiration " +
            "AND subc.serial_id IS NULL " +
            "AND entc.serial_id IS NULL " +
            "AND cdnc.serial_id IS NULL " +
            "AND idc.serial_id IS NULL " +
            "AND scac.serial_id IS NULL " +
            "AND anonc.serial_id IS NULL " +
            "AND uebc.serial_id IS NULL;";

        List<Long> serials = entityManager.createNativeQuery(fetchSQL)
            .setParameter("expiration", getExpiryRestriction())
            .unwrap(NativeQuery.class)
            .addSynchronizedEntityClass(CertificateSerial.class)
            .addSynchronizedEntityClass(SubscriptionsCertificate.class)
            .addSynchronizedEntityClass(EntitlementCertificate.class)
            .addSynchronizedEntityClass(CdnCertificate.class)
            .addSynchronizedEntityClass(IdentityCertificate.class)
            .addSynchronizedEntityClass(SCACertificate.class)
            .addSynchronizedEntityClass(AnonymousContentAccessCertificate.class)
            .addSynchronizedEntityClass(UeberCertificate.class)
            .getResultList();

        String deleteSQL = "DELETE FROM cp_cert_serial WHERE id IN (:serials_to_delete)";
        int deleted = 0;

        Query deleteQuery = entityManager.createNativeQuery(deleteSQL)
            .unwrap(NativeQuery.class)
            .addSynchronizedEntityClass(CertificateSerial.class);

        for (List<Long> serialsToDeleteBlock : this.partition(serials)) {
            deleted += deleteQuery.setParameter("serials_to_delete", serialsToDeleteBlock)
                .executeUpdate();
        }

        return deleted;
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

        this.getEntityManager().createQuery(query)
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
            updated += this.getEntityManager().createQuery(query)
                .setParameter("serials", serialsToRevokeBlock)
                .executeUpdate();
        }

        return updated;
    }

}
