/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.test;

import static org.junit.Assert.*;

import org.candlepin.auth.Access;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.auth.permissions.OwnerPermission;
import org.candlepin.auth.permissions.Permission;
import org.candlepin.model.Branding;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Content;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductAttribute;
import org.candlepin.model.RulesCurator;
import org.candlepin.model.SourceSubscription;
import org.candlepin.model.User;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyPool;
import org.candlepin.model.dto.Subscription;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * TestUtil for creating various testing objects. Objects backed by the database
 * are not persisted, the caller is expected to persist the entities returned
 * and any dependent objects.
 */
public class TestUtil {

    private TestUtil() {
    }

    public static Owner createOwner() {
        return new Owner("Test Owner " + randomInt());
    }

    public static Owner createOwner(String key) {
        return new Owner(key);
    }

    public static Owner createOwner(String key, String name) {
        return new Owner(key, name);
    }

    public static Consumer createConsumer(ConsumerType type, Owner owner) {
        return new Consumer("TestConsumer" + randomInt(), "User", owner, type);
    }

    public static Consumer createConsumer(ConsumerType type, Owner owner, String username) {
        return new Consumer("TestConsumer" + randomInt(), username, owner, type);
    }

    /**
     * Create a consumer with a new owner
     *
     * @return Consumer
     */
    public static Consumer createConsumer() {
        return createConsumer(createConsumerType(), new Owner("Test Owner " + randomInt()));
    }

    /**
     * Create a consumer with a new owner
     *
     * @return Consumer
     */
    public static Consumer createConsumer(Owner owner) {
        Consumer consumer = new Consumer(
            "testconsumer" + randomInt(),
            "User",
            owner,
            createConsumerType()
        );
        consumer.setCreated(new Date());
        consumer.setFact("foo", "bar");
        consumer.setFact("foo1", "bar1");

        return consumer;
    }

    public static ConsumerType createConsumerType() {
        return new ConsumerType("test-consumer-type-" + randomInt());
    }

    private static final Random RANDOM = new Random(System.currentTimeMillis());

    public static int randomInt() {
        return Math.abs(RANDOM.nextInt());
    }

    public static String randomString() {
        return String.valueOf(randomInt());
    }

    public static Content createContent(String id) {
        return createContent(TestUtil.createOwner(), id);
    }

    public static Content createContent(Owner owner, String id) {
        return createContent(owner, id, "test-content-" + randomInt());
    }

    public static Content createContent(Owner owner, String id, String name) {
        return new Content(
            owner,
            name,
            id,
            name,
            "test-type",
            "test-vendor",
            "https://test.url.com",
            "https://gpg.test.url.com",
            "x86"
        );
    }

    public static Product createProduct(String id, String name, Owner owner) {
        Product rhel = new Product(id, name, null);

        ProductAttribute a1 = new ProductAttribute("a1", "a1");
        rhel.addAttribute(a1);

        ProductAttribute a2 = new ProductAttribute("a2", "a2");
        rhel.addAttribute(a2);

        return rhel;
    }

    public static Product createProduct(String id) {
        return createProduct(id, "test-product-" + randomInt(), createOwner());
    }

    public static Product createProduct(Owner o) {
        int random = randomInt();
        return createProduct(
            String.valueOf(random),
            "test-product-" + random,
            o
        );
    }

    public static Subscription createSubscription() {
        Owner owner = createOwner();
        Product product = createProduct(owner);

        return createSubscription(owner, product);
    }

    public static Subscription createSubscription(Owner owner, Product product) {
        return createSubscription(owner, product, new HashSet<Product>());
    }

    public static Subscription createSubscription(Owner owner, Product product,
        Set<Product> providedProducts) {

        Subscription sub = new Subscription(
            owner,
            product,
            providedProducts,
            1000L,
            createDate(2000, 1, 1),
            createDate(2050, 1, 1),
            createDate(2000, 1, 1)
        );

        return sub;
    }

    public static Pool createPool(Owner owner) {
        return createPool(owner, createProduct(owner));
    }

    public static Pool createPool(Product product) {
        return createPool(new Owner("Test Owner " + randomInt()), product);
    }

    public static Pool createPool(Owner owner, Product product) {
        return createPool(owner, product, 5);
    }

    public static Pool createPool(Owner owner, Product product, int quantity) {
        return createPool(owner, product, null, quantity);
    }

    public static Pool createPool(Owner owner, Product product, Collection<Product> providedProducts,
        int quantity) {

        String random = String.valueOf(randomInt());

        Set<Product> provided = new HashSet<Product>();
        if (providedProducts != null) {
            provided.addAll(providedProducts);
        }

        Pool pool = new Pool(
            owner,
            product,
            provided,
            Long.valueOf(quantity),
            TestUtil.createDate(2009, 11, 30),
            TestUtil.createDate(Calendar.getInstance().get(Calendar.YEAR) + 10, 11, 30),
            "SUB234598S" + random,
            "ACC123" + random,
            "ORD222" + random
        );

        pool.setSourceSubscription(new SourceSubscription("SUB234598S" + random, "master" + random));

        return pool;
    }

