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
package org.candlepin.service;

import java.util.List;

import org.candlepin.model.Consumer;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.EntitlementCertificateCurator;

/**
 * BaseEntitlementCertServiceAdapter
 *
 * Shared base class for all entitlement cert service adapters. Because we store the
 * certs in most cases, some functionality is common to all.
 */
public abstract class BaseEntitlementCertServiceAdapter implements
    EntitlementCertServiceAdapter {

    protected EntitlementCertificateCurator entCertCurator;

    @Override
    public List<EntitlementCertificate> listForConsumer(
        Consumer consumer) {
        return entCertCurator.listForConsumer(consumer);
    }

}
