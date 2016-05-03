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
import com.google.inject.persist.Transactional;

import org.hibernate.criterion.Restrictions;

import java.util.Date;
import java.util.List;



/**
 * EntitlementCertificateCurator
 */
public class EntitlementCertificateCurator extends AbstractHibernateCurator<EntitlementCertificate> {

    @Inject
    public EntitlementCertificateCurator() {
        super(EntitlementCertificate.class);
    }

    @SuppressWarnings("unchecked")
    @Transactional
    public List<EntitlementCertificate> listForEntitlement(Entitlement e) {
        return currentSession().createCriteria(
            EntitlementCertificate.class).add(
                Restrictions.eq("entitlement", e)).list();

    }

    @SuppressWarnings("unchecked")
    @Transactional
    public List<EntitlementCertificate> listForConsumer(Consumer c) {
        return currentSession().createCriteria(EntitlementCertificate.class)
            .createAlias("entitlement", "ent")
            .createAlias("ent.pool", "p")
            .add(Restrictions.eq("ent.consumer", c))
            // Never show a consumer expired certificates
            .add(Restrictions.ge("p.endDate", new Date()))
            .list();
    }

    @Transactional
    public void delete(EntitlementCertificate cert) {
        // make sure to delete it! else get ready to face
        // javax.persistence.EntityNotFoundException('deleted entity passed to persist')
        cert.getEntitlement().getCertificates().remove(cert);
        super.delete(cert);
    }
}
