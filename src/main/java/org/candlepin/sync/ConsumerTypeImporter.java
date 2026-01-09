/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import org.candlepin.dto.manifest.v1.ConsumerTypeDTO;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.Reader;
import java.util.Set;


/**
 * ConsumerTypeImporter
 */
public class ConsumerTypeImporter {
    private static Logger log = LoggerFactory.getLogger(ConsumerTypeImporter.class);

    private ConsumerTypeCurator curator;

    public ConsumerTypeImporter(ConsumerTypeCurator curator) {
        this.curator = curator;
    }

    public ConsumerType createObject(ObjectMapper mapper, Reader reader)
        throws IOException {
        ConsumerTypeDTO consumerTypeDTO = mapper.readValue(reader, ConsumerTypeDTO.class);

        ConsumerType consumerType = new ConsumerType();
        consumerType.setManifest(
            consumerTypeDTO.isManifest() != null ? consumerTypeDTO.isManifest() : false);
        consumerType.setLabel(consumerTypeDTO.getLabel());
        consumerType.setId(null);
        return consumerType;
    }

    /**
     * @param consumerTypes Set of different consumer types.
     */
    public void store(Set<ConsumerType> consumerTypes) {
        log.debug("Creating/updating consumer types");
        for (ConsumerType consumerType : consumerTypes) {
            if (curator.getByLabel(consumerType.getLabel()) == null) {
                curator.create(consumerType);
                log.debug("Created consumer type: " + consumerType.getLabel());
            }
        }
    }
}
