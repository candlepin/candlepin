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
package org.candlepin.sync;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;

import org.codehaus.jackson.map.ObjectMapper;
import org.candlepin.config.Config;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * MetaExporterTest
 */
public class MetaExporterTest {

    @Test
    public void testMetaExporter() throws IOException {
        ObjectMapper mapper = SyncUtils.getObjectMapper(
            new Config(new HashMap<String, String>()));

        MetaExporter meta = new MetaExporter();

        StringWriter writer = new StringWriter();

        meta.export(mapper, writer, new Meta());
        assertTrue(writer.toString().contains("\"version\":\"0.0.0\""));
    }

}
