/**
 * Copyright (c) 2008 Red Hat, Inc.
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
package org.fedoraproject.candlepin.api.test;

import org.fedoraproject.candlepin.api.ConsumerApi;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerInfo;
import org.fedoraproject.candlepin.model.ObjectFactory;
import org.fedoraproject.candlepin.model.test.TestUtil;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.representation.Form;

import junit.framework.TestCase;


/**
 * ConsumerApiTest
 * @version $Rev$
 */
public class ConsumerApiTest extends TestCase {

    public void testCreateConsumer() throws Exception {
        String newname = "test-consumer-" + System.currentTimeMillis();
        
        ConsumerApi capi = new ConsumerApi();
        Form f = new Form();
        f.add("name", newname);
        f.add("type", "standard-system");
        capi.create(f);
        assertNotNull(ObjectFactory.get().lookupByFieldName(Consumer.class, 
                "name", newname));
        
        
    }
    
    public void testDelete() {
        Consumer c = TestUtil.createConsumer();
        String uuid = c.getUuid();
        ConsumerApi capi = new ConsumerApi();
        assertNotNull(ObjectFactory.get().lookupByUUID(c.getClass(), uuid));
        capi.delete(c);
        assertNull(ObjectFactory.get().lookupByUUID(c.getClass(), uuid));
    }

    public void testJSON() { 
        ClientConfig cc = new DefaultClientConfig();
        Client c = Client.create(cc);

        Consumer consumer = TestUtil.createConsumer();
        String uuid = consumer.getUuid();
        
        WebResource deleteResource = c.resource("http://localhost:8080/candlepin/consumer/");
        deleteResource.accept("application/json").type("application/json").delete(consumer);
        
        assertNull(ObjectFactory.get().lookupByUUID(c.getClass(), uuid));
    }

/*
    public void testJson() {
        ClientConfig cc = new DefaultClientConfig();
        Client c = Client.create(cc);
        ConsumerInfo ci = new ConsumerInfo();
        ci.setParent(null);
        ci.setType("system");
        ci.setMetadataField("mata1", "value1");
        WebResource postresource = c.resource("http://localhost:8080/candlepin/consumer");
        ConsumerInfo pci = postresource.accept("application/json").type("application/json").post(ConsumerInfo.class, ci);
        assertNotNull(pci);
        assertEquals("system", pci.getType());
        assertNotNull(pci.getMetadata());
        assertEquals("value1", pci.getMetadataField("mata1"));

//        WebResource getresource = c.resource("http://localhost:8080/candlepin/consumer/info");
//        ConsumerInfo nci = getresource.accept("application/json").get(ConsumerInfo.class);
//        assertNotNull(nci);
//        assertEquals("system", nci.getType());
//        assertNotNull(nci.getMetadata());
//        assertEquals("value1", nci.getMetadataField("mata1"));
//        System.out.println(nci.getType());
//        System.out.println(nci.getMetadata());



//        WebResource postresource = c.resource("http://localhost:8080/candlepin/test/");
//        postresource.accept("application/json").type("application/json").post(jto);
//
//        System.out.println(jto.getName());
//        jto = getresource.accept("application/json").get(JsonTestObject.class);
//        assertEquals("testname", jto.getName());
//        assertEquals("AEF", jto.getUuid());
    }
    */
}
