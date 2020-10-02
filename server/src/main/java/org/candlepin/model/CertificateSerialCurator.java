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

import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;

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

    /**
     * Fetches a collection of serials from uncollected, revoked and not expired
     * certficiate serials. If there are no such certificate serials, this method
     * returns an empty collection.
     *
     * @return
     *  a collection of serials from uncollected, revoked certificate serials that have not expired.
     */
    public CandlepinQuery<Long> getUncollectedRevokedCertSerials() {
        DetachedCriteria criteria = DetachedCriteria.forClass(CertificateSerial.class)
            .add(Restrictions.gt("expiration", getExpiryRestriction()))
            .add(Restrictions.eq("revoked", true))
            .add(Restrictions.eq("collected", false))
            .setProjection(Projections.id()); // Note: the ID *is* the serial for cert serials

        return this.cpQueryFactory.<Long>buildQuery(this.currentSession(), criteria);
    }

    /**
     * Fetches a collection of serials from revoked certficiate serials that expired prior to
     * midnight, yesterday in UTC. If there are no such certificate serials, this method returns an
     * empty collection.
     *
     * @return
     *  a collection of serials from revoked certficiate serials that expired prior to "today."
     */
    public CandlepinQuery<Long> getExpiredRevokedCertSerials() {
        return this.getExpiredRevokedCertSerials(getExpiryRestriction());
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
     * Fetches a collection of serials from revoked certificate serials that expired prior to the
     * specified cutoff date. If there are no such certificate serials, this method returns an empty
     * collection.
     *
     * @param cutoff
     *  The cutoff date to use for considering certificate serials "expired"
     *
     * @throws IllegalArgumentException
     *  if the cutoff date is null
     *
     * @return
     *  a collection of serials from revoked certficiate serials that expired prior to the specified
     *  cutoff date
     */
    public CandlepinQuery<Long> getExpiredRevokedCertSerials(Date cutoff) {
        if (cutoff == null) {
            throw new IllegalArgumentException("cutoff is null");
        }

        DetachedCriteria criteria = DetachedCriteria.forClass(CertificateSerial.class)
            .add(Restrictions.lt("expiration", cutoff))
            .add(Restrictions.eq("revoked", true))
            .setProjection(Projections.id()); // Note: the ID *is* the serial for cert serials

        return this.cpQueryFactory.<Long>buildQuery(this.currentSession(), criteria);
    }

    /**
     * Marks the specified serials as collected.
     *
     * @param serials
     *  A collection of serials to mark as collected
     *
     * @return
     *  the number of certificate serials updated
     */
    @Transactional
    public int markSerialsAsCollected(Collection<Long> serials) {
        int updated = 0;

        if (serials != null && !serials.isEmpty()) {
            // Impl note: the ID *is* the serial for cert serials. If this changes in the future, this query
            // should change, not the input.
            String hql = "UPDATE CertificateSerial cs SET collected = true WHERE id IN (:serials)";
            Query query = this.getEntityManager().createQuery(hql);

            for (Collection<Long> block : this.partition(serials)) {
                updated += query.setParameter("serials", block).executeUpdate();
            }
        }

        return updated;
    }

    /**
     * Deletes the certificate serials with the specified serials.
     *
     * @param serials
     *  A collection of serials to delete
     *
     * @return
     *  the number of certificate serials deleted
     */
    @Transactional
    public int deleteSerials(Collection<Long> serials) {
        int deleted = 0;

        if (serials != null && !serials.isEmpty()) {
            // Impl note: the ID *is* the serial for cert serials. If this changes in the future, this query
            // should change, not the input.
            String hql = "DELETE from CertificateSerial WHERE id IN (:serials)";
            Query query = this.getEntityManager().createQuery(hql);

            for (Collection<Long> block : this.partition(serials)) {
                deleted += query.setParameter("serials", block).executeUpdate();
            }
        }

        return deleted;
    }

    /**
     * Deletes all cert serials that have expired before they have been collected.
     *
     * @return the total number of serials that were deleted.
     */
    @Transactional
    public int deleteRevokedExpiredAndNotCollectedSerials() {
        String hql = "DELETE FROM CertificateSerial c WHERE c.revoked=true AND " +
            "c.collected=false AND expiration < :cutoff";
        Query query = this.getEntityManager().createQuery(hql);
        query.setParameter("cutoff", getExpiryRestriction());
        return query.executeUpdate();
    }

    public CandlepinQuery<CertificateSerial> listBySerialIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }

        Long[] lids = ids.stream()
            .map(Long::valueOf)
            .toArray(Long[]::new);

        DetachedCriteria criteria = DetachedCriteria.forClass(CertificateSerial.class)
            .add(CPRestrictions.in("id", lids));

        return this.cpQueryFactory.buildQuery(this.currentSession(), criteria);
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
}