    public static Pool createPool(Owner owner, Product product, Collection<Product> providedProducts,
        Product derivedProduct, Collection<Product> subProvidedProducts, int quantity) {

        Pool pool = createPool(owner, product, providedProducts, quantity);
        Set<Product> subProvided = new HashSet<Product>();
        if (subProvidedProducts != null) {
            subProvided.addAll(subProvidedProducts);
        }

        pool.setDerivedProduct(derivedProduct);
        pool.setDerivedProvidedProducts(subProvided);

        return pool;
    }

    public static Date createDate(int year, int month, int day) {
        Calendar cal = Calendar.getInstance();

        cal.set(Calendar.YEAR, year);
        // Watch out! Java expects month as 0-11
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

        // byte[] bytes = Base64.encode(xml);
        Base64 encoder = new Base64();
        byte[] bytes = encoder.encode(xml.getBytes());

        StringBuilder buf = new StringBuilder();
        for (byte b : bytes) {
            buf.append((char) Integer.parseInt(Integer.toHexString(b), 16));
        }

        return buf.toString();
    }

    public static User createUser(String username, String password,
        boolean superAdmin) {
        username = (username == null) ? "user-" + randomInt() : username;
        password = (password == null) ? "pass-" + randomInt() : password;
        return new User(username, password, superAdmin);
    }

    public static UserPrincipal createPrincipal(String username, Owner owner, Access role) {
        return new UserPrincipal(
            username,
            Arrays.asList(new Permission[]{ new OwnerPermission(owner, role) }),
            false
        );
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
        return createIdCert(new Date());
    }

    public static IdentityCertificate createIdCert(Date expiration) {
        IdentityCertificate idCert = new IdentityCertificate();
        CertificateSerial serial = new CertificateSerial(expiration);
        serial.setId(Long.valueOf(new Random().nextInt(1000000)));

        // totally arbitrary
        idCert.setId(String.valueOf(new Random().nextInt(1000000)));
        idCert.setKey("uh0876puhapodifbvj094");
        idCert.setCert("hpj-08ha-w4gpoknpon*)&^%#");
        idCert.setSerial(serial);
        return idCert;
    }

    public static Entitlement createEntitlement(Owner owner, Consumer consumer, Pool pool,
        EntitlementCertificate cert) {

        Entitlement toReturn = new Entitlement();
        toReturn.setOwner(owner);
        toReturn.setPool(pool);

        consumer.addEntitlement(toReturn);

        if (cert != null) {
            cert.setEntitlement(toReturn);
            toReturn.getCertificates().add(cert);
        }

        return toReturn;
    }

    public static Entitlement createEntitlement() {
        Owner owner = new Owner("Test Owner |" + randomInt());
        owner.setId(String.valueOf(RANDOM.nextLong()));
        return createEntitlement(
            owner,
            createConsumer(owner),
            createPool(owner, createProduct(owner)),
            null
        );
    }

    public void addPermissionToUser(User u, Access role, Owner o) {
        // Check if a permission already exists for this verb and owner:
    }

    /**
     * Returns a pool which will look like it was created from the given subscription
     * during refresh pools.
     *
     * @param sub source subscription
     * @return pool for subscription
     */
    public static Pool copyFromSub(Subscription sub) {
        Pool p = new Pool(sub.getOwner(),
            sub.getProduct(),
            new HashSet<Product>(sub.getProvidedProducts()),
            sub.getQuantity(),
            sub.getStartDate(),
            sub.getEndDate(),
            sub.getContractNumber(),
            sub.getAccountNumber(),
            sub.getOrderNumber()
        );
        p.setUpstreamPoolId(sub.getUpstreamPoolId());
        p.setSourceSubscription(new SourceSubscription(sub.getId(), "master"));

        // Copy sub-product data if there is any:
        p.setDerivedProduct(sub.getDerivedProduct());
        p.setDerivedProvidedProducts(new HashSet<Product>(sub.getDerivedProvidedProducts()));

        for (Branding b : sub.getBranding()) {
            p.getBranding().add(new Branding(b.getProductId(), b.getType(), b.getName()));
        }

        return p;
    }

    /**
     * @param pool source pool
     * @return pool the clone pool
     */
    public static Pool clone(Pool pool) {
        Pool p = new Pool(pool.getOwner(),
            pool.getProduct(),
            new HashSet<Product>(pool.getProvidedProducts()),
            pool.getQuantity(),
            pool.getStartDate(),
            pool.getEndDate(),
            pool.getContractNumber(),
            pool.getAccountNumber(),
            pool.getOrderNumber()
        );

        p.setSourceSubscription(
            new SourceSubscription(pool.getSubscriptionId(), pool.getSubscriptionSubKey()));

        // Copy sub-product data if there is any:
        p.setDerivedProduct(pool.getDerivedProduct());

        for (Branding b : pool.getBranding()) {
            p.getBranding().add(new Branding(b.getProductId(), b.getType(), b.getName()));
        }

        return p;
    }

