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
package org.fedoraproject.candlepin.model.test;

import junit.framework.TestCase;

import org.fedoraproject.candlepin.model.BaseModel;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.ObjectFactory;
import org.fedoraproject.candlepin.model.Organization;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.User;

/**
 * 
 *
 */
public class OrganizationTest extends TestCase {

	public void testOrg() throws Exception {
		Organization o = new Organization(BaseModel.generateUUID());
		assertNotNull(o);
	}
	
	public void testLookup() throws Exception {
		String lookedUp = BaseModel.generateUUID();
		Organization o = new Organization();
		o.setUuid(lookedUp);
		ObjectFactory.get().store(o);
		
		o = (Organization) ObjectFactory.get().
			lookupByUUID(Organization.class, lookedUp);
		assertNotNull(o);
	}
	
	public void testObjectRelationships() throws Exception {
		Organization org = new Organization(BaseModel.generateUUID());
		org.setName("test-org");
		// Product
		Product rhel = new Product(BaseModel.generateUUID());
		rhel.setName("Red Hat Enterprise Linux");
		
		// User
		User u = new User();
		u.setLogin("test-login");
		u.setPassword("redhat");
		org.addUser(u);
		assertEquals(1, org.getUsers().size());
		
		// Consumer
		Consumer c = new Consumer(BaseModel.generateUUID());
		c.setOrganization(org);
		org.addConsumer(c);
		c.addConsumedProduct(rhel);
		assertEquals(1, org.getConsumers().size());
		assertEquals(1, c.getConsumedProducts().size());
		
		// EntitlementPool
		EntitlementPool pool = new EntitlementPool(BaseModel.generateUUID());
		org.addEntitlementPool(pool);
		pool.setProduct(rhel);
		assertEquals(1, org.getEntitlementPools().size());
		
	}
}
