/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import static org.candlepin.model.SourceSubscription.PRIMARY_POOL_SUB_KEY;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

import org.candlepin.auth.Access;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.auth.permissions.OwnerPermission;
import org.candlepin.auth.permissions.Permission;
import org.candlepin.dto.api.server.v1.ConsumerDTO;
import org.candlepin.dto.api.server.v1.ConsumerTypeDTO;
import org.candlepin.dto.api.server.v1.ContentDTO;
import org.candlepin.dto.api.server.v1.GuestIdDTO;
import org.candlepin.dto.api.server.v1.NestedOwnerDTO;
import org.candlepin.dto.api.server.v1.OwnerDTO;
import org.candlepin.dto.api.server.v1.ProductContentDTO;
import org.candlepin.dto.api.server.v1.ProductDTO;
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
import org.candlepin.model.dto.ContentData;
import org.candlepin.model.dto.ProductContentData;
import org.candlepin.model.dto.ProductData;
import org.candlepin.model.dto.Subscription;
import org.candlepin.resource.util.InfoAdapter;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.util.ObjectMapperFactory;
import org.candlepin.util.Transactional;
import org.candlepin.util.Util;
import org.candlepin.util.function.CheckedRunnable;
import org.candlepin.util.function.CheckedSupplier;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;


/**
 * TestUtil for creating various testing objects. Objects backed by the database are not persisted,
 * the caller is expected to persist the entities returned and any dependent objects.
 */
public class TestUtil {
    private static final Random RANDOM = new Random(System.currentTimeMillis());

    /** Character set consisting of upper- and lower-case alphabetical ASCII characters */
    public static final String CHARSET_ALPHABETICAL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    /** Character set consisting of numeric ASCII characters */
    public static final String CHARSET_NUMERIC = "0123456789";

    /** Character set consisting of characters representing base-16 (hexidecimal) values (0-9, A-F) */
    public static final String CHARSET_NUMERIC_HEX = "0123456789ABCDEF";

    /** Character set consisting of numeric and upper- and lower-case alphabetical ASCII characters */
    public static final String CHARSET_ALPHANUMERIC = CHARSET_ALPHABETICAL + CHARSET_NUMERIC;

    /** Default length of randomly generated strings */
    public static final int DEFAULT_RANDOM_STRING_LENGTH = 8;

    /** Default to the alphanumeric character set */
    public static final String DEFAULT_RANDOM_STRING_CHARSET = CHARSET_ALPHANUMERIC;

    private TestUtil() {
        throw new UnsupportedOperationException();
    }

    public static int randomInt() {
        return Math.abs(RANDOM.nextInt());
    }

    public static int randomInt(int max) {
        return Math.abs(RANDOM.nextInt(max));
    }

    /**
     * Generates a random string of characters from the specified charset string.
     *
     * @param length
     *     the length of the string to generate; must be a positive integer
     *
     * @param charset
     *     a string representing the character set to use for generating the string
     *
     * @throws IllegalArgumentException
     *     if length is a non-positive integer, or charset is null or empty
     *
     * @return a randomly generated string using the characters from the specified charset string
     */
    public static String randomString(int length, String charset) {
        if (length < 1) {
            throw new IllegalArgumentException("length is a non-positive integer");
        }

        if (charset == null || charset.isEmpty()) {
            throw new IllegalArgumentException("charset is null or empty");
        }

        return RANDOM.ints(length, 0, charset.length())
            .map(charset::charAt)
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
            .toString();
    }

    /**
     * Generates a string of characters from the specified charset string, appended to the given prefix
     * string. If the prefix is null or empty, the resultant string will only contain the generated
     * portion.
     *
     * @param prefix
     *     the prefix to prepend to the generated string
     *
     * @param length
     *     the length of the string to generate, not counting the prefix; must be a positive integer
     *
     * @param charset
     *     a string representing the character set to use for generating the string
     *
     * @throws IllegalArgumentException
     *     if length is a non-positive integer, or charset is null or empty
     *
     * @return a randomly generated string using the characters from the specified charset string
     * appended to the provided prefix
     */
    public static String randomString(String prefix, int length, String charset) {
        String suffix = randomString(length, charset);

        return (prefix != null && !prefix.isEmpty()) ?
            prefix + suffix :
            suffix;
    }

    /**
     * Generates a random alphanumeric string of the specified length.
     *
     * @param length
     *     the length of the string to generate; must be a positive integer
     *
     * @throws IllegalArgumentException
     *     if length is a non-positive integer
     *
     * @return a randomly generated, alphanumeric string of the given length
     */
    public static String randomString(int length) {
        return randomString(length, DEFAULT_RANDOM_STRING_CHARSET);
    }

