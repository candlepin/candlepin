/**
 * 
 */
package org.fedoraproject.candlepin.resource.test;

import org.fedoraproject.candlepin.model.BaseModel;
import org.fedoraproject.candlepin.model.ObjectFactory;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.User;
import org.fedoraproject.candlepin.resource.ApiHandler;
import org.fedoraproject.candlepin.resource.OwnerApi;

import junit.framework.TestCase;

/**
 * @author mmccune
 *
 */
public class ApiTest extends TestCase {

	public void testAuthentication() throws Exception {
		User u = new User();
		u.setLogin("admin");
		u.setPassword("password");
		
		ObjectFactory.get().store(u);
		
		ApiHandler handler = ApiHandler.get();
		String token = handler.login(u.getLogin(), "bad-password");
		assertNull(token);
		token = handler.login(u.getLogin(), u.getPassword());
		assertNotNull(token);
	}
	
	public void testLookupOwner() throws Exception {
		Owner o = new Owner(BaseModel.generateUUID());
		ObjectFactory.get().store(o);

		User u = new User();
		u.setLogin("admin");
		u.setPassword("password");
		ObjectFactory.get().store(u);
		
		String token = ApiHandler.get().login(u.getLogin(), u.getPassword());
		
		OwnerApi oapi = new OwnerApi();
		Owner lookedup = (Owner) oapi.get("BAD-UUID-NOTFOUND");
		assertNull(lookedup);
		lookedup = ApiHandler.get().getOwner(token, o.getUuid());
		assertNotNull(lookedup);
		
		// Check bad token
		boolean failed = false;
		try {
			lookedup = ApiHandler.get().getOwner("BAD-TOKEN", o.getUuid());
		} catch (Exception e) {
			failed = true;
		}
		assertTrue(failed);
		
	}
}
