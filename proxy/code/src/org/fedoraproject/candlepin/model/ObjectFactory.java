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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author mmccune
 *
 */
public class ObjectFactory {

    /**
     * Logger for this class
     */
    private static final Logger logger = Logger.getLogger(ObjectFactory.class);

    private static ObjectFactory instance = new ObjectFactory();
    private Map objects;
    
    
    private ObjectFactory() {
        objects = new HashMap();
        initMockObjects();
    }

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

    public static ObjectFactory get() {
        return instance;
    }
    
    /**
     * Get a List of objects by type
     * @param clazz
     * @return List if found.  null if not.
     */
    public List listObjectsByClass(Class<?> clazz) {
        return (List) objects.get(clazz.getName());
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
     * Create a new instance of the classname passed in.
     * 
     * @param className
     * @return instance of class passed in.
     */
    private static Object callNewMethod(String className, Object... args) {
        Object retval = null;
        
        try {
            Class<?> clazz = Thread.currentThread().
                            getContextClassLoader().loadClass(className);
            if (args == null || args.length == 0) {
                retval = clazz.newInstance();                
            }
            else {
                try {
                    Constructor[] ctors = clazz.getConstructors();
                    for (Constructor ctor : ctors) {
                        if (isCompatible(ctor.getParameterTypes(), args)) {
                            return ctor.newInstance(args);
                        }
                    }
                }
                catch (IllegalArgumentException e) {
                    throw new RuntimeException(e);
                }
                catch (InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
            }

        }
        catch (InstantiationException e) {
           throw new RuntimeException(e);
        }
        catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        
        return retval;
    }

    /* This is insanity itself, but the reflection APIs ignore inheritance.
     * So, if you ask for a method that accepts (Integer, HashMap, HashMap),
     * and the class only has (Integer, Map, Map), you won't find the method.
     * This method uses Class.isAssignableFrom to solve this problem.
     */
    private static boolean isCompatible(Class[] declaredParams, Object[] params) {
        if (params.length != declaredParams.length) {
            return false;
        }

        for (int i = 0; i < params.length; i++) {
            if (!declaredParams[i].isInstance(params[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Store an object
     * @param u
     */
    public void store(Object u) {
        String key = u.getClass().getName();
        if (!objects.containsKey(key)) {
            List newtype = new LinkedList();
            newtype.add(u);
            objects.put(u.getClass().getName(), newtype);
        }
        List typelist = (List) objects.get(key);
        typelist.add(u);
    }
    

}
