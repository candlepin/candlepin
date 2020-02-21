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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

import org.candlepin.auth.Access;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.auth.permissions.OwnerPermission;
import org.candlepin.auth.permissions.Permission;
import org.candlepin.dto.api.v1.ConsumerDTO;
import org.candlepin.dto.api.v1.ConsumerTypeDTO;
import org.candlepin.dto.api.v1.ContentDTO;
import org.candlepin.dto.api.v1.GuestIdDTO;
import org.candlepin.dto.api.v1.OwnerDTO;
import org.candlepin.dto.api.v1.ProductDTO;
import org.candlepin.dto.api.v1.ProductDTO.ProductContentDTO;
import org.candlepin.model.AbstractHibernateCurator;
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
import org.candlepin.model.RulesCurator;
import org.candlepin.model.SourceSubscription;
import org.candlepin.model.User;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyPool;
import org.candlepin.model.dto.ContentData;
import org.candlepin.model.dto.ProductContentData;
import org.candlepin.model.dto.ProductData;
import org.candlepin.model.dto.Subscription;
import org.candlepin.util.Transactional;
import org.candlepin.util.Util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;




/**
 * TestUtil for creating various testing objects. Objects backed by the database
 * are not persisted, the caller is expected to persist the entities returned
 * and any dependent objects.
 */
public class TestUtil {

    private TestUtil() {
    }

    public static Owner createOwner(String key, String name) {
        Owner owner = new Owner(key, name);
        owner.setId(key + "-id");
        return owner;
    }

    public static Owner createOwner(String key) {
        return createOwner(key, key);
    }

    public static Owner createOwner() {
        return createOwner("Test Owner " + randomInt());
    }

    public static Consumer createConsumer(ConsumerType type, Owner owner) {
        return new Consumer("TestConsumer" + randomInt(), "User", owner, type).setUuid(Util.generateUUID());
    }

    public static ConsumerDTO createConsumerDTO(ConsumerTypeDTO type, OwnerDTO owner) {
        return createConsumerDTO("TestConsumer" + randomInt(), "User", owner, type);
    }

    public static ConsumerDTO createConsumerDTO(String name, String userName, OwnerDTO owner,
        ConsumerTypeDTO type) {
        return (new ConsumerDTO()).setName(name)
            .setUsername(userName)
            .setOwner(owner)
            .setType(type)
            .setAutoheal(true)
            .setServiceLevel("")
            .setEntitlementCount(0L)
            .setFacts(new HashMap<>())
            .setInstalledProducts(new HashSet<>())
            .setGuestIds(new ArrayList<>());
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
        return createConsumer(createConsumerType(), createOwner("Test Owner " + randomInt()));
    }

    public static Consumer createDistributor() {
        return createDistributor(createOwner());
    }

    public static Consumer createDistributor(Owner owner) {
        ConsumerType ctype = new ConsumerType(ConsumerType.ConsumerTypeEnum.CANDLEPIN);
        ctype.setId("test-ctype-" + randomInt());

        return createConsumer(ctype, owner);
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
        ).setUuid(Util.generateUUID());
        consumer.setCreated(new Date());
        consumer.setFact("foo", "bar");
        consumer.setFact("foo1", "bar1");