    /**
     * Generates a random alphanumeric string, eight characters in length, appended to the given prefix
     * string. If the prefix is null or empty, the resultant string will only contain the generated
     * suffix.
     *
     * @param prefix
     *     the prefix to prepend to the generated string
     *
     * @return an eight-character, randomly generated, alphanumeric string appended to the provided
     * prefix
     */
    public static String randomString(String prefix) {
        return randomString(prefix, DEFAULT_RANDOM_STRING_LENGTH, DEFAULT_RANDOM_STRING_CHARSET);
    }

    /**
     * Generates a random alphanumeric string, eight characters in length.
     *
     * @return an eight-character, randomly generated, alphanumeric string
     */
    public static String randomString() {
        return randomString(null);
    }

    public static Owner createOwner(String key, String displayName) {
        return new Owner()
            .setId(key + "-id")
            .setKey(key)
            .setDisplayName(displayName);
    }

    public static Owner createOwner(String key) {
        return createOwner(key, key);
    }

    public static Owner createOwner() {
        return createOwner("Test Owner " + randomInt());
    }

    public static Consumer createConsumer(ConsumerType type, Owner owner, String username) {
        return new Consumer()
            .setUuid(Util.generateUUID())
            .setName("TestConsumer" + randomInt())
            .setUsername(username)
            .setOwner(owner)
            .setType(type);
    }

    public static Consumer createConsumer(ConsumerType type, Owner owner) {
        return createConsumer(type, owner, "User");
    }

    /**
     * Create a consumer with a new owner
     *
     * @return Consumer
     */
    public static Consumer createConsumer(Owner owner) {
        Consumer consumer = createConsumer(createConsumerType(), owner);

        // TODO: FIXME: These *should* live on the main createConsumer for consistency and sanity,
        // but for whatever reason we decided rewriting the same thing 9001 times with slight
        // variations would be more spicy. Move these if they don't break too many tests.
        consumer.setCreated(new Date());
        consumer.setFact("foo", "bar");
        consumer.setFact("foo1", "bar1");

        return consumer;
    }

    /**
     * Create a consumer with a new owner
     *
     * @return Consumer
     */
    public static Consumer createConsumer() {
        return createConsumer(createConsumerType(), createOwner("Test Owner " + randomInt()));
    }

    public static ConsumerDTO createConsumerDTO(ConsumerTypeDTO type, OwnerDTO owner) {
        return createConsumerDTO("TestConsumer" + randomInt(), "User", owner, type);
    }

    public static ConsumerDTO createConsumerDTO(String name, String userName, OwnerDTO owner,
        ConsumerTypeDTO type) {

        ConsumerDTO consumer = new ConsumerDTO()
            .name(name)
            .username(userName)
            .type(type)
            .autoheal(true)
            .serviceLevel("")
            .entitlementCount(0L)
            .facts(new HashMap<>())
            .installedProducts(new HashSet<>())
            .guestIds(new ArrayList<>())
            .environments(new ArrayList<>());

        if (owner != null) {
            consumer.setOwner(new NestedOwnerDTO()
                .key(owner.getKey())
                .id(owner.getId())
                .displayName(owner.getDisplayName()));
        }
        else {
            consumer.setOwner(null);
        }

        return consumer;
    }

    public static Consumer createDistributor() {
        return createDistributor(createOwner());
    }

    public static Consumer createDistributor(Owner owner) {
        ConsumerType ctype = new ConsumerType(ConsumerType.ConsumerTypeEnum.CANDLEPIN);
        ctype.setId("test-ctype-" + randomInt());

        return createConsumer(ctype, owner);
    }

    public static ConsumerType createConsumerType() {
        ConsumerType ctype = new ConsumerType("test-consumer-type-" + randomInt());
        ctype.setId("test-ctype-" + randomInt());

        return ctype;
    }

