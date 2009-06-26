/**
 * 
 */
package org.fedoraproject.candlepin.model.test;

import org.fedoraproject.candlepin.model.BaseModel;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.ObjectFactory;
import org.fedoraproject.candlepin.model.Organization;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.User;

import junit.framework.TestCase;

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
		Organization o = (Organization) ObjectFactory.get().
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
