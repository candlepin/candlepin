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

    /**
     * Returns the User identified by the given login.
     * @return user whose login is 'login'
     */
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

    /**
     * Returns a list of Users.
     * @return a list of Users.
     */
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

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public User create(String login, String password) {
        String newuuid = BaseModel.generateUUID();
        User u = new User(newuuid);
        ObjectFactory.get().store(u);
        return u;
    }
}
