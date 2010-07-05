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
package org.fedoraproject.candlepin.exporter;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringReader;

import org.codehaus.jackson.map.ObjectMapper;
import org.fedoraproject.candlepin.model.Attribute;
import org.fedoraproject.candlepin.model.Product;
import org.junit.Before;
import org.junit.Test;

/**
 * ProductImporterTest
 */
public class ProductImporterTest {

    private ObjectMapper mapper;
    private ProductImporter importer;

    @Before
    public void setUp() throws IOException {
        mapper = ExportUtils.getObjectMapper();
        importer = new ProductImporter();
    }
    
    @Test
    public void importShouldCreateAValidProduct() throws IOException {
//        Product p = importer.createObject(mapper,
//            new StringReader("{\"name\":\"name\",\"id\":\"id\"," +
//                "\"content\":[1]," +
//                "\"attributes\":[{\"name\":\"a_name\",\"value\":\"a_value\"," +
//                "\"childAttributes\":[]}],\"multiplier\":1}"));
//
//        assertEquals("name", p.getName());
//        assertEquals("id", p.getId());
//        assertTrue(p.getAttributes().contains(new Attribute("a_name", "a_value")));
//        assertEquals(new Long(1), p.getMultiplier());
    }
}
