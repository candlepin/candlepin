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

package org.candlepin.policy.js.compliance.hash;

import org.candlepin.model.Consumer;
import org.candlepin.policy.js.compliance.ComplianceStatus;

/**
 * Creates an SHA256 hash of the the important pieces of compliance data
 * that pertains to a compliance change in the context of emitting an
 * event.
 *
 * It is important to note that this is not intended to be a hash of
 * everything within compliance. The hash will contain only data that
 * should be considered when determining if a compliance event should
 * be emitted.
 */
public class ComplianceStatusHasher extends Hasher {

    public ComplianceStatusHasher(Consumer consumer, ComplianceStatus status) {
        putCollection(status.getNonCompliantProducts(), HashableStringGenerators.STRING);
        putCollection(status.getCompliantProducts().entrySet(),
            HashableStringGenerators.ENTITLEMENT_SET_ENTRY);
        putCollection(status.getPartiallyCompliantProducts().entrySet(),
            HashableStringGenerators.ENTITLEMENT_SET_ENTRY);
        putCollection(status.getPartialStacks().entrySet(), HashableStringGenerators.ENTITLEMENT_SET_ENTRY);
        putCollection(status.getReasons(), HashableStringGenerators.COMPLIANCE_REASON);
        putObject(consumer, HashableStringGenerators.CONSUMER);
    }

}