    public static Content createContent(String id, String name) {
        return new Content(id)
            .setName(name)
            .setType("test-type")
            .setLabel("test-label")
            .setVendor("test-vendor")
            .setContentUrl("https://test.url.com")
            .setGpgUrl("https://gpg.test.url.com")
            .setArches("x86");
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
            content.setReleaseVersion(dto.getReleaseVer());
            content.setGpgUrl(dto.getGpgUrl());
            content.setMetadataExpiration(dto.getMetadataExpire());
            content.setModifiedProductIds(dto.getModifiedProductIds());
            content.setArches(dto.getArches());
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

            product.setAttributes(Util.toMap(dto.getAttributes()));

            if (dto.getProductContent() != null) {
                for (ProductContentDTO pcd : dto.getProductContent()) {
                    if (pcd != null) {
                        Content content = createContent(pcd.getContent());

                        if (content != null) {
                            product.addContent(content, pcd.getEnabled() != null ? pcd.getEnabled() : true);
                        }
                    }
                }
            }

            product.setDependentProductIds(dto.getDependentProductIds());
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
                        Content content = createContent(pcd.getContent());

                        if (content != null) {
                            product.addContent(content, pcd.isEnabled() != null ? pcd.isEnabled() : true);
                        }
                    }
                }
            }

            product.setDependentProductIds(pdata.getDependentProductIds());
        }

        return product;
    }

    public static ProductDTO createProductDTO(String id, String name) {
        ProductDTO dto = new ProductDTO();

        dto.setId(id);
        dto.setName(name);
        dto.setAttributes(new ArrayList<>());
        dto.setBranding(new HashSet<>());

        return dto;
    }

    public static ProductDTO createProductDTO(String id) {
        return createProductDTO(id, id);
    }

    public static ProductDTO createProductDTO() {
        return createProductDTO("test-product-" + randomInt());
    }

    public static ProductInfo createProductInfo(String id, String name) {
        return InfoAdapter.productInfoAdapter(createProductDTO(id, name));
    }

    public static ProductInfo createProductInfo(String id) {
        return InfoAdapter.productInfoAdapter(createProductDTO(id));
    }

    public static ProductInfo createProductInfo() {
        return InfoAdapter.productInfoAdapter(createProductDTO());
    }

    public static Branding createBranding(String productId, String brandName) {
        return new Branding(productId, brandName, "OS");
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
            createDate(2000, 1, 1));

        sub.setId("test-sub-" + randomInt());

        return sub;
    }

    public static Pool createPool(Owner owner) {
        return createPool(owner, createProduct());
    }

    public static Pool createPool(Product product) {
        return createPool(createOwner("Test Owner " + randomInt()), product);
    }

    public static Pool createPool(Owner owner, Product product) {
        return createPool(owner, product, 5);
    }

    public static Pool createPool(Owner owner, Product product, int quantity) {
        String random = String.valueOf(randomInt());

        Pool pool = new Pool()
            .setOwner(owner)
            .setProduct(product)
            .setQuantity((long) quantity)
            .setStartDate(TestUtil.createDateOffset(-1, 0, 0))
            .setEndDate(TestUtil.createDateOffset(1, 0, 0))
            .setContractNumber("SUB234598S" + random)
            .setAccountNumber("ACC123" + random)
            .setOrderNumber("ORD222" + random)
            .setSourceSubscription(
                new SourceSubscription("SUB234598S" + random, PRIMARY_POOL_SUB_KEY + random));

        return pool;
    }

    public static Date createDate(int year, int month, int day) {
        Calendar cal = Calendar.getInstance();

        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, month - 1); // Java months are zero-indexed
        cal.set(Calendar.DATE, day);

        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        return cal.getTime();
    }

    /**
     * Creates a new Date offset from the current date by the specified years, months and days. If the
     * provided offsets are negative, the date created may be in the past.
     *
     * @param years
     *     the number of years to offset the created date
     *
     * @param months
     *     the number of months to offset the created date
     *
     * @param days
     *     the number of days to offset the created date
     *
     * @return a new date offset from the current date by the specified date units
     */
    public static Date createDateOffset(int years, int months, int days) {
        LocalDate now = LocalDate.now();
        return createDate(now.getYear() + years, now.getMonthValue() + months, now.getDayOfMonth() + days);
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
            Arrays.asList(new Permission[] { new OwnerPermission(owner, role) }),
            false);
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
        int rand = randomInt();

        Owner owner = new Owner()
            .setId(String.valueOf(rand))
            .setKey("Test Owner |" + rand)
            .setDisplayName("Test Owner |" + rand);

        return createEntitlement(
            owner,
            createConsumer(owner),
            createPool(owner, createProduct()),
            null);
    }

    public static GuestIdDTO createGuestIdDTO(String guestId) {
        GuestIdDTO dto = new GuestIdDTO()
            .guestId(guestId)
            .attributes(Collections.<String, String>emptyMap());

        return dto;
    }

    public void addPermissionToUser(User u, Access role, Owner o) {
        // Check if a permission already exists for this verb and owner:
    }

    /**
     * Returns a pool which will look like it was created from the given subscription during refresh
     * pools.
     *
     * @param sub
     *     source subscription
     * @return pool for subscription
     */
    public static Pool copyFromSub(Subscription sub) {
        Product product = createProduct(sub.getProduct());
        Product derivedProduct = createProduct(sub.getDerivedProduct());

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

        if (product != null && derivedProduct != null) {
            derivedProduct.setProvidedProducts(derivedProvidedProducts);
            product.setDerivedProduct(derivedProduct);
        }

        if (sub.getId() != null) {
            pool.setSourceSubscription(new SourceSubscription(sub.getId(), PRIMARY_POOL_SUB_KEY));
        }

        pool.setUpstreamPoolId(sub.getUpstreamPoolId());
        pool.setUpstreamConsumerId(sub.getUpstreamConsumerId());
        pool.setUpstreamEntitlementId(sub.getUpstreamEntitlementId());

        return pool;
    }

    public static ActivationKey createActivationKey(Owner owner, List<Pool> pools) {
        ActivationKey key = new ActivationKey()
            .setOwner(owner)
            .setName("A Test Key")
            .setServiceLevel("TestLevel")
            .setDescription("A test description for the test key.");

        if (pools != null) {
            pools.forEach(pool -> key.addPool(pool, 1L));
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

    public static boolean isJsonEqual(String one, String two) throws IOException {
        ObjectMapper mapper = ObjectMapperFactory.getObjectMapper();
        JsonNode tree1 = mapper.readTree(one);
        JsonNode tree2 = mapper.readTree(two);
        return tree1.equals(tree2);
    }

    public static String getStringOfSize(int size) {
        char[] charArray = new char[size];
        Arrays.fill(charArray, 'x');
        return new String(charArray);
    }

    public static Map<String, Product> stubChangedProducts(Product... products) {
        Map<String, Product> result = new HashMap<>();
        for (Product p : products) {
            result.put(p.getId(), p);
        }
        return result;
    }

    // TODO: FIXME: This mock should not exist, nor should mocks of the entire database layer in this fashion.
    // This exists as a kludge and should be avoided. If you find yourself reaching for this function in your
    // tests, you are probably doing something wrong. Seriously, this should not be used and is only being
    // maintained for compatibility with the existing poorly written test suites. Do not add to that pile.

    public static void mockTransactionalFunctionality(EntityManager mockEntityManager,
        AbstractHibernateCurator... mockCurators) {

        Objects.requireNonNull(mockEntityManager);

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
                    throw new IllegalStateException("transaction is not active");
                }

                if (this.rollbackOnly) {
                    throw new IllegalStateException("transaction is flagged rollback only");
                }

                this.active = false;
                this.rollbackOnly = false;
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
                    throw new IllegalStateException("transaction is not active");
                }

                this.active = false;
                this.rollbackOnly = false;
            }

            @Override
            public void setRollbackOnly() {
                this.rollbackOnly = true;
            }
        };

        doReturn(transaction).when(mockEntityManager).getTransaction();

        if (mockCurators != null) {
            for (AbstractHibernateCurator mockCurator : mockCurators) {
                Objects.requireNonNull(mockCurator);

                doReturn(mockEntityManager).when(mockCurator).getEntityManager();
                doReturn(transaction).when(mockCurator).getTransaction();

                doAnswer(iom -> new Transactional(mockEntityManager)).when(mockCurator).transactional();

                // Runnables
                doAnswer(iom -> {
                    new Transactional(mockEntityManager)
                        .execute((Runnable) iom.getArgument(0));
                    return null;
                }).when(mockCurator).transactional(any(Runnable.class));

                try {
                    doAnswer(iom -> {
                        new Transactional(mockEntityManager)
                            .checkedExecute((CheckedRunnable) iom.getArgument(0));
                        return null;
                    }).when(mockCurator).checkedTransactional(any(CheckedRunnable.class));
                }
                catch (Exception e) {
                    // Mock shenanigans; shouldn't ever happen
                    throw new RuntimeException(e);
                }

                // Suppliers
                doAnswer(iom -> {
                    return new Transactional(mockEntityManager)
                        .execute((Supplier) iom.getArgument(0));
                }).when(mockCurator).transactional(any(Supplier.class));

                try {
                    doAnswer(iom -> {
                        return new Transactional(mockEntityManager)
                            .checkedExecute((CheckedSupplier) iom.getArgument(0));
                    }).when(mockCurator).checkedTransactional(any(CheckedSupplier.class));
                }
                catch (Exception e) {
                    // Mock shenanigans; shouldn't ever happen
                    throw new RuntimeException(e);
                }
            }
        }
    }

}
