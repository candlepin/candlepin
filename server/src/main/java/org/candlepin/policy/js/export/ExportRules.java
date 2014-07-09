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
package org.candlepin.policy.js.export;

import org.candlepin.model.Entitlement;
import org.candlepin.model.Pool;

import com.google.inject.Inject;

/**
 *
 */
public class ExportRules {

    @Inject
    public ExportRules() {
    }

    public boolean canExport(Entitlement entitlement) {
        Pool pool = entitlement.getPool();

        // Product would typically never have pool_derived on it, as this would only be
        // applied to pools internally by candlepin, but some tests were doing this so
        // we will continue doing the same.
        Boolean poolDerived = pool.hasProductAttribute("pool_derived") ||
            pool.hasAttribute("pool_derived");
        return !entitlement.getConsumer().getType().isManifest() || !poolDerived;
    }

}
