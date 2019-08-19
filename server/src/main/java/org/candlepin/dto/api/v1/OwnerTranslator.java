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
import org.candlepin.dto.TimestampedEntityTranslator;
import org.candlepin.model.Owner;
import org.candlepin.model.UpstreamConsumer;



/**
 * The OwnerTranslator provides translation from Owner model objects to OwnerDTOs
 */
public class OwnerTranslator extends TimestampedEntityTranslator<Owner, OwnerDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public OwnerDTO translate(Owner source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OwnerDTO translate(ModelTranslator translator, Owner source) {
        return source != null ? this.populate(translator, source, new OwnerDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OwnerDTO populate(Owner source, OwnerDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OwnerDTO populate(ModelTranslator translator, Owner source, OwnerDTO dest) {
        dest = super.populate(translator, source, dest);

        dest.setId(source.getId());
        dest.setKey(source.getKey());
        dest.setDisplayName(source.getDisplayName());
        dest.setContentPrefix(source.getContentPrefix());
        dest.setDefaultServiceLevel(source.getDefaultServiceLevel());
        dest.setLogLevel(source.getLogLevel());
        dest.setAutobindDisabled(source.isAutobindDisabled());
        dest.setAutobindHypervisorDisabled(source.isAutobindHypervisorDisabled());
        dest.setContentAccessMode(source.getContentAccessMode());
        dest.setContentAccessModeList(source.getContentAccessModeList());
        dest.setLastRefreshed(source.getLastRefreshed());

        // TODO: Should this actually follow the nested child rules of all the other objects?
        Owner parent = source.getParentOwner();
        dest.setParentOwner(parent != null ? this.translate(translator, parent) : null);

        // Process nested objects if we have a model translator to use to the translation...
        if (translator != null) {
            UpstreamConsumer consumer = source.getUpstreamConsumer();
            dest.setUpstreamConsumer(translator.translate(consumer, UpstreamConsumerDTO.class));
        }
        else {
            dest.setUpstreamConsumer(null);
        }

        return dest;
    }

}
