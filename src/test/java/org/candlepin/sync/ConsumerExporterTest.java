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

import static org.junit.Assert.assertEquals;

import org.candlepin.config.Config;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;

/**
 * ConsumerExporterTest
 */
public class ConsumerExporterTest {
    @Test
    public void testConsumerExport() throws IOException {
        ObjectMapper mapper = SyncUtils.getObjectMapper(
            new Config(new HashMap<String, String>()));

        ConsumerExporter exporter = new ConsumerExporter();
        ConsumerType ctype = new ConsumerType("candlepin");
        ctype.setId("8888");
        ctype.setManifest(true);

        StringWriter writer = new StringWriter();

        Consumer consumer = new Consumer();
        consumer.setUuid("test-uuid");
        consumer.setName("testy consumer");
        consumer.setType(ctype);

        exporter.export(mapper, writer, consumer, "testHostname", "testPrefix");

        assertEquals("{\"uuid\":\"" + consumer.getUuid() + "\"," +
                     "\"name\":\"" + consumer.getName() + "\"," +
                     "\"type\":" +
                     "{\"id\":\"" + ctype.getId() + "\"," +
                     "\"label\":\"" + ctype.getLabel() + "\"," +
                     "\"manifest\":" + ctype.isManifest() + "}," +
                     "\"owner\":null," +
                     "\"hostname\":\"testHostname\"," +
                     "\"prefix\":\"testPrefix\"}",
            writer.toString());
    }
}
