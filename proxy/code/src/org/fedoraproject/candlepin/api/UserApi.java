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
package org.fedoraproject.candlepin.api;

import org.fedoraproject.candlepin.model.BaseModel;
import org.fedoraproject.candlepin.model.JsonTestObject;
import org.fedoraproject.candlepin.model.ObjectFactory;
import org.fedoraproject.candlepin.model.User;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;


/**
 * REST api gateway for the User object.
 */
@Path("/user")
public class UserApi extends BaseApi {
    private static JsonTestObject jto = null;
    
    public UserApi() {
        System.out.println("hello from ctor");
    }
    
    @GET @Path("/{login}")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public User get(@PathParam("login") String login) {
        return (User) ObjectFactory.get().lookupByFieldName(User.class, "login", login);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Class getApiClass() {
        return User.class;
    }
    
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<User> list() {
        List<Object> u = ObjectFactory.get().listObjectsByClass(getApiClass());
        List<User> users = new ArrayList<User>();
        for (Object o : u) {
            users.add((User) o);
        }
        return users;
    }
    
    @GET @Path("/testobject")
    @Produces(MediaType.APPLICATION_JSON)
    public JsonTestObject returnJsonObject() {
        return jto;
    }
    
    @POST @Path("/createtestobject")
    @Consumes(MediaType.APPLICATION_JSON)
    public void createJsonTestObject(JsonTestObject obj) {
        jto = obj;
        if (obj == null) {
            throw new RuntimeException("obj is null");
        }
        System.out.println("object.name:" + obj.getName());
        System.out.println("jto.name:" + jto.getName());
    }
    
}
