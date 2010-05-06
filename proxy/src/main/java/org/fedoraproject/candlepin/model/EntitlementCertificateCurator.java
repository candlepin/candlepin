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

import org.fedoraproject.candlepin.auth.interceptor.EnforceAccessControl;
import org.hibernate.criterion.Restrictions;

import com.google.inject.Inject;

/**
 * EntitlementCertificateCurator
 */
public class EntitlementCertificateCurator extends
    AbstractHibernateCurator<EntitlementCertificate> {

    @Inject
    public EntitlementCertificateCurator() {
        super(EntitlementCertificate.class);
    }

    @SuppressWarnings("unchecked")
    public List<EntitlementCertificate> listForEntitlement(Entitlement e) {
        return currentSession().createCriteria(
            EntitlementCertificate.class).add(
                Restrictions.eq("entitlement", e)).list();

    }

    @SuppressWarnings("unchecked")
    @EnforceAccessControl
    public List<EntitlementCertificate> listForConsumer(Consumer c) {
        return currentSession().createCriteria(EntitlementCertificate.class)
            .createAlias("entitlement", "ent")
            .add(Restrictions.eq("ent.consumer", c))
            .list();
    }
}
