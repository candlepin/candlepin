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
package org.fedoraproject.candlepin.resource;

import org.fedoraproject.candlepin.model.JsonTestObject;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;


/**
 * TestApi - used to prototype RESTful things without mucking up real
 * test classes.
 * @version $Rev$
 */
@Path("/test")
public class TestApi {

    private static JsonTestObject jto = null;
    
    public TestApi() {
        System.out.println("hello from TestApi ctor");
    }
    
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public JsonTestObject get() {
        return jto;
    }
    
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public void create(JsonTestObject obj) {
        jto = obj;
        System.out.println("object.name:" + obj.getName());
        System.out.println("jto.name:" + jto.getName());
        System.out.println("jto.uuid:" + jto.getUuid());
        System.out.println("jto.list:" + jto.getStringList());
    }
}
