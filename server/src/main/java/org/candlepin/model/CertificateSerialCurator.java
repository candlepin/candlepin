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

import org.hibernate.Query;
import org.hibernate.criterion.Conjunction;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.criterion.Subqueries;

import java.util.Collection;
import java.util.List;
import java.util.Map;


/**
 * CertificateSerialCurator - Interface to request a unique certificate serial number.
 */
public class CertificateSerialCurator extends AbstractHibernateCurator<CertificateSerial> {

    private static int inClauseLimit = 1000;

    @SuppressWarnings("rawtypes")
    private static final Class[] CERTCLASSES = {IdentityCertificate.class,
        EntitlementCertificate.class, SubscriptionsCertificate.class, CdnCertificate.class};

    public CertificateSerialCurator() {
        super(CertificateSerial.class);
    }

    /**
     * @return list of certificate serials which are revoked but not yet collected
     * and put into CRL
     */
    @SuppressWarnings("unchecked")
    public List<CertificateSerial> retrieveTobeCollectedSerials() {
        return this.currentSession().createCriteria(CertificateSerial.class)
            .add(getRevokedCriteria())
            .add(Restrictions.eq("collected", false)).list();
    }

    @SuppressWarnings("unchecked")
    public List<CertificateSerial> getExpiredSerials() {
        //TODO - Should date fields be truncated when checking expiration?
        return this.currentSession()
            .createCriteria(CertificateSerial.class)
            .add(Restrictions.le("expiration", Util.yesterday()))
            .add(getRevokedCriteria()).list();
    }

    /**
     * Delete expired serials.
     *
     * @return the number of rows deleted.
     */
    public int deleteExpiredSerials() {
        // Some databases don't like to update based on a field that is being updated
        // So we must get expired ids, and then delete them
        @SuppressWarnings("unchecked")
        List<String> ids = this.currentSession()
            .createCriteria(CertificateSerial.class)
            .add(Restrictions.le("expiration", Util.yesterday()))
            .add(getRevokedCriteria())
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
    public List<CertificateSerial> listBySerialIds(String[] ids) {
        if (ids == null) {
            return null;
        }

        // convert IDs to Longs for the query
        Long[] lids = new Long[ids.length];
        for (int i = 0; i < ids.length; i++) {
            lids[i] = Long.valueOf(ids[i]);
        }

        return currentSession().createCriteria(
            CertificateSerial.class).add(Restrictions.in("id", lids)).list();
    }

    /*
     * Generates criteria to check that no certificates (of any type in
     * CertificateSerialCurator.CERTCLASSES) reference a serial so we can consider
     * it revoked
     */
    @SuppressWarnings("rawtypes")
    private Criterion getRevokedCriteria() {
        Conjunction crit = Restrictions.conjunction();
        for (Class clazz : CERTCLASSES) {
            DetachedCriteria certSerialQuery = DetachedCriteria.forClass(clazz)
                .createCriteria("serial")
                .setProjection(Projections.property("id"));
            crit.add(Subqueries.propertyNotIn("id", certSerialQuery));
        }
        return crit;
    }

    /*
     * This method is really not necessary, but is probably the cleanest way to
     * unit test.
     */
    public Collection<CertificateSerial> saveOrUpdateAll(Map<String, CertificateSerial> serialMap) {
        return this.saveOrUpdateAll(serialMap.values(), false);
    }
}
