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
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.ConsumerTypeCurator;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.Test;

/**
 * ConsumerTypeImporterTest
 */
public class ConsumerTypeImporterTest {

    @Test
    public void testDeserialize() throws IOException {
        String consumerTypeString = "{\"id\":15, \"label\":\"prosumer\"}";
        
        Reader reader = new StringReader(consumerTypeString);
        
        ConsumerType consumerType = new ConsumerTypeImporter(null).createObject(
            SyncUtils.getObjectMapper(new Config(new HashMap<String, String>())), reader);
        
        assertEquals("prosumer", consumerType.getLabel());
    }
    
    @Test
    public void testDeserializeIdIsNull() throws IOException {
        String consumerTypeString = "{\"id\":15, \"label\":\"prosumer\"}";
        
        Reader reader = new StringReader(consumerTypeString);
        
        ConsumerType consumerType = new ConsumerTypeImporter(null).createObject(
            SyncUtils.getObjectMapper(new Config(new HashMap<String, String>())), reader);
        
        assertEquals(null, consumerType.getId());      
    }
    
    @Test
    public void testSingleConsumerTypeInDbAndListCausesNoChange() {
        final ConsumerType testType = new ConsumerType();
        testType.setLabel("prosumer");
        
        ConsumerTypeCurator curator = mock(ConsumerTypeCurator.class);
        
        when(curator.lookupByLabel("prosumer")).thenReturn(testType);
        
        ConsumerTypeImporter importer = new ConsumerTypeImporter(curator);
        importer.store(new HashSet<ConsumerType>() {
            {
                add(testType);
            }
        });
        
        verify(curator, never()).create(testType);
        verify(curator, never()).merge(testType);
    }
    
    @Test
    public void testSingleConsumerTypeInListEmptyDbCausesInsert() {
        final ConsumerType testType = new ConsumerType();
        testType.setLabel("prosumer");
        
        ConsumerTypeCurator curator = mock(ConsumerTypeCurator.class);
        
        when(curator.lookupByLabel("prosumer")).thenReturn(null);
        
        ConsumerTypeImporter importer = new ConsumerTypeImporter(curator);
        importer.store(new HashSet<ConsumerType>() {
            {
                add(testType);
            }
        });
        
        verify(curator).create(testType);
        verify(curator, never()).merge(testType);        
    }
    
    @Test
    public void testEmptyListCausesDbRemove() {
        final ConsumerType typeInDb1 = new ConsumerType();
        typeInDb1.setLabel("oldconsumer");
        final ConsumerType typeInDb2 = new ConsumerType();
        typeInDb2.setLabel("otheroldconsumer");
        
        ConsumerTypeCurator curator = mock(ConsumerTypeCurator.class);
        
        when(curator.listAll()).thenReturn(new ArrayList<ConsumerType>() {
            {
                add(typeInDb1);
                add(typeInDb2);
            }
        });
        
        ConsumerTypeImporter importer = new ConsumerTypeImporter(curator);
        importer.store(new HashSet<ConsumerType>());
        
        verify(curator).delete(typeInDb1);
        verify(curator).delete(typeInDb2);
    }
    
    @Test
    public void testEmptyListWithRefToDbLeavesTypeInDb() {
        final ConsumerType typeInDb1 = new ConsumerType();
        typeInDb1.setLabel("oldconsumer");
        final ConsumerType typeInDb2 = new ConsumerType();
        typeInDb2.setLabel("otheroldconsumer");
        
        ConsumerTypeCurator curator = mock(ConsumerTypeCurator.class);
        
        when(curator.listAll()).thenReturn(new ArrayList<ConsumerType>() {
            {
                add(typeInDb1);
                add(typeInDb2);
            }
        });
        
        doThrow(new ConstraintViolationException(null, null,
            null)).when(curator).delete(typeInDb1);
        
        ConsumerTypeImporter importer = new ConsumerTypeImporter(curator);
        importer.store(new HashSet<ConsumerType>());
        
        verify(curator).delete(typeInDb1);
        verify(curator).delete(typeInDb2);        
    }
}
