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
package org.fedoraproject.candlepin.model;

import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


/**
 * ProductFactory
 * @version $Rev$
 */
public class ProductFactory extends ObjectFactory {

    /**
     * Logger for this class
     */
    private static Logger log = Logger.getLogger(ProductFactory.class);
    
    private static ProductFactory instance = new ProductFactory();
    
    private Map<Product, List<ConsumerType>> prodConsumerMap;
    
    
    protected ProductFactory() {
        super();
        prodConsumerMap = new HashMap();
        
        // TODO: Move this all into the DB for definition
        // Create some Products
        Product rhel = new Product(BaseModel.generateUUID());
        rhel.setName("Red Hat Enterprise Linux");
        rhel.setLabel("rhel");
        this.store(rhel);
        
        Product jboss = new Product(BaseModel.generateUUID());
        jboss.setName("JBoss Application Server");
        jboss.setLabel("jboss-as");
        this.store(jboss);
        
        Product virt = new Product(BaseModel.generateUUID());
        virt.setName("RHEL Virtualization");
        virt.setLabel("rhel-virt");
        rhel.addChildProduct(virt);
        this.store(virt);
        
        Product cluster = new Product(BaseModel.generateUUID());
        cluster.setName("RHEL Cluster-Storage");
        cluster.setLabel("rhel-cluster");
        rhel.addChildProduct(cluster);
        this.store(cluster);
        
        Product jbossdev = new Product(BaseModel.generateUUID());
        jbossdev.setName("JBoss Developer Studio (v1) for Linux");
        this.store(jbossdev);
        
        List f = ObjectFactory.get().listObjectsByClass(Product.class);
        
        ConsumerType xenvm = new ConsumerType("xenvm"); 
        ConsumerType qemuvm = new ConsumerType("qemuvm");
        ConsumerType vmwarevm = new ConsumerType("vmwarevm");
        ConsumerType xenhost = new ConsumerType("xenhost");
        ConsumerType vmwarehost = new ConsumerType("vmwarehost");
        ConsumerType system = new ConsumerType("system");
        ConsumerType bladesystem = new ConsumerType("bladesystem");
        // ConsumerType javavm = new ConsumerType("javavm");
        this.store(xenvm);
        this.store(qemuvm);
        this.store(vmwarevm);
        this.store(xenhost);
        this.store(vmwarehost);
        this.store(system);
        this.store(bladesystem);
        
        List alltypes = new LinkedList();
        alltypes.add(xenvm);
        alltypes.add(qemuvm);
        alltypes.add(vmwarevm);
        alltypes.add(xenhost);
        alltypes.add(vmwarehost);
        alltypes.add(system);
        alltypes.add(bladesystem);
        // alltypes.add(javavm);
        
        List virttypes = new LinkedList();
        virttypes.add(xenhost);
        virttypes.add(system);
        virttypes.add(bladesystem);

        // List javatypes = new LinkedList();
        //javatypes.add(javavm);
        
 
        prodConsumerMap.put(rhel, alltypes);
        prodConsumerMap.put(jboss, alltypes);
        prodConsumerMap.put(virt, virttypes);
        
        // EntitlementPool
        Owner owner = (Owner) listObjectsByClass(Owner.class).get(0);
        EntitlementPool pool = new EntitlementPool(BaseModel.generateUUID());
        owner.addEntitlementPool(pool);
        pool.setProduct(rhel);
        store(pool);
        
        log.debug("ProductFactory constructor done.");
        
    }
    
    /**
     * Returns the instance of the ObjectFactory.
     * @return the instance of the ObjectFactory.
     */
    public static ProductFactory get() {
        return instance;
    }

    /**
     * Lookup a ConsumerTYpe by name
     * @param labelIn to lookup by
     * @return ConsumerType found
     */
    public ConsumerType lookupConsumerTypeByLabel(String labelIn) {
        return (ConsumerType) lookupByFieldName(ConsumerType.class, "label", labelIn);
    }
    
    /**
     * Get the list of ConsumerTypes that are compatible with a given Product.
     * 
     * @param productIn to check
     * @return List of ConsumerType objects that are compatible
     */
    public List<ConsumerType> getCompatibleConsumerTypes(Product productIn) {
        log.debug("getCompatibleConsumerTypes: " + productIn);
        return prodConsumerMap.get(productIn);
    }
}
