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

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.test.TestUtil;
import org.candlepin.util.ObjectMapperFactory;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Date;
import java.util.Map;


public class MetaExporterTest {

    @Test
    public void testMetaExporter() throws IOException {
        DevConfig config = TestConfig.custom(Map.of(
            ConfigProperties.FAIL_ON_UNKNOWN_IMPORT_PROPERTIES, "false"));

        ObjectMapper mapper = ObjectMapperFactory.getSyncObjectMapper(config);

        MetaExporter metaEx = new MetaExporter();
        StringWriter writer = new StringWriter();
        Meta meta = new Meta();
        Date now = new Date();
        String nowString = mapper.convertValue(now, String.class);
        meta.setVersion("0.1.0");
        meta.setCreated(now);
        meta.setPrincipalName("myUsername");
        meta.setWebAppPrefix("webapp_prefix");
        meta.setCdnLabel("test-cdn");

        metaEx.export(mapper, writer, meta);

        String json = "{\"version\":\"0.1.0\",\"created\":\"" + nowString +
            "\",\"principalName\":\"myUsername\"," +
            "\"webAppPrefix\":\"webapp_prefix\"," +
            "\"cdnLabel\":\"test-cdn\"}";
        assertTrue(TestUtil.isJsonEqual(json, writer.toString()));
    }

}
