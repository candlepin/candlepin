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
import org.candlepin.dto.ObjectTranslator;
import org.candlepin.service.model.OwnerInfo;



/**
 * The OwnerInfoTranslator provides translation from OwnerInfo service model objects to OwnerDTOs
 */
public class OwnerInfoTranslator implements ObjectTranslator<OwnerInfo, OwnerDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public OwnerDTO translate(OwnerInfo source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OwnerDTO translate(ModelTranslator translator, OwnerInfo source) {
        return source != null ? this.populate(translator, source, new OwnerDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OwnerDTO populate(OwnerInfo source, OwnerDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OwnerDTO populate(ModelTranslator translator, OwnerInfo source, OwnerDTO dest) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (dest == null) {
            throw new IllegalArgumentException("dest is null");
        }

        dest.setCreated(source.getCreated());
        dest.setUpdated(source.getUpdated());

        // Service model objects do not provide an ID
        dest.setId(null);
        dest.setKey(source.getKey());

        // These fields are not present on the service model
        dest.setDisplayName(null);
        dest.setContentPrefix(null);
        dest.setDefaultServiceLevel(null);
        dest.setLogLevel(null);
        dest.setAutobindDisabled(null);
        dest.setAutobindHypervisorDisabled(null);
        dest.setContentAccessMode(null);
        dest.setContentAccessModeList(null);
        dest.setLastRefreshed(null);
        dest.setParentOwner(null);
        dest.setUpstreamConsumer(null);

        return dest;
    }

}
