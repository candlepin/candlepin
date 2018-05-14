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

import com.google.inject.Inject;

import org.hibernate.criterion.Restrictions;

import javax.inject.Singleton;

/**
 * IdentityCertificateCurator
 */
@Singleton
public class IdentityCertificateCurator extends AbstractHibernateCurator<IdentityCertificate> {

    @Inject
    public IdentityCertificateCurator() {
        super(IdentityCertificate.class);
    }

    public IdentityCertificate getBySerialNumber(Long serialNumber) {
        return (IdentityCertificate) currentSession().createCriteria(IdentityCertificate.class)
            .add(Restrictions.eq("serial", serialNumber))
            .uniqueResult();
    }
}
