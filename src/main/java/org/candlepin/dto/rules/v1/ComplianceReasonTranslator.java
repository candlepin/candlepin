/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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
package org.candlepin.dto.rules.v1;

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.ObjectTranslator;
import org.candlepin.policy.js.compliance.ComplianceReason;



/**
 * Rules translator for the ComplianceReason object
 */
public class ComplianceReasonTranslator implements ObjectTranslator<ComplianceReason, ComplianceReasonDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public ComplianceReasonDTO translate(ComplianceReason source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ComplianceReasonDTO translate(ModelTranslator translator, ComplianceReason source) {
        return source != null ? this.populate(translator, source, new ComplianceReasonDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ComplianceReasonDTO populate(ComplianceReason source, ComplianceReasonDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ComplianceReasonDTO populate(ModelTranslator translator, ComplianceReason source,
        ComplianceReasonDTO destination) {

        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (destination == null) {
            throw new IllegalArgumentException("destination is null");
        }

        destination.setKey(source.getKey());
        destination.setMessage(source.getMessage());
        destination.setAttributes(source.getAttributes());

        return destination;
    }
}