    public static ActivationKey createActivationKey(Owner owner,
        List<Pool> pools) {
        ActivationKey key = new ActivationKey();
        key.setOwner(owner);
        key.setName("A Test Key");
        key.setServiceLevel("TestLevel");
        key.setDescription("A test description for the test key.");
        if (pools != null) {
            Set<ActivationKeyPool> akPools = new HashSet<ActivationKeyPool>();
            for (Pool p : pools) {
                akPools.add(new ActivationKeyPool(key, p, (long) 1));
            }
            key.setPools(akPools);
        }

        return key;
    }

    /*
     * Creates a fake rules blob with a version that matches the current API number.
     */
    public static String createRulesBlob(int minorVersion) {
        return "// Version: " + RulesCurator.RULES_API_VERSION + "." + minorVersion +
            "\n//somerules";
    }

    public static boolean isJsonEqual(String one, String two) throws JsonProcessingException, IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tree1 = mapper.readTree(one);
        JsonNode tree2 = mapper.readTree(two);
        return tree1.equals(tree2);
    }

    public static String getStringOfSize(int size) {
        char[] charArray = new char[size];
        Arrays.fill(charArray, 'x');
        return new String(charArray);
    }

    public static void cleanupDir(String dir, String basename) {
        File tempDir = new File(dir);

        for (File f : tempDir.listFiles()) {
            if (f.getName().startsWith(basename)) {
                try {
                    FileUtils.deleteDirectory(f);
                }
                catch (IOException e) {
                    throw new RuntimeException(
                        "Failed to cleanup directory: " + dir, e);
                }
            }
        }
    }

    public static Set<Product> stubChangedProducts(Product ... products) {
        Set<Product> result = new HashSet<Product>();
        for (Product p : products) {
            result.add(p);
        }
        return result;
    }

    public static void assertPoolsAreEqual(Pool pool1, Pool pool2) {
        assertEquals(pool1.getAccountNumber(), pool2.getAccountNumber());
        assertEquals(pool1.getContractNumber(), pool2.getContractNumber());
        assertEquals(pool1.getOrderNumber(), pool2.getOrderNumber());
        assertEquals(pool1.getType(), pool2.getType());
        assertEquals(pool1.getOwner(), pool2.getOwner());
        assertEquals(pool1.getQuantity(), pool2.getQuantity());
        assertEquals(pool1.getActiveSubscription(), pool2.getActiveSubscription());
        assertEquals(pool1.getSourceEntitlement(), pool2.getSourceEntitlement());
        assertEquals(pool1.getSourceStack(), pool2.getSourceStack());
        assertEquals(pool1.getSubscriptionId(), pool2.getSubscriptionId());
        assertEquals(pool1.getSubscriptionSubKey(), pool2.getSubscriptionSubKey());
        assertEquals(pool1.getStartDate(), pool2.getStartDate());
        assertEquals(pool1.getEndDate(), pool2.getEndDate());
        assertEquals(pool1.getProduct(), pool2.getProduct());
        assertEquals(pool1.getProvidedProducts(), pool2.getProvidedProducts());
        assertEquals(pool1.getDerivedProvidedProducts(), pool2.getDerivedProvidedProducts());
        assertEquals(pool1.getProvidedProductDtos(), pool2.getProvidedProductDtos());
        assertEquals(pool1.getDerivedProvidedProductDtos(), pool2.getDerivedProvidedProductDtos());
        assertEquals(pool1.getAttributes(), pool2.getAttributes());
        assertEquals(pool1.getEntitlements(), pool2.getEntitlements());
        assertEquals(pool1.getConsumed(), pool2.getConsumed());
        assertEquals(pool1.getExported(), pool2.getExported());
        assertEquals(pool1.getBranding(), pool2.getBranding());
        assertEquals(pool1.getCalculatedAttributes(), pool2.getCalculatedAttributes());
        assertEquals(pool1.isMarkedForDelete(), pool2.isMarkedForDelete());
        assertEquals(pool1.getImportedProductId(), pool2.getImportedProductId());
        assertEquals(pool1.getUpstreamConsumerId(), pool2.getUpstreamConsumerId());
        assertEquals(pool1.getUpstreamEntitlementId(), pool2.getUpstreamEntitlementId());
        assertEquals(pool1.getUpstreamPoolId(), pool2.getUpstreamPoolId());
        assertEquals(pool1.getCertificate(), pool2.getCertificate());
        assertEquals(pool1.getCdn(), pool2.getCdn());

    }
}
