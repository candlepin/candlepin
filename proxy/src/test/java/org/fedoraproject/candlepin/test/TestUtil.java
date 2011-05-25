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

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.apache.commons.codec.binary.Base64;
import org.fedoraproject.candlepin.auth.Access;
import org.fedoraproject.candlepin.auth.UserPrincipal;
import org.fedoraproject.candlepin.auth.permissions.Permission;
import org.fedoraproject.candlepin.model.CertificateSerial;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementCertificate;
import org.fedoraproject.candlepin.model.IdentityCertificate;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.OwnerPermission;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.ProductAttribute;
import org.fedoraproject.candlepin.model.ProvidedProduct;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.model.SubscriptionToken;
import org.fedoraproject.candlepin.model.User;

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
        return new Consumer("TestConsumer" + randomInt(), "User", owner, type);
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
        consumer.setFact("foo", "bar");
        consumer.setFact("foo1", "bar1");

        return consumer;
    }

    public static ConsumerType createConsumerType() {
        return new ConsumerType("test-consumer-type-" + randomInt());
    }
    
    private static final Random RANDOM = new Random(System.currentTimeMillis());
    
    public static int randomInt() {
        return RANDOM.nextInt(10000);
    }

    public static Product createProduct(String id, String name) {
        Product rhel = new Product(id, name);
        ProductAttribute a1 = new ProductAttribute("a1", "a1");
        rhel.addAttribute(a1);

        ProductAttribute a2 = new ProductAttribute("a2", "a2");
        rhel.addAttribute(a2);
        
        return rhel;
    }
    
    public static ProvidedProduct createProvidedProduct(String id, String name) {
        ProvidedProduct p = new ProvidedProduct(id, name);
        return p;
    }
    
    public static Product createProduct() {
        int random =  randomInt();
        return createProduct(String.valueOf(random), "test-product-" + random);
    }
    
    public static ProvidedProduct createProvidedProduct() {
        int random =  randomInt();
        return createProvidedProduct("test-provided-product-" + random,
            "Test Provided Product " + random);
    }
    
    public static Subscription createSubscription(Product product) {
        Owner owner = new Owner("Test Owner " + randomInt());
        return createSubscription(owner, product);
    }
    
    public static Subscription createSubscription() {
        return createSubscription(createProduct());
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
    
    public static Pool createPool(Product product) {
        return createPool(new Owner("Test Owner " + randomInt()), product);
    }

    public static Pool createPool(Owner owner, Product product) {
        return createPool(owner, product, 5);
    }

    public static Pool createPool(Owner owner, Product product, int quantity) {
        return createPool(owner, product, new HashSet<ProvidedProduct>(), quantity);
    }
    
    public static Pool createPool(Owner owner, Product product,
        Set<ProvidedProduct> providedProducts, int quantity) {

        Pool pool = new Pool(owner, product.getId(), product.getName(), 
            providedProducts, Long.valueOf(quantity),
            TestUtil.createDate(2009, 11, 30), TestUtil.createDate(2015, 11, 30),
            "SUB234598S", "ACC123");

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
        
        StringBuilder buf = new StringBuilder();
        for (byte b : bytes) {
            buf.append((char) Integer.parseInt(Integer.toHexString(b), 16));
        }
                
        return buf.toString();
    }


    public static UserPrincipal createPrincipal(String username, Owner owner, Access role) {
        return new UserPrincipal(username,  Arrays.asList(new Permission[] {
            new OwnerPermission(owner, role)}));
    }

    public static UserPrincipal createOwnerPrincipal() {
        Owner owner = new Owner("Test Owner " + randomInt());
        return createPrincipal("someuser", owner, Access.ALL);
    }


    public static Set<String> createSet(String productId) {
        Set<String> results = new HashSet<String>();
        results.add(productId);
        return results;
    }

    public static IdentityCertificate createIdCert() {
        IdentityCertificate idCert = new IdentityCertificate();
        CertificateSerial serial = new CertificateSerial(new Date());
        serial.setId(Long.valueOf(new Random().nextInt(1000000)));

        // totally arbitrary
        idCert.setId(String.valueOf(new Random().nextInt(1000000)));
        idCert.setKey("uh0876puhapodifbvj094");
        idCert.setCert("hpj-08ha-w4gpoknpon*)&^%#");
        idCert.setSerial(serial);
        return idCert;
    }

    public static Entitlement createEntitlement(Owner owner, Consumer consumer,
        Pool pool, EntitlementCertificate cert) {
        Entitlement toReturn = new Entitlement();
        toReturn.setOwner(owner);
        toReturn.setPool(pool);
        toReturn.setOwner(owner);
        toReturn.setConsumer(consumer);
        toReturn.setStartDate(pool.getStartDate());
        toReturn.setEndDate(pool.getEndDate());
        if (cert != null) {
            cert.setEntitlement(toReturn);
            toReturn.getCertificates().add(cert);
        }
        return toReturn;
    }

    public static Entitlement createEntitlement() {
        Owner owner = new Owner("Test Owner |" + randomInt());
        owner.setId(String.valueOf(RANDOM.nextLong()));
        return createEntitlement(owner, createConsumer(owner), createPool(
            owner, createProduct()), null);
    }
    
    public void addPermissionToUser(User u, Access role, Owner o) {
        // Check if a permission already exists for this verb and owner:
        
        
    }

}
