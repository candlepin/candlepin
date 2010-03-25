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

import org.hibernate.criterion.Restrictions;

import com.google.inject.Inject;

/**
 * ConsumerCurator
 */
public class ConsumerEntitlementCertificateCurator extends
    AbstractHibernateCurator<ConsumerEntitlementCertificate> {

    /**
     * default constructor
     */
    @Inject
    public ConsumerEntitlementCertificateCurator() {
        super(ConsumerEntitlementCertificate.class);
    }

    public List<ConsumerEntitlementCertificate> listForEntitlement(Entitlement e) {
        return (List<ConsumerEntitlementCertificate>) currentSession().createCriteria(
            ConsumerEntitlementCertificate.class).add(
                Restrictions.eq("entitlement", e)).list();

    }

    public List<ConsumerEntitlementCertificate> listForConsumer(Consumer c) {
        return (List<ConsumerEntitlementCertificate>) currentSession().createCriteria(
            ConsumerEntitlementCertificate.class).
            createAlias("entitlement", "ent").
            add(Restrictions.eq("ent.consumer", c)).list();
    }
}
