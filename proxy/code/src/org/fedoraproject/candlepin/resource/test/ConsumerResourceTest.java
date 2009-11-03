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

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerInfo;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.ObjectFactory;
import org.fedoraproject.candlepin.model.test.TestUtil;
import org.fedoraproject.candlepin.resource.ConsumerResource;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;

import junit.framework.TestCase;


/**
 * ConsumerResourceTest
 * @version $Rev$
 */
public class ConsumerResourceTest extends TestCase {

    public void testCreateConsumer() throws Exception {
        String newname = "test-consumer-" + System.currentTimeMillis();
        
        ConsumerResource capi = new ConsumerResource();
        ConsumerInfo ci = new ConsumerInfo();
        ci.setMetadataField("name", newname);
        ci.setType(new ConsumerType("standard-system"));
        capi.create(ci);
        assertNotNull(ObjectFactory.get().lookupByFieldName(Consumer.class, 
                "name", newname));
    }
    
    public void testDelete() {
        Consumer c = TestUtil.createConsumer();
        String uuid = c.getUuid();
        ConsumerResource capi = new ConsumerResource();
        assertNotNull(ObjectFactory.get().lookupByUUID(c.getClass(), uuid));
        capi.delete(uuid);
        assertNull(ObjectFactory.get().lookupByUUID(c.getClass(), uuid));
    }

    public void testJSON() { 
        ClientConfig cc = new DefaultClientConfig();
        Client c = Client.create(cc);

        ConsumerInfo ci = new ConsumerInfo();
        ci.setMetadataField("name", "jsontestname");
        ci.setType(new ConsumerType("standard-system"));
        
        WebResource res =
            c.resource("http://localhost:8080/candlepin/consumer/");
        Consumer rc = res.type("application/json").post(Consumer.class, ci);
        assertNotNull(rc);
        assertNotNull(rc.getUuid());
        System.out.println(rc.getUuid());
        
//        WebResource delres =
//          c.resource("http://localhost:8080/candlepin/consumer/");
//        delres.accept("application/json").delete(rc.getUuid());
//        
//        assertNull(ObjectFactory.get().lookupByUUID(c.getClass(), rc.getUuid()));
    }
}
