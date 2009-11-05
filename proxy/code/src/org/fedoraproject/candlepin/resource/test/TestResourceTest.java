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
package org.fedoraproject.candlepin.resource.test;

import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.JsonTestObject;
import org.fedoraproject.candlepin.resource.TestResource;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import static org.junit.Assert.*;


/**
 * TestResourceTest
 * @version $Rev$
 */
public class TestResourceTest {
    
    private JsonTestObject createTestObject() {
        JsonTestObject jto = new JsonTestObject();
        jto.setName("testname");
        jto.setUuid("AEF");
        List<String> l = new ArrayList<String>();
        l.add("hey there");
        l.add("how are you?");
        jto.setStringList(l);
        return jto;
    }
    
    @Test
    public void testJson() {
        ClientConfig cc = new DefaultClientConfig();
        Client c = Client.create(cc);
        

        JsonTestObject jto = createTestObject();

        WebResource postresource = c.resource("http://localhost:8080/candlepin/test/");
        postresource.accept("application/json").type("application/json").post(jto);
        
        WebResource getresource = c.resource("http://localhost:8080/candlepin/test/");
        System.out.println(jto.getName());
        jto = getresource.accept("application/json").get(JsonTestObject.class);
        assertEquals("testname", jto.getName());
        assertEquals("AEF", jto.getUuid());
        assertNotNull(jto.getStringList());
        assertEquals(2, jto.getStringList().size());
        assertNull(jto.getParent());
        System.out.println(jto.getStringList());
    }
    
    @Test
    public void testGet() {
        TestResource tr = new TestResource();
        assertNull(tr.get());
        
        JsonTestObject jto = createTestObject();
        tr.create(jto);
        assertEquals(jto, tr.get());
    }
    
    @Test
    public void testConsumerType() {
        ClientConfig cc = new DefaultClientConfig();
        Client c = Client.create(cc);

        WebResource getresource =
            c.resource("http://localhost:8080/candlepin/test/consumertype");
        ConsumerType ct = getresource.accept("application/json").get(ConsumerType.class);
        assertNotNull(ct);
        assertEquals("testtype", ct.getLabel());
    }
}
