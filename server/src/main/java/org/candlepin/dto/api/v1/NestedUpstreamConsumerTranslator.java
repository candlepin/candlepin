/**
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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
import org.candlepin.model.UpstreamConsumer;

/**
 * The NestedUpstreamConsumerTranslator provides translation from UpstreamConsumer model objects to
 * NestedUpstreamConsumerDTO.
 */
public class NestedUpstreamConsumerTranslator implements
    ObjectTranslator<UpstreamConsumer, NestedUpstreamConsumerDTO> {


    @Override
    public NestedUpstreamConsumerDTO translate(UpstreamConsumer source) {
        return this.translate(null, source);
    }

    @Override
    public NestedUpstreamConsumerDTO translate(ModelTranslator translator, UpstreamConsumer source) {
        return source != null ? this.populate(translator, source, new NestedUpstreamConsumerDTO()) : null;
    }

    @Override
    public NestedUpstreamConsumerDTO populate(UpstreamConsumer source,
        NestedUpstreamConsumerDTO destination) {
        return this.populate(null, source, destination);
    }

    @Override
    public NestedUpstreamConsumerDTO populate(ModelTranslator translator,
        UpstreamConsumer source, NestedUpstreamConsumerDTO dest) {

        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (dest == null) {
            throw new IllegalArgumentException("dest is null");
        }

        dest.id(source.getId())
            .name(source.getName())
            .uuid(source.getUuid());

        return dest;
    }
}
