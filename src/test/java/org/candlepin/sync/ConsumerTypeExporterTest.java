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

import static org.junit.Assert.assertTrue;

import org.candlepin.config.Config;
import org.candlepin.model.ConsumerType;
import org.candlepin.test.TestUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;

/**
 * ConsumerTypeExporterTest
 */
public class ConsumerTypeExporterTest {

    @Test
    public void testConsumerTypeExport() throws IOException {
        ObjectMapper mapper = SyncUtils.getObjectMapper(
            new Config(new HashMap<String, String>()));

        ConsumerTypeExporter consumerType = new ConsumerTypeExporter();

        StringWriter writer = new StringWriter();

        ConsumerType type = new ConsumerType("TESTTYPE");

        consumerType.export(mapper, writer, type);

        StringBuffer json = new StringBuffer();
        json.append("{\"id\":null,\"label\":\"TESTTYPE\",");
        json.append("\"manifest\":false}");
        assertTrue(TestUtil.isJsonEqual(json.toString(), writer.toString()));
    }

}
