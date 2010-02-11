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

import java.util.HashSet;
import java.util.List;
import java.util.LinkedList;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.fedoraproject.candlepin.model.ClientCertificate;
import org.fedoraproject.candlepin.model.ClientCertificateSerialNumber;
import org.fedoraproject.candlepin.model.ClientCertificateStatus;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.JsonTestObject;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Product;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;


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
    
   @GET @Path("/consumer")
   @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
   public Consumer getConsumer() {
       Consumer consumer  = new Consumer();
       Product product = new Product("test product", "SuperAwesomeEnterpriseHyperLinux");
       consumer.addConsumedProduct(product);
       return consumer;
       
   }
   
   
   @GET @Path("/client_certificate")
   @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
   public ClientCertificate getCertificate() {
           return new  ClientCertificate();
   }
   
   @GET @Path("/client_certificate_serial_numbers")
   @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
   public List<ClientCertificateSerialNumber> getCertificateSerialNumber() {
           List<ClientCertificateSerialNumber> serialNumberList = new LinkedList<ClientCertificateSerialNumber>();
           serialNumberList.add(new  ClientCertificateSerialNumber("SerialNumbersAreAwesome-1234"));
           serialNumberList.add(new  ClientCertificateSerialNumber("A different serial Number"));
           return serialNumberList;
   }
   
   @GET @Path("/certificate_status")
   @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
   public ClientCertificateStatus getCertificateStatus() {
           return new  ClientCertificateStatus("123456", "AWESOME", new ClientCertificate());
   }
   
   
   
   
   @GET @Path("/certificate_status_list")
   @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
   public List<ClientCertificateStatus> getCertificateStatusList() {
           List<ClientCertificateStatus> certList = new LinkedList<ClientCertificateStatus>();
           certList.add(new ClientCertificateStatus("blargh", "supertype", new ClientCertificate()));
           return certList;
   }
   
   @GET @Path("/product")
   @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
   public Product getProduct() {
       return new Product("test product", "SuperAwesomeEnterpriseHyperLinux");
       //           return new  ClientCertificateStatus("123456", "AWESOME", new ClientCertificate());
   }
   
   @POST
   @Path("/owner")
   public Owner getOwner() {
           return new Owner("test_owner");
   }
   
}
