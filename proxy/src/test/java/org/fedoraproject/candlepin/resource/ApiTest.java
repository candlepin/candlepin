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
package org.fedoraproject.candlepin.resource;

import org.fedoraproject.candlepin.model.BaseModel;
import org.fedoraproject.candlepin.model.ObjectFactory;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.User;
import org.fedoraproject.candlepin.resource.ApiHandler;
import org.fedoraproject.candlepin.resource.OwnerResource;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * @author mmccune
 *
 */
public class ApiTest {

    @Test
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
    
    @Test
    public void testLookupOwner() throws Exception {
        Owner o = new Owner(BaseModel.generateUUID());
        ObjectFactory.get().store(o);

        User u = new User();
        u.setLogin("admin");
        u.setPassword("password");
        ObjectFactory.get().store(u);
        
        String token = ApiHandler.get().login(u.getLogin(), u.getPassword());
        
        OwnerResource oapi = new OwnerResource();
        Owner lookedup = (Owner) oapi.get("BAD-UUID-NOTFOUND");
        assertNull(lookedup);
        lookedup = ApiHandler.get().getOwner(token, o.getUuid());
        assertNotNull(lookedup);
        
        // Check bad token
        boolean failed = false;
        try {
            lookedup = ApiHandler.get().getOwner("BAD-TOKEN", o.getUuid());
        }
        catch (Exception e) {
            failed = true;
        }
        assertTrue(failed);
        
    }
}
