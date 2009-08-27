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
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerInfo;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.ObjectFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/consumer")
public class ConsumerApi extends BaseApi {

    @Override
    protected Class getApiClass() {
        return Consumer.class;
    }
    
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<Consumer> list() {
        List<Object> u = ObjectFactory.get().listObjectsByClass(getApiClass());
        List<Consumer> consumers = new ArrayList<Consumer>();
        for (Object o : u) {
            consumers.add((Consumer) o);
        }
        return consumers;
    }
    
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Consumer create(ConsumerInfo ci) {
        System.out.println("metadata: " + ci.getMetadata());
        System.out.println("ci: " + ci);
        //Owner owner = (Owner) ObjectFactory.get().lookupByUUID(Owner.class, owneruuid);
        Consumer c = new Consumer(BaseModel.generateUUID());
        //c.setOwner(owner);
        c.setInfo(ci);
        return c;
    }

    @GET @Path("/info")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public ConsumerInfo getInfo() {
        ConsumerInfo ci = new ConsumerInfo();
        ci.setType(new ConsumerType("system"));
        ci.setParent(null);
//        Map<String,String> m = new HashMap<String,String>();
//        m.put("cpu", "i386");
//        m.put("hey", "biteme");
//        ci.setMetadata(m);
        ci.setMetadataField("cpu", "i386");
        ci.setMetadataField("hey", "foobar");
        System.out.println(ci.getMetadata());
        return ci;
    }
}
