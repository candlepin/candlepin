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
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Date;
import java.util.HashMap;

/**
 * MetaExporterTest
 */
public class MetaExporterTest {

    @Test
    public void testMetaExporter() throws IOException {
        ObjectMapper mapper = SyncUtils.getObjectMapper(
            new Config(new HashMap<String, String>()));

        MetaExporter metaEx = new MetaExporter();
        StringWriter writer = new StringWriter();
        Meta meta = new Meta();
        Date now = new Date();
        String nowString = mapper.convertValue(now, String.class);
        meta.setVersion("0.1.0");
        meta.setCreated(now);
        meta.setPrincipalName("myUsername");
        meta.setWebAppPrefix("webapp_prefix");

        metaEx.export(mapper, writer, meta);

        assertEquals("{\"version\":\"0.1.0\",\"created\":\"" + nowString +
            "\",\"principalName\":\"myUsername\",\"webAppPrefix\":\"webapp_prefix\"}",
            writer.toString());
    }

}
