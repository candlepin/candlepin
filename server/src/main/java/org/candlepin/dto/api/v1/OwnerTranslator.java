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
import org.candlepin.model.Owner;
import org.candlepin.model.UpstreamConsumer;

import java.time.ZoneOffset;


/**
 * The OwnerTranslator provides translation from Owner model objects to OwnerDTOs
 */
public class OwnerTranslator implements ObjectTranslator<Owner, OwnerDTO> {

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
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (dest == null) {
            throw new IllegalArgumentException("dest is null");
        }

        dest.created(source.getCreated() != null ?
            source.getCreated().toInstant().atOffset(ZoneOffset.UTC) : null)
            .updated(source.getUpdated() != null ?
            source.getUpdated().toInstant().atOffset(ZoneOffset.UTC) : null)
            .id(source.getId())
            .key(source.getKey())
            .displayName(source.getDisplayName())
            .contentPrefix(source.getContentPrefix())
            .defaultServiceLevel(source.getDefaultServiceLevel())
            .logLevel(source.getLogLevel())
            .autobindDisabled(source.isAutobindDisabled())
            .autobindHypervisorDisabled(source.isAutobindHypervisorDisabled())
            .contentAccessMode(source.getContentAccessMode())
            .contentAccessModeList(source.getContentAccessModeList())
            .lastRefreshed(source.getLastRefreshed() != null ?
            source.getLastRefreshed().toInstant().atOffset(ZoneOffset.UTC) : null);

        if (translator != null) {
            Owner parent = source.getParentOwner();
            dest.setParentOwner(parent != null ? translator.translate(parent, NestedOwnerDTO.class) : null);
        }
        else {
            dest.setParentOwner(null);
        }

        // Process nested objects if we have a model translator to use to the translation...
        if (translator != null) {
            UpstreamConsumer consumer = source.getUpstreamConsumer();
            dest.upstreamConsumer(consumer != null ?
                translator.translate(consumer, NestedUpstreamConsumerDTO.class) : null);
        }
        else {
            dest.setUpstreamConsumer(null);
        }

        return dest;
    }

}
