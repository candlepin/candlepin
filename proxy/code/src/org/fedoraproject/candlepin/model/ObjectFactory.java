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
import org.fedoraproject.candlepin.util.MethodUtil;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * ObjectFactory is used to create and persist the data model.
 * @author mmccune
 */
public class ObjectFactory {

    /** Logger for this class */
    private static final Logger logger = Logger.getLogger(ObjectFactory.class);

    /** the singleton instance of the ObjectFactory */
    private static ObjectFactory instance = new ObjectFactory();
    
    private Map objects;
    

    /**
     * default constructor
     */
    private ObjectFactory() {
        objects = new HashMap();
        initMockObjects();
    }

    /**
     * @deprecated demo method
     */
    private void initMockObjects() {
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
        
        // Consumer
        Consumer c = new Consumer(BaseModel.generateUUID());
        c.setName("fake-consumer-i386");
        c.setOrganization(org);
        org.addConsumer(c);
        c.addConsumedProduct(rhel);
        
        // EntitlementPool
        EntitlementPool pool = new EntitlementPool(BaseModel.generateUUID());
        org.addEntitlementPool(pool);
        pool.setProduct(rhel);

        this.store(org);
        this.store(u);
        this.store(c);
        this.store(pool);
    }

    /**
     * Returns the instance of the ObjectFactory.
     * @return the instance of the ObjectFactory.
     */
    public static ObjectFactory get() {
        return instance;
    }
    
    /**
     * Get a List of objects by type
     * @param clazz
     * @return List if found. null if not.
     */
    public List<Object> listObjectsByClass(Class<?> clazz) {
        return (List<Object>) objects.get(clazz.getName());
    }

    /**
     * Lookup an Organization by UUID
     * @param uuid to lookup
     * @return Organization
     */
    public BaseModel lookupByUUID(Class<?> clazz, String uuid) {
        return (BaseModel) lookupByFieldName(clazz, "uuid", uuid);
    }

    /**
     * Lookup an object by a field name
     * @param clazz
     * @param fieldName
     * @return BaseModel if found.
     */
    public Object lookupByFieldName(Class<?> clazz, String fieldName, String value) {
        String key = clazz.getName();
        if (!objects.containsKey(key)) {
            return null;
        }
        List typelist = (List) objects.get(key);
        for (int i = 0; i < typelist.size(); i++) {
            Object o = typelist.get(i);
            logger.debug("O: " + o);
            String getter = "get" +  fieldName.substring(0, 1).toUpperCase() +
                fieldName.substring(1);
            logger.debug("getter: " + getter);
            Object v = MethodUtil.callMethod(o, getter, new Object[0]);
            logger.debug("v: " + v);
            if (v != null && v.equals(value)) {
                return o;
            }
        }
        return null;
    }

    /**
     * Store an object
     * @param u
     */
    public Object store(Object u) {
        String key = u.getClass().getName();
        if (!objects.containsKey(key)) {
            List newtype = new LinkedList();
            newtype.add(u);
            objects.put(u.getClass().getName(), newtype);
        }
        List typelist = (List) objects.get(key);
        typelist.add(u);
        return u;
    }
    

}
