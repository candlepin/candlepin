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

import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.apache.commons.codec.binary.Base64;
import org.candlepin.auth.Access;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.auth.permissions.Permission;
import org.candlepin.model.ActivationKey;
import org.candlepin.model.ActivationKeyPool;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerPermission;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductAttribute;
import org.candlepin.model.ProvidedProduct;
import org.candlepin.model.RulesCurator;
import org.candlepin.model.SubProvidedProduct;
import org.candlepin.model.Subscription;
import org.candlepin.model.User;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.ObjectMapper;

/**
 * TestUtil for creating various testing objects. Objects backed by the database
 * are not persisted, the caller is expected to persist the entities returned
 * and any dependent objects.
 */
public class TestUtil {

    private TestUtil() {
    }

    public static Consumer createConsumer(ConsumerType type, Owner owner) {
        return new Consumer("TestConsumer" + randomInt(), "User", owner, type);
    }

    /**
     * Create a consumer with a new owner
     *
     * @return Consumer
     */
    public static Consumer createConsumer() {
        return createConsumer(createConsumerType(), new Owner("Test Owner " +
            randomInt()));
    }

    /**
     * Create a consumer with a new owner
     *
     * @return Consumer
     */
    public static Consumer createConsumer(Owner owner) {
        Consumer consumer = new Consumer("testconsumer" + randomInt(), "User",
            owner, createConsumerType());
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
        int random = randomInt();
        return createProduct(String.valueOf(random), "test-product-" + random);
    }

    public static ProvidedProduct createProvidedProduct() {
        int random = randomInt();
        return createProvidedProduct("test-provided-product-" + random,
            "Test Provided Product " + random);
    }

    public static SubProvidedProduct createSubProvidedProduct() {
        int random = randomInt();
        return new SubProvidedProduct("test-sub-provided-product-" + random,
            "Test Sub Provided Product " + random);
    }

    public static Subscription createSubscription(Product product) {
        Owner owner = new Owner("Test Owner " + randomInt());
        return createSubscription(owner, product);
    }

    public static Subscription createSubscription() {
        return createSubscription(createProduct());
    }

    public static Subscription createSubscription(Owner owner, Product product) {
        return createSubscription(owner, product, new HashSet<Product>());
    }

    public static Subscription createSubscription(Owner owner, Product product,
        Set<Product> providedProducts) {
        Subscription sub = new Subscription(owner, product,
            providedProducts, 1000L, createDate(2000, 1, 1), createDate(
                2050, 1, 1), createDate(2000, 1, 1));
        return sub;
    }

    public static Pool createPool(Product product) {
        return createPool(new Owner("Test Owner " + randomInt()), product);
    }

    public static Pool createPool(Owner owner, Product product) {
        return createPool(owner, product, 5);
    }

    public static Pool createPool(Owner owner, Product product, int quantity) {
        return createPool(owner, product, new HashSet<ProvidedProduct>(),
            quantity);
    }

    public static Pool createPool(Owner owner, Product product,
        Set<ProvidedProduct> providedProducts, int quantity) {

        Pool pool = new Pool(owner, product.getId(), product.getName(),
            providedProducts, Long.valueOf(quantity), TestUtil.createDate(2009,
                11, 30), TestUtil.createDate(2015, 11, 30), "SUB234598S",
            "ACC123", "ORD222");

        // Simulate copying product attributes to the pool.
        if (product != null) {
            for (ProductAttribute attr : product.getAttributes()) {
                pool.setProductAttribute(attr.getName(), attr.getValue(), product.getId());
            }
        }
        return pool;
    }

    public static Pool createPool(Owner owner, Product product,
        Set<ProvidedProduct> providedProducts, String subProductId,
        Set<SubProvidedProduct> subProvidedProducts, int quantity) {
        Pool pool = createPool(owner, product, providedProducts, quantity);
        pool.setSubProductId(subProductId);
        pool.setSubProvidedProducts(subProvidedProducts);
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

    public static UserPrincipal createPrincipal(String username, Owner owner,
        Access role) {
        return new UserPrincipal(
            username,
            Arrays.asList(new Permission[]{ new OwnerPermission(owner, role) }),
            false);
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
        return createEntitlement(owner, createConsumer(owner),
            createPool(owner, createProduct()), null);
    }

    public void addPermissionToUser(User u, Access role, Owner o) {
        // Check if a permission already exists for this verb and owner:
    }

    public static ActivationKey createActivationKey(Owner owner,
        List<Pool> pools) {
        ActivationKey key = new ActivationKey();
        key.setOwner(owner);
        key.setName("A Test Key");
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

    public static boolean isJsonEqual(String one, String two)
        throws JsonProcessingException, IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tree1 = mapper.readTree(one);
        JsonNode tree2 = mapper.readTree(two);
        return tree1.equals(tree2);
    }
}
