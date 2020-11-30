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
package org.candlepin.dto;

import org.candlepin.model.TimestampedEntity;


/**
 * The TimestampedEntityTranslator provides common functionality for translating and populating
 * DTOs derived from model objects which contain the created and updated timestamps.
 *
 * @param <I>
 *  The input entity type supported by this translator
 *
 * @param <O>
 *  The output DTO type generated/managed by this translator
 */
public abstract class TimestampedEntityTranslator
    <I extends TimestampedEntity, O extends TimestampedCandlepinDTO> implements ObjectTranslator<I, O> {

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract O translate(I source);

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract O translate(ModelTranslator translator, I source);

    /**
     * {@inheritDoc}
     */
    @Override
    public O populate(I source, O destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public O populate(ModelTranslator translator, I source, O destination) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (destination == null) {
            throw new IllegalArgumentException("destination is null");
        }

        destination.setCreated(source.getCreated());
        destination.setUpdated(source.getUpdated());

        return destination;
    }
}
