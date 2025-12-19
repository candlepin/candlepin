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
import org.candlepin.dto.StandardTranslator;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Product;
import org.candlepin.test.TestUtil;
import org.candlepin.util.ObjectMapperFactory;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;


public class ProductExporterTest {
    @Test
    public void testProductExport() throws IOException {
        DevConfig config = TestConfig.custom(Map.of(
            ConfigProperties.FAIL_ON_UNKNOWN_IMPORT_PROPERTIES, "false"));

        ObjectMapper mapper = ObjectMapperFactory.getSyncObjectMapper(config);

        ProductExporter exporter = new ProductExporter(
            new StandardTranslator(new ConsumerTypeCurator(), new EnvironmentCurator(), new OwnerCurator()));

        StringWriter writer = new StringWriter();

        Product product = TestUtil.createProduct("my-id", "product name");

        exporter.export(mapper, writer, product);
        String s = writer.toString();
        assertTrue(s.contains("\"name\":\"product name\""));
        assertTrue(s.contains("\"id\":\"my-id\""));
        assertTrue(s.contains("\"productContent\":[]"));
        assertTrue(s.contains("\"attributes\":[]"));
        assertTrue(s.contains("\"multiplier\":1"));
    }
}
