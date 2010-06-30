/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.model;

import java.util.List;

import org.fedoraproject.candlepin.util.Util;
import org.hibernate.criterion.Restrictions;


/**
 * CertificateSerialCurator - Interface to request a unique certificate serial number.
 */
public class CertificateSerialCurator extends AbstractHibernateCurator<CertificateSerial> {
    
    protected CertificateSerialCurator() {
        super(CertificateSerial.class);
    }

    /**
     * @return list of certificate serials which are revoked but not yet collected
     * and put into crl
     */
    @SuppressWarnings("unchecked")
    public List<CertificateSerial> retrieveTobeCollectedSerials() {
        return this.currentSession().createCriteria(CertificateSerial.class)
            .add(Restrictions.eq("revoked", true))
            .add(Restrictions.eq("collected", false)).list();
    }
    
    @SuppressWarnings("unchecked")
    public List<CertificateSerial> getExpiredSerials() {
        //TODO - Should date fields be truncated when checking expiration?
        return this.currentSession()
            .createCriteria(CertificateSerial.class)
            .add(Restrictions.ge("expiration", Util.yesterday())).list();
    }

    /**
     * 
     */
    public void deleteExpiredSerials() {
        this.currentSession()
            .createQuery("delete from CertificateSerial where expiration >= :date")
            .setDate("date", Util.yesterday())
            .executeUpdate();
    }

}
