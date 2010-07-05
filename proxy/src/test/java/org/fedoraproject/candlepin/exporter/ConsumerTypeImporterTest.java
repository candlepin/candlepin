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

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

import org.fedoraproject.candlepin.model.ConsumerType;
import org.junit.Test;

/**
 * ConsumerTypeImporterTest
 */
public class ConsumerTypeImporterTest {

    @Test
    public void testDeserialize() throws IOException {
        String consumerTypeString = "{\"id\":15, \"label\":\"prosumer\"}";
        
        Reader reader = new StringReader(consumerTypeString);
        
        ConsumerType consumerType =
            new ConsumerTypeImporter().importObject(ExportUtils.getObjectMapper(), reader);
        
        assertEquals("prosumer", consumerType.getLabel());
    }
    
    @Test
    public void testDeserializeIdIsNull() throws IOException {
        String consumerTypeString = "{\"id\":15, \"label\":\"prosumer\"}";
        
        Reader reader = new StringReader(consumerTypeString);
        
        ConsumerType consumerType =
            new ConsumerTypeImporter().importObject(ExportUtils.getObjectMapper(), reader);
        
        assertEquals(null, consumerType.getId());      
    }
}
