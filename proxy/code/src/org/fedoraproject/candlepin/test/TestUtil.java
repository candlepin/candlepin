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
package org.fedoraproject.candlepin.test;

import java.sql.Date;
import java.util.Calendar;
import java.util.Random;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.ObjectFactory;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.User;

import com.sun.jersey.core.util.Base64;

/**
 * TestUtil for creating various testing objects.
 * 
 * Objects backed by the database are not persisted, the caller is expected to persist
 * the entities returned and any dependent objects.
 */
public class TestUtil {
    
    private TestUtil() {
    }

    public static Owner createOwner() {
        Owner o = new Owner("Test Owner " + randomInt());
//        o.setUuid(lookedUp);
        ObjectFactory.get().store(o);
        return o;
    }

    public static Consumer createConsumer(ConsumerType type, Owner owner) {
        Consumer c = new Consumer("Test Consumer " + randomInt(), owner, type);
        ObjectFactory.get().store(c);
        return c;
    }

    /**
     * Create a consumer with a new owner
     * @return Consumer
     */
    public static Consumer createConsumer() {
        return createConsumer(createConsumerType(), createOwner());
    }
    
    public static ConsumerType createConsumerType() {
        return new ConsumerType("test-consumer-type-" + randomInt());
    }
    
    public static int randomInt() {
        return new Random().nextInt(10000);
    }

    public static Product createProduct() {
        int random =  randomInt();
        Product rhel = new Product("test-product-" + random, 
                "Test Product " + random);
        ObjectFactory.get().store(rhel);
        return rhel;
    }
    
    public static EntitlementPool createEntitlementPool() {
        EntitlementPool pool = new EntitlementPool(createOwner(), createProduct(), 
                new Long(1000),
                TestUtil.createDate(2009, 11, 30), TestUtil.createDate(2015, 11, 30));
        return pool;
    }
    
    public static Entitlement createEntitlement(EntitlementPool pool) {
        Entitlement e = new Entitlement(pool, pool.getOwner(), pool.getStartDate());
        return e;
    }
    
    public static User createUser(Owner owner) {
        User u = new User(owner, "testuser" + randomInt(), "sekret");
        return u;
    }
    
    public static Date createDate(int year, int month, int day) {
        Calendar cal = Calendar.getInstance();
            
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, month);
        cal.set(Calendar.DATE, day);

        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        Date jsqlD = new Date(cal.getTime().getTime());
        return jsqlD;
    }
    
    public static String xmlToBase64String(String xml) {
        
        byte[] bytes = Base64.encode(xml);
        
        StringBuffer buf = new StringBuffer();
        for (byte b : bytes) {
            buf.append((char) Integer.parseInt(Integer.toHexString(b), 16));
        }
                
        return buf.toString();
    }

}
