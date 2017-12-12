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
package org.candlepin.sync;

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;

import java.io.IOException;
import java.io.Writer;

/**
 * Consumer - maps to the consumer.json file
 */
public class ConsumerExporter {

    @Inject
    ConsumerExporter() {
    }

    void export(ObjectMapper mapper, Writer writer, Consumer consumer,
        String weburl, String apiurl) throws IOException {

        ConsumerType type = consumer.getType();
        ConsumerType tdto = null;

        if (type != null) {
            tdto = new ConsumerType();

            tdto.setId(type.getId());
            tdto.setLabel(type.getLabel());
            tdto.setManifest(type.isManifest());
        }

        ConsumerDto dto = new ConsumerDto(consumer.getUuid(), consumer.getName(),
            tdto, consumer.getOwner(), weburl, apiurl, consumer.getContentAccessMode());

        mapper.writeValue(writer, dto);
    }
}
