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
import org.candlepin.model.Product;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;

/**
 * ProductExporterTest
 */
public class ProductExporterTest {
    @Test
    public void testProductExport() throws IOException {
        ObjectMapper mapper = SyncUtils.getObjectMapper(
            new Config(new HashMap<String, String>()));

        ProductExporter exporter = new ProductExporter();

        StringWriter writer = new StringWriter();

        Product product = new Product("my-id", "product name");
        exporter.export(mapper, writer, product);
        String s = writer.toString();
        assertTrue(s.contains("\"name\":\"product name\""));
        assertTrue(s.contains("\"id\":\"my-id\""));
        assertTrue(s.contains("\"productContent\":[]"));
        assertTrue(s.contains("\"attributes\":[]"));
        assertTrue(s.contains("\"multiplier\":1"));
    }
}
