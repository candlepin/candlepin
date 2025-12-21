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

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.manifest.v1.ConsumerDTO;
import org.candlepin.model.Consumer;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.Writer;

import javax.inject.Inject;


/**
 * Consumer - maps to the consumer.json file
 */
public class ConsumerExporter {

    private ModelTranslator translator;

    @Inject
    ConsumerExporter(ModelTranslator translator) {
        this.translator = translator;
    }

    void export(ObjectMapper mapper, Writer writer, Consumer consumer,
        String weburl, String apiurl) throws IOException {
        ConsumerDTO consumerDTO = this.translator.translate(consumer, ConsumerDTO.class);

        consumerDTO.setUrlApi(apiurl);
        consumerDTO.setUrlWeb(weburl);
        mapper.writeValue(writer, consumerDTO);
    }
}
