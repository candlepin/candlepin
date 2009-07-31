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

import org.fedoraproject.candlepin.model.JsonTestObject;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;

import junit.framework.TestCase;


/**
 * TestApiTest
 * @version $Rev$
 */
public class TestApiTest extends TestCase {
    public void testJson() {
        ClientConfig cc = new DefaultClientConfig();
        Client c = Client.create(cc);
        
        WebResource getresource = c.resource("http://localhost:8080/candlepin/test/");
        JsonTestObject jto = new JsonTestObject();
        jto.setName("testname");
        jto.setUuid("AEF");

        WebResource postresource = c.resource("http://localhost:8080/candlepin/test/");
        postresource.accept("application/json").type("application/json").post(jto);
        
        System.out.println(jto.getName());
        jto = getresource.accept("application/json").get(JsonTestObject.class);
        assertEquals("testname", jto.getName());
        assertEquals("AEF", jto.getUuid());
    }
}
