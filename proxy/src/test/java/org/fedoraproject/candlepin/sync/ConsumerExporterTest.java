/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.sync;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;

import org.codehaus.jackson.map.ObjectMapper;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.model.Consumer;
import org.junit.Test;

/**
 * ConsumerExporterTest
 */
public class ConsumerExporterTest {
    @Test
    public void testConsumerExport() throws IOException {
        ObjectMapper mapper = SyncUtils.getObjectMapper(
            new Config(new HashMap<String, String>()));
        
        ConsumerExporter exporter = new ConsumerExporter();
        
        StringWriter writer = new StringWriter();
        
        Consumer consumer = new Consumer();
        consumer.setUuid("test-uuid");
        
        exporter.export(mapper, writer, consumer);
        
        assertEquals("{\"uuid\":\"test-uuid\"}", writer.toString());
    }
}
