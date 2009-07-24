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

import com.sun.jersey.api.representation.Form;

import org.fedoraproject.candlepin.api.EntitlementApi;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.ObjectFactory;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.test.TestUtil;

import java.sql.Date;

import junit.framework.TestCase;


/**
 * ConsumerApiTest
 * @version $Rev$
 */
public class EntitlementApiTest extends TestCase {

    public void testEntitle() throws Exception {
        
        Consumer c = TestUtil.createConsumer();
        Product p = TestUtil.createProduct();
        EntitlementPool ep = new EntitlementPool();
        ep.setProduct(p);
        ep.setOwner(c.getOwner());
        ep.setMaxMembers(10);
        ep.setCurrentMembers(0);
        
        Date futuredate = new Date(System.currentTimeMillis() + 1000000000);
        Date pastdate = new Date(System.currentTimeMillis() - 1000000000);
        System.out.println("future: " + futuredate);
        System.out.println("past: " + pastdate);
        ep.setEndDate(futuredate);
        ObjectFactory.get().store(ep);
        
        EntitlementApi eapi = new EntitlementApi();
        Form f = new Form();
        f.add("consumer_uuid", c.getUuid());
        f.add("product_uuid", p.getUuid());
        String cert = (String) eapi.entitle(f);
        
        assertNotNull(cert);
        assertNotNull(c.getConsumedProducts());
        assertNotNull(c.getEntitlements());
     
        // Test max membership
        boolean failed = false;
        for (int i = 0; i < ep.getMaxMembers() + 10; i++) {
            Consumer ci = TestUtil.createConsumer(c.getOwner());
            f.add("consumer_uuid", ci.getUuid());
            try {
                eapi.entitle(f);
            }
            catch (Exception e) {
                System.out.println("Failed: " + e);
                failed = true;
            }
        }
        assertTrue("we didnt hit max members", failed);

        // Test expiration
        ep.setEndDate(pastdate);
        failed = false;
        try {
            eapi.entitle(f);
        } catch (Exception e) {
            System.out.println("expired:  ? " + e);
            failed = true;
        }
        assertTrue("we didnt expire", failed);
        

        
    }
}
