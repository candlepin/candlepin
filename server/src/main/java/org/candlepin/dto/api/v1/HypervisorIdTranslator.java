/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
package org.candlepin.dto.api.v1;

import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.HypervisorId;

/**
 * The HypervisorIdTranslator provides translation from HypervisorId model objects to
 * HypervisorIdDTOs
 */
public class HypervisorIdTranslator extends TimestampedEntityTranslator<HypervisorId, HypervisorIdDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public HypervisorIdDTO translate(HypervisorId source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HypervisorIdDTO translate(ModelTranslator translator, HypervisorId source) {
        return source != null ? this.populate(translator, source, new HypervisorIdDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HypervisorIdDTO populate(HypervisorId source, HypervisorIdDTO dest) {
        return this.populate(null, source, dest);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HypervisorIdDTO populate(ModelTranslator translator, HypervisorId source,
        HypervisorIdDTO dest) {

        dest = super.populate(translator, source, dest);

        dest.setId(source.getId().toString());
        dest.setHypervisorId(source.getHypervisorId());
        dest.setReporterId(source.getReporterId());

        return dest;
    }

}
