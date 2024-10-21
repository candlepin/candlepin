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



@Singleton
public class AnonymousContentAccessCertificateCurator
    extends AbstractHibernateCurator<AnonymousContentAccessCertificate> {

    public AnonymousContentAccessCertificateCurator() {
        super(AnonymousContentAccessCertificate.class);
    }

    /**
     * Retrieves all of the expired anonymous content access certificates.
     *
     * @return all of the expired anonymous content access certificates
     */
    public List<CertSerial> listAllExpired() {
        String hql = "SELECT new org.candlepin.model.CertSerial(c.id, s.id)" +
            " FROM AnonymousContentAccessCertificate c" +
            " INNER JOIN c.serial s " +
            " WHERE s.expiration < :nowDate";

        return this.getEntityManager()
            .createQuery(hql, CertSerial.class)
            .setParameter("nowDate", new Date())
            .getResultList();
    }

    /**
     * Deletes anonymous content access certificates based on the provided IDs.
     *
     * @param ids
     *  the IDs of anonymous content access certificates to delete
     *
     * @return
     *  the number of deleted records
     */
    public int deleteByIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }

        String query = "DELETE FROM AnonymousContentAccessCertificate c WHERE c.id IN (:ids)";

        int deleted = 0;
        for (Collection<String> idsToDeleteBlock : this.partition(ids)) {
            deleted += this.getEntityManager()
                .createQuery(query)
                .setParameter("ids", idsToDeleteBlock)
                .executeUpdate();
        }

        return deleted;
    }

}
