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

import java.util.Collection;
import java.util.Date;
import java.util.List;

import javax.inject.Singleton;

import com.google.inject.persist.Transactional;



@Singleton
public class AnonymousContentAccessCertificateCurator
    extends AbstractHibernateCurator<AnonymousContentAccessCertificate> {

    public AnonymousContentAccessCertificateCurator() {
        super(AnonymousContentAccessCertificate.class);
    }

    public List<ExpiredCertificate> listAllExpired() {
        String hql = "SELECT new org.candlepin.model.ExpiredCertificate(c.id, s.id)" +
            " FROM AnonymousContentAccessCertificate c" +
            " INNER JOIN c.serial s " +
            " WHERE s.expiration < :nowDate";

        return this.getEntityManager()
            .createQuery(hql, ExpiredCertificate.class)
            .setParameter("nowDate", new Date())
            .getResultList();
    }

    @Transactional
    public int deleteByIds(Collection<String> idsToDelete) {
        if (idsToDelete == null || idsToDelete.isEmpty()) {
            return 0;
        }

        String query = "DELETE FROM AnonymousContentAccessCertificate c WHERE c.id IN (:idsToDelete)";

        int deleted = 0;
        for (Collection<String> idsToDeleteBlock : this.partition(idsToDelete)) {
            deleted += this.currentSession().createQuery(query)
                .setParameter("idsToDelete", idsToDeleteBlock)
                .executeUpdate();
        }

        return deleted;
    }

}
