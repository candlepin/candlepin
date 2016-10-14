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
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.hibernate.criterion.Restrictions;

import java.util.List;

import javax.persistence.Query;



/**
 * ContentAccessCertificateCurator
 */
public class ContentAccessCertificateCurator extends AbstractHibernateCurator<ContentAccessCertificate> {

    @Inject
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

        String hql = " SELECT cac.id " +
            "    FROM Consumer c" +
            "       JOIN c.owner o" +
            "       JOIN c.contentAccessCert cac" +
            "    WHERE" +
            "       o.key=:ownerkey";
        Query query = this.getEntityManager().createQuery(hql);
        List<String> certsToDelete = query.setParameter("ownerkey", owner.getKey()).getResultList();

        hql = "DELETE from ContentAccessCertificate WHERE id IN (:certsToDelete)";
        String hql2 = "UPDATE Consumer set contentAccessCert = null WHERE " +
            "contentAccessCert.id IN (:certsToDelete)";
        query = this.getEntityManager().createQuery(hql);
        Query query2 = this.getEntityManager().createQuery(hql2);
        int removed = 0;
        for (List<String> block : Iterables.partition(certsToDelete,
            AbstractHibernateCurator.IN_OPERATOR_BLOCK_SIZE)) {
            String param = block.toString();
            query2.setParameter("certsToDelete", block).executeUpdate();
            removed += query.setParameter("certsToDelete", block).executeUpdate();
        }
        return removed;
    }
}
