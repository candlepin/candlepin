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
package org.candlepin.dto.api.v1;

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.ObjectTranslator;
import org.candlepin.model.DeletedConsumer;

import java.time.ZoneOffset;
import java.util.Date;

/**
 * This translator provides translation from DeletedConsumer model objects to DeletedConsumerDTOs
 */
public class DeletedConsumerTranslator implements ObjectTranslator<DeletedConsumer, DeletedConsumerDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public DeletedConsumerDTO translate(DeletedConsumer source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DeletedConsumerDTO translate(ModelTranslator translator, DeletedConsumer source) {
        return source != null ? this.populate(translator, source, new DeletedConsumerDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DeletedConsumerDTO populate(DeletedConsumer source, DeletedConsumerDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DeletedConsumerDTO populate(ModelTranslator modelTranslator, DeletedConsumer source,
        DeletedConsumerDTO dest) {

        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (dest == null) {
            throw new IllegalArgumentException("destination is null");
        }

        dest.id(source.getId())
            .consumerUuid(source.getConsumerUuid())
            .ownerId(source.getOwnerId())
            .ownerKey(source.getOwnerKey())
            .ownerDisplayName(source.getOwnerDisplayName())
            .principalName(source.getPrincipalName());

        Date created = source.getCreated();
        dest.setCreated(created != null ? created.toInstant().atOffset(ZoneOffset.UTC) : null);

        Date updated = source.getUpdated();
        dest.setUpdated(updated != null ? updated.toInstant().atOffset(ZoneOffset.UTC) : null);

        return dest;
    }
}
