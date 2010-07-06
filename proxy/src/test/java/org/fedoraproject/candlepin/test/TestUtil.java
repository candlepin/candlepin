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
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.apache.commons.codec.binary.Base64;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.ProductAttribute;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.model.SubscriptionToken;

/**
 * TestUtil for creating various testing objects.
 * 
 * Objects backed by the database are not persisted, the caller is expected to persist
 * the entities returned and any dependent objects.
 */
public class TestUtil {
    
    private TestUtil() {
    }

    public static Consumer createConsumer(ConsumerType type, Owner owner) {
        return new Consumer("Test Consumer " + randomInt(), "User", owner, type);
    }

    /**
     * Create a consumer with a new owner
     * @return Consumer
     */
    public static Consumer createConsumer() {
        return createConsumer(createConsumerType(), new Owner("Test Owner " + randomInt()));
    }
    
    /**
     * Create a consumer with a new owner
     * @return Consumer
     */
    public static Consumer createConsumer(Owner owner) {
        ConsumerType consumerType = new ConsumerType("test-consumer-type-" +
                randomInt());

        Consumer consumer = new Consumer("testconsumer" + randomInt(),
            "User", owner, consumerType);
        consumer.setMetadataField("foo", "bar");
        consumer.setMetadataField("foo1", "bar1");

        return consumer;
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
        rhel.setId("test-product-" + random);
        
        ProductAttribute a1 = new ProductAttribute("a1", "a1");
        a1.setId(new Long(1001)); // faked
        rhel.addAttribute(a1);

        ProductAttribute a2 = new ProductAttribute("a2", "a2");
        a2.setId(new Long(1002)); // faked
        rhel.addAttribute(a2);
        
        return rhel;
    }
    
    public static Subscription createSubscription(Product product) {
        Owner owner = new Owner("Test Owner " + randomInt());
        return createSubscription(owner, product);
    }
    
    public static Subscription createSubscription(Owner owner, Product product) {
        Subscription sub = new Subscription(owner,
            product, new HashSet<Product>(), 1000L, createDate(2000, 1, 1),
            createDate(2050, 1, 1), createDate(2000, 1, 1));
        return sub;
    }
    
    public static SubscriptionToken createSubscriptionToken() {
        
        SubscriptionToken st = new SubscriptionToken();
        st.setToken("this_is_a_test_token");
        
        return st;
        
    }
    
    public static Pool createEntitlementPool(Product product) {
        return createEntitlementPool(new Owner("Test Owner " + randomInt()), product);
    }

    public static Pool createEntitlementPool(Owner owner, Product product) {
        return createEntitlementPool(owner, product, 5);
    }

    public static Pool createEntitlementPool(Owner owner, Product product, int quantity) {
        return createEntitlementPool(owner, product.getId(), new HashSet<String>(), 
            quantity);
    }
    
    public static Pool createEntitlementPool(Owner owner, String productId, 
        Set<String> productIds, int quantity) {

        Pool pool = new Pool(owner, productId, productIds, new Long(quantity),
            TestUtil.createDate(2009, 11, 30), TestUtil.createDate(2015, 11, 30));
        return pool;
    }
    
    public static Date createDate(int year, int month, int day) {
        Calendar cal = Calendar.getInstance();
            
        cal.set(Calendar.YEAR, year);
        // Watchout, Java expects month as 0-11
        cal.set(Calendar.MONTH, month - 1);
        cal.set(Calendar.DATE, day);

        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        Date jsqlD = new Date(cal.getTime().getTime());
        return jsqlD;
    }
    
    public static String xmlToBase64String(String xml) {
        
//        byte[] bytes = Base64.encode(xml);
        Base64 encoder = new Base64();
        byte [] bytes = encoder.encode(xml.getBytes());
        
        StringBuffer buf = new StringBuffer();
        for (byte b : bytes) {
            buf.append((char) Integer.parseInt(Integer.toHexString(b), 16));
        }
                
        return buf.toString();
    }

    public static Set<String> createSet(String productId) {
        Set<String> results = new HashSet<String>();
        results.add(productId);
        return results;
    }
}
