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

import org.candlepin.util.Util;

import com.google.common.collect.Iterables;
import com.google.inject.persist.Transactional;

import org.hibernate.Query;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;



/**
 * CertificateSerialCurator - Interface to request a unique certificate serial number.
 */
public class CertificateSerialCurator extends AbstractHibernateCurator<CertificateSerial> {

    private static int inClauseLimit = 1000;

    public CertificateSerialCurator() {
        super(CertificateSerial.class);
    }

    /**
     * @return list of certificate serials which are revoked but not yet collected
     * and put into CRL
     */
    @SuppressWarnings("unchecked")
    public CandlepinQuery<CertificateSerial> retrieveTobeCollectedSerials() {
        DetachedCriteria criteria = DetachedCriteria.forClass(CertificateSerial.class)
            .add(Restrictions.eq("revoked", true))
            .add(Restrictions.eq("collected", false));

        return this.cpQueryFactory.<CertificateSerial>buildQuery(this.currentSession(), criteria);
    }

    @SuppressWarnings("unchecked")
    public CandlepinQuery<CertificateSerial> getExpiredSerials() {
        //TODO - Should date fields be truncated when checking expiration?

        DetachedCriteria criteria = DetachedCriteria.forClass(CertificateSerial.class)
            .add(Restrictions.le("expiration", Util.yesterday()))
            .add(Restrictions.eq("revoked", true));

        return this.cpQueryFactory.<CertificateSerial>buildQuery(this.currentSession(), criteria);
    }

    /**
     * Delete expired serials.
     *
     * @return the number of rows deleted.
     */
    @Transactional
    public int deleteExpiredSerials() {
        // Some databases don't like to update based on a field that is being updated
        // So we must get expired ids, and then delete them
        @SuppressWarnings("unchecked")
        List<String> ids = this.currentSession()
            .createCriteria(CertificateSerial.class)
            .add(Restrictions.le("expiration", Util.yesterday()))
            .add(Restrictions.eq("revoked", true))
            .setProjection(Projections.id())
            .addOrder(Order.asc("id"))
            .list();

        if (ids.isEmpty()) {
            return 0;
        }

        String hql = "DELETE from CertificateSerial WHERE id IN (:expiredIds)";
        Query query = this.currentSession().createQuery(hql);

        int removed = 0;

        for (List<String> block : Iterables.partition(ids, AbstractHibernateCurator.IN_OPERATOR_BLOCK_SIZE)) {
            removed += query.setParameterList("expiredIds", block).executeUpdate();
        }

        return removed;
    }

    @SuppressWarnings("unchecked")
    public CandlepinQuery<CertificateSerial> listBySerialIds(String[] ids) {
        if (ids == null) {
            return null;
        }

        // convert IDs to Longs for the query
        Long[] lids = new Long[ids.length];
        for (int i = 0; i < ids.length; i++) {
            lids[i] = Long.valueOf(ids[i]);
        }

        DetachedCriteria criteria = DetachedCriteria.forClass(CertificateSerial.class)
            .add(CPRestrictions.in("id", lids));

        return this.cpQueryFactory.<CertificateSerial>buildQuery(this.currentSession(), criteria);
    }

    /*
     * This method is really not necessary, but is probably the cleanest way to
     * unit test.
     */
    public Collection<CertificateSerial> saveOrUpdateAll(Map<String, CertificateSerial> serialMap) {
        return this.saveOrUpdateAll(serialMap.values(), false, false);
    }

    @SuppressWarnings("unchecked")
    public List<Long> listEntitlementSerialIds(Consumer c) {
        List<Long> resultList = null;
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
        javax.persistence.Query query = this.getEntityManager().createQuery(hql);

        resultList = (List<Long>) query
            .setParameter("consumerId", c.getId())
            .setParameter("nowDate", new Date())
            .getResultList();
        return resultList;
    }
}