        return consumer;
    }

    public static ConsumerType createConsumerType() {
        ConsumerType ctype = new ConsumerType("test-consumer-type-" + randomInt());
        ctype.setId("test-ctype-" + randomInt());

        return ctype;
    }

    private static final Random RANDOM = new Random(System.currentTimeMillis());

    public static int randomInt() {
        return Math.abs(RANDOM.nextInt());
    }

    public static int randomInt(int max) {
        return Math.abs(RANDOM.nextInt(max));
    }

    public static String randomString() {
        return String.valueOf(randomInt());
    }

    public static String randomString(String prefix) {
        return String.format("%s-%06d", prefix, RANDOM.nextInt(1000000));
    }

    public static Content createContent(String id, String name) {
        Content content = new Content(id, name, "test-type", "test-label", "test-vendor");
        content.setContentUrl("https://test.url.com");
        content.setGpgUrl("https://gpg.test.url.com");
        content.setArches("x86");

        return content;
    }

    public static Content createContent(String id) {
        return createContent(id, id);
    }

    public static Content createContent() {
        return createContent("test-content-" + randomInt());
    }

    public static Content createContent(ContentDTO dto) {
        Content content = null;

        if (dto != null) {
            content = createContent(dto.getId(), dto.getName());

            content.setUuid(dto.getUuid());
            content.setType(dto.getType());
            content.setLabel(dto.getLabel());
            content.setVendor(dto.getVendor());
            content.setContentUrl(dto.getContentUrl());
            content.setRequiredTags(dto.getRequiredTags());
            content.setReleaseVersion(dto.getReleaseVersion());
            content.setGpgUrl(dto.getGpgUrl());
            content.setMetadataExpiration(dto.getMetadataExpiration());
            content.setModifiedProductIds(dto.getModifiedProductIds());
            content.setArches(dto.getArches());
            content.setLocked(dto.isLocked());
        }

        return content;
    }

    public static Content createContent(ContentData cdata) {
        Content content = null;

        if (cdata != null) {
            content = createContent(cdata.getId(), cdata.getName());

            content.setUuid(cdata.getUuid());
            content.setType(cdata.getType());
            content.setLabel(cdata.getLabel());
            content.setVendor(cdata.getVendor());
            content.setContentUrl(cdata.getContentUrl());
            content.setRequiredTags(cdata.getRequiredTags());
            content.setReleaseVersion(cdata.getReleaseVersion());
            content.setGpgUrl(cdata.getGpgUrl());
            content.setMetadataExpiration(cdata.getMetadataExpiration());
            content.setModifiedProductIds(cdata.getModifiedProductIds());
            content.setArches(cdata.getArches());
            content.setLocked(cdata.isLocked());
        }

        return content;
    }

    public static ContentDTO createContentDTO(String id, String name) {
        ContentDTO dto = new ContentDTO();

        dto.setId(id);
        dto.setName(name);

        return dto;
    }

    public static ContentDTO createContentDTO(String id) {
        return createContentDTO(id, id);
    }

    public static ContentDTO createContentDTO() {
        return createContentDTO("test-content-" + randomInt());
    }

    public static Product createProduct(String id, String name) {
        return new Product(id, name, null);

        // TODO:
        // Leaving these comments in for the moment for reference when a few tests that are
        // expecting these attributes break.

        // ProductAttribute a1 = new ProductAttribute("a1", "a1");
        // rhel.addAttribute(a1);

        // ProductAttribute a2 = new ProductAttribute("a2", "a2");
        // rhel.addAttribute(a2);
    }

    public static Product createProduct(String id) {
        return createProduct(id, "test-product-" + randomInt());
    }

    public static Product createProduct() {
        int random = randomInt();
        return createProduct(String.valueOf(random), "test-product-" + random);
    }

    public static Product createProduct(ProductDTO dto) {
        Product product = null;

        if (dto != null) {
            product = new Product(dto.getId(), dto.getName());

            product.setUuid(dto.getUuid());
            product.setMultiplier(dto.getMultiplier());

            product.setAttributes(dto.getAttributes());

            if (dto.getProductContent() != null) {
                for (ProductContentDTO pcd : dto.getProductContent()) {
                    if (pcd != null) {
                        Content content = createContent((ContentDTO) pcd.getContent());

                        if (content != null) {
                            product.addContent(content, pcd.isEnabled() != null ? pcd.isEnabled() : true);
                        }
                    }
                }
            }

            product.setDependentProductIds(dto.getDependentProductIds());
            product.setLocked(dto.isLocked() != null ? dto.isLocked() : false);
        }

        return product;
    }

    public static Product createProduct(ProductData pdata) {
        Product product = null;

        if (pdata != null) {
            product = new Product(pdata.getId(), pdata.getName());

            product.setUuid(pdata.getUuid());
            product.setMultiplier(pdata.getMultiplier());

            product.setAttributes(pdata.getAttributes());

            if (pdata.getProductContent() != null) {
                for (ProductContentData pcd : pdata.getProductContent()) {
                    if (pcd != null) {
                        Content content = createContent((ContentData) pcd.getContent());

                        if (content != null) {
                            product.addContent(content, pcd.isEnabled() != null ? pcd.isEnabled() : true);
                        }
                    }
                }
            }

            product.setDependentProductIds(pdata.getDependentProductIds());
            product.setLocked(pdata.isLocked() != null ? pdata.isLocked() : false);
        }

        return product;
    }

    public static ProductDTO createProductDTO(String id, String name) {
        ProductDTO dto = new ProductDTO();

        dto.setId(id);
        dto.setName(name);

        return dto;
    }

    public static ProductDTO createProductDTO(String id) {
        return createProductDTO(id, id);
    }

    public static ProductDTO createProductDTO() {
        return createProductDTO("test-product-" + randomInt());
    }

    public static Subscription createSubscription() {
        Owner owner = createOwner();
        Product product = createProduct();

        return createSubscription(owner, product);
    }

    public static Subscription createSubscription(Owner owner, Product product) {
        return createSubscription(owner, product.toDTO());
    }

    public static Subscription createSubscription(Owner owner, ProductData dto) {
        Subscription sub = new Subscription(
            owner,
            dto,
            1000L,
            createDate(2000, 1, 1),
            createDate(2050, 1, 1),
            createDate(2000, 1, 1)
        );

        sub.setId("test-sub-" + randomInt());

        return sub;
    }

    public static Pool createPool(Owner owner) {
        return createPool(owner, createProduct());
    }

    public static Pool createPool(Product product) {
        return createPool(new Owner("Test Owner " + randomInt()), product);
    }

    public static Pool createPool(Owner owner, Product product) {
        return createPool(owner, product, 5);
    }

    public static Pool createPool(Owner owner, Product product, int quantity) {
        String random = String.valueOf(randomInt());

        Pool pool = new Pool();
        pool.setOwner(owner);
        pool.setProduct(product);
        pool.setQuantity(Long.valueOf(quantity));
        pool.setStartDate(TestUtil.createDate(2009, 11, 30));
        pool.setEndDate(
            TestUtil.createDate(Calendar.getInstance().get(Calendar.YEAR) + 10, 11, 30));
        pool.setContractNumber("SUB234598S" + random);
        pool.setAccountNumber("ACC123" + random);
        pool.setOrderNumber("ORD222" + random);
        pool.setSourceSubscription(new SourceSubscription("SUB234598S" + random, "master" + random));

        return pool;
    }

    public static Pool createPool(Owner owner, Product product, Product derivedProduct, int quantity) {
        Pool pool = createPool(owner, product, quantity);

        if (derivedProduct != null) {
            pool.setDerivedProduct(derivedProduct);
        }

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

    public static Date createFutureDate(int afterYears) {
        LocalDate now = LocalDate.now();
        return createDate(now.getYear() + afterYears, now.getMonthValue(), now.getDayOfMonth());
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

    public static UserPrincipal createOwnerPrincipal(Owner owner) {
        return createPrincipal("someuser", owner, Access.ALL);
    }

    public static UserPrincipal createOwnerPrincipal() {
        Owner owner = createOwner("Test Owner " + randomInt());
        return createPrincipal("someuser", owner, Access.ALL);
    }


    public static Set<String> createSet(String productId) {
        Set<String> results = new HashSet<>();
        results.add(productId);
        return results;
    }

    public static IdentityCertificate createIdCert() {
        return createIdCert(new Date());
    }

    public static IdentityCertificate createIdCert(Date expiration) {
        IdentityCertificate idCert = new IdentityCertificate();
        CertificateSerial serial = new CertificateSerial(expiration);

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
        toReturn.setId(Util.generateDbUUID());
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
            createPool(owner, createProduct()),
            null
        );
    }

    public static GuestIdDTO createGuestIdDTO(String guestId) {
        GuestIdDTO dto = new GuestIdDTO()
            .setGuestId(guestId)
            .setAttributes(Collections.<String, String>emptyMap());

        return dto;
    }

    public static Branding createProductBranding(Product product) {
        Branding productBranding = new Branding();
        String suffix = randomString();
        productBranding.setId("test-id-" + suffix);
        productBranding.setProduct(product);
        productBranding.setName("test-name-" + suffix);
        productBranding.setType("test-type-" + suffix);
        productBranding.setProductId("test-product-id-" + suffix);
        return productBranding;
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
        Product product = createProduct((ProductData) sub.getProduct());
        Product derivedProduct = createProduct((ProductData) sub.getDerivedProduct());

        List<Product> providedProducts = new LinkedList<>();
        if (sub.getProvidedProducts() != null) {
            for (ProductData pdata : sub.getProvidedProducts()) {
                if (pdata != null) {
                    providedProducts.add(TestUtil.createProduct(pdata));
                }
            }
        }

        List<Product> derivedProvidedProducts = new LinkedList<>();
        if (sub.getDerivedProvidedProducts() != null) {
            for (ProductData pdata : sub.getDerivedProvidedProducts()) {
                if (pdata != null) {
                    derivedProvidedProducts.add(TestUtil.createProduct(pdata));
                }
            }
        }

        Pool pool = new Pool();
        pool.setOwner(sub.getOwner());
        pool.setQuantity(sub.getQuantity());
        pool.setStartDate(sub.getStartDate());
        pool.setEndDate(sub.getEndDate());
        pool.setAccountNumber(sub.getContractNumber());
        pool.setAccountNumber(sub.getAccountNumber());
        pool.setOrderNumber(sub.getOrderNumber());

        if (product != null) {
            product.setProvidedProducts(providedProducts);
            pool.setProduct(product);
        }

        if (derivedProduct != null) {
            derivedProduct.setProvidedProducts(derivedProvidedProducts);
            pool.setDerivedProduct(derivedProduct);
        }

        if (sub.getId() != null) {
            pool.setSourceSubscription(new SourceSubscription(sub.getId(), "master"));
        }

        pool.setUpstreamPoolId(sub.getUpstreamPoolId());
        pool.setUpstreamConsumerId(sub.getUpstreamConsumerId());
        pool.setUpstreamEntitlementId(sub.getUpstreamEntitlementId());

        return pool;
    }

    /**
     * @param pool source pool
     * @return pool the clone pool
     */
    public static Pool clone(Pool pool) {
        Pool p = new Pool(pool.getOwner(),
            pool.getProduct(), new HashSet<>(pool.getProvidedProducts()),
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
        if (pool.getDerivedProduct() != null) {
            p.setDerivedProduct((Product) pool.getDerivedProduct().clone());
        }

        if (pool.getProduct() != null) {
            p.setProduct((Product) pool.getProduct().clone());
        }

        return p;
    }

    public static ActivationKey createActivationKey(Owner owner, List<Pool> pools) {
        ActivationKey key = new ActivationKey();
        key.setOwner(owner);
        key.setName("A Test Key");
        key.setServiceLevel("TestLevel");
        key.setDescription("A test description for the test key.");
        if (pools != null) {
            Set<ActivationKeyPool> akPools = new HashSet<>();
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

    public static Map<String, Product> stubChangedProducts(Product ... products) {
        Map<String, Product> result = new HashMap<>();
        for (Product p : products) {
            result.put(p.getId(), p);
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
        assertEquals(pool1.getCalculatedAttributes(), pool2.getCalculatedAttributes());
        assertEquals(pool1.isMarkedForDelete(), pool2.isMarkedForDelete());
        assertEquals(pool1.getUpstreamConsumerId(), pool2.getUpstreamConsumerId());
        assertEquals(pool1.getUpstreamEntitlementId(), pool2.getUpstreamEntitlementId());
        assertEquals(pool1.getUpstreamPoolId(), pool2.getUpstreamPoolId());
        assertEquals(pool1.getCertificate(), pool2.getCertificate());
        assertEquals(pool1.getCdn(), pool2.getCdn());

    }

    public static void mockTransactionalFunctionality(EntityManager mockEntityManager,
        AbstractHibernateCurator mockCurator) {

        EntityTransaction transaction = new EntityTransaction() {
            private boolean active;
            private boolean rollbackOnly;

            @Override
            public void begin() {
                this.active = true;
            }

            @Override
            public void commit() {
                if (!this.active) {
                    throw new IllegalStateException();
                }

                this.active = false;
            }

            @Override
            public boolean getRollbackOnly() {
                return this.rollbackOnly;
            }

            @Override
            public boolean isActive() {
                return this.active;
            }

            @Override
            public void rollback() {
                if (!this.active) {
                    throw new IllegalStateException();
                }

                this.active = false;
            }

            @Override
            public void setRollbackOnly() {
                this.rollbackOnly = true;
            }
        };

        doReturn(transaction).when(mockEntityManager).getTransaction();

        doAnswer((Answer<Transactional>) iom -> {
            Transactional.Action action = (Transactional.Action) iom.getArguments()[0];
            return new Transactional(mockEntityManager).wrap(action);
        }).when(mockCurator).transactional(any());
    }
}
