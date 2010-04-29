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

import org.apache.log4j.Logger;
import org.hibernate.Criteria;
import org.hibernate.criterion.Restrictions;

/**
 * CertificateSerialCurator - Interface to request a unique certificate serial number.
 * Doesn't do much else.
 */
public class CertificateSerialCurator extends AbstractHibernateCurator<CertificateSerial> {

    private static Logger log = Logger.getLogger(CertificateSerialCurator.class);

    protected CertificateSerialCurator() {
        super(CertificateSerial.class);
    }

    /**
     * Get the next available serial number, delete the old entries from the db.
     * (we're really only interested in the most recent)
     *
     * @return next available serial number.
     */
    public Long getNextSerial() {
        CertificateSerial serial = new CertificateSerial();
        create(serial);

        Criteria crit = currentSession().createCriteria(CertificateSerial.class);
        crit.add(Restrictions.lt("id", serial.getId()));
        List<CertificateSerial> toDelete = crit.list();
        for (CertificateSerial deleteThis : toDelete) {
            log.debug("Deleting serial: " + deleteThis);
            delete(deleteThis);
        }

        return serial.getId();
    }

}
