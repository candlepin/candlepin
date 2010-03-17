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

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.fedoraproject.candlepin.model.ClientCertificate;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerFacts;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.JsonTestObject;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Product;

/**
 * TestResource - used to prototype RESTful things without mucking up real
 * test classes.
 * @version $Rev$
 */
@Path("/test")
public class TestResource {

    private static JsonTestObject jto = null;

    /**
     * default ctor
     */
    public TestResource() {
        System.out.println("hello from TestResource ctor");
    }

    @GET
    @Path("/gettest")
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public JsonTestObject gettest() {

        List<String> stringlist = new ArrayList<String>();
        stringlist.add("string2");
        stringlist.add("string3");


        JsonTestObject parent = new JsonTestObject();
        parent.setName("parentname");
        parent.setParent(null);
        parent.setStringList(stringlist);

        stringlist.add("child");
        JsonTestObject jto1 = new JsonTestObject();
        jto1.setName("myname");
        jto1.setParent(parent);
        jto1.setStringList(stringlist);

        return jto1; 
    }

    /**
     * Returns the test object
     * @return the test object
     */
    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public JsonTestObject get() {
        return jto;
    }

    /**
     * Creates a test json object
     * @param obj test object
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public void create(JsonTestObject obj) {
        jto = obj;
        System.out.println("object.name:" + obj.getName());
        System.out.println("jto.name:" + jto.getName());
        System.out.println("jto.list:" + jto.getStringList());
        System.out.println("jto.parent.name:" +
            jto.getParent() == null ? jto.getParent().getName() : "");
        System.out.println("jto.parent.list:" +
            jto.getParent() == null ? jto.getParent().getStringList() : "");
    }

    /**
     * Returns a ConsumerType
     * @return a ConsumerType
     */
    @GET @Path("/consumertype")
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public ConsumerType getConsumerType() {
        return new ConsumerType("testtype");
    }

    /**
     * Return a test consumer.
     * @return test consumer.
     */
    @GET @Path("/consumer")
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public Consumer getConsumer() {
        Consumer consumer  = new Consumer();
        ConsumerFacts facts = new ConsumerFacts();
        
        Map<String, String> metadata = new HashMap<String, String>();
        metadata.put("this_is_a_key", "this_is_a_value");
        metadata.put("this is a different key", "this is a different value");
        facts.setMetadata(metadata);
        consumer.setFacts(facts);
        return consumer;
    }

    /**
     * Return a client certificate.
     * @return a client certificate.
     */
    @GET @Path("/client_certificate")
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public ClientCertificate getCertificate() {
        return new  ClientCertificate();
    }

    /**
     * Returns JSON or XML version of a test product.
     * @return JSON or XML version of a test product.
     */
    @GET @Path("/product")
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public Product getProduct() {
        return new Product("test product", "SuperAwesomeEnterpriseHyperLinux");
    }

    /**
     * Returns a test owner.
     * @return a test owner.
     */
    @POST
    @Path("/owner")
    public Owner getOwner() {
        return new Owner("test_owner");
    }
}
