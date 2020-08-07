/**
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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
package org.candlepin.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.Mockito.*;

import org.candlepin.audit.EventSink;
import org.candlepin.common.config.Configuration;
import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.ContentAccessManager.ContentAccessMode;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCapability;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Content;
import org.candlepin.model.ContentAccessCertificateCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentContent;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.KeyPairCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerContentCurator;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.OwnerEnvContentAccess;
import org.candlepin.model.OwnerEnvContentAccessCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.pki.CertificateReader;
import org.candlepin.pki.PKIUtility;
import org.candlepin.pki.PrivateKeyReader;
import org.candlepin.pki.SubjectKeyIdentifierWriter;
import org.candlepin.pki.impl.DefaultSubjectKeyIdentifierWriter;
import org.candlepin.pki.impl.JSSPKIUtility;
import org.candlepin.pki.impl.JSSPrivateKeyReader;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;
import org.candlepin.util.X509V3ExtensionUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;



/**
 * Test suite for the ContentAccessManager class
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ContentAccessManagerTest {

    private static KeyPair testingKeyPair;

    private Configuration config;

    @Mock private EventSink mockEventSink;
    @Mock private KeyPairCurator mockKeyPairCurator;
    @Mock private CertificateSerialCurator mockCertSerialCurator;
    @Mock private ConsumerCurator mockConsumerCurator;
    @Mock private ConsumerTypeCurator mockConsumerTypeCurator;
    @Mock private ContentAccessCertificateCurator mockContentAccessCertCurator;
    @Mock private OwnerContentCurator mockOwnerContentCurator;
    @Mock private OwnerCurator mockOwnerCurator;
    @Mock private OwnerEnvContentAccessCurator mockOwnerEnvContentAccessCurator;
    @Mock private EnvironmentCurator mockEnvironmentCurator;
    @Mock private EntitlementCurator mockEntitlementCurator;
    private PKIUtility pkiUtility;
    private ObjectMapper objMapper;
    private X509V3ExtensionUtil x509V3ExtensionUtil;

    private final String entitlementMode = ContentAccessMode.ENTITLEMENT.toDatabaseValue();
    private final String orgEnvironmentMode = ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue();


    @BeforeAll
    public static void loadTestKeyPair() throws Exception {
        ClassLoader classloader = ContentAccessManagerTest.class.getClassLoader();
        InputStream keyStream = classloader.getResourceAsStream("test.key");
        assertNotNull(keyStream);

        testingKeyPair = null;
        try (PEMParser reader = new PEMParser(new InputStreamReader(keyStream))) {
            testingKeyPair = new JcaPEMKeyConverter().getKeyPair((PEMKeyPair) reader.readObject());
        }
        assertNotNull(testingKeyPair);
    }

    @BeforeEach
    public void setup() throws Exception {
        this.config = new CandlepinCommonTestConfig();

        PrivateKeyReader keyReader = new JSSPrivateKeyReader();
        CertificateReader certReader = new CertificateReader(this.config, keyReader);
        SubjectKeyIdentifierWriter keyIdWriter = new DefaultSubjectKeyIdentifierWriter();
        this.pkiUtility = spy(new JSSPKIUtility(certReader, keyIdWriter, this.config));

        this.objMapper = new ObjectMapper();
        this.x509V3ExtensionUtil = spy(new X509V3ExtensionUtil(this.config, this.mockEntitlementCurator,
            this.objMapper));

        // FIXME: This mess of mocks is why we should not be using mocks in this way. We should be
        // using a test database framework and our actual curators and objects.

        doAnswer(returnsFirstArg()).when(this.mockOwnerCurator).merge(any(Owner.class));
        doAnswer(returnsFirstArg()).when(this.mockConsumerCurator).merge(any(Consumer.class));
        doAnswer(returnsFirstArg()).when(this.mockOwnerEnvContentAccessCurator)
            .saveOrUpdate(any(OwnerEnvContentAccess.class));
        doReturn(this.testingKeyPair).when(this.mockKeyPairCurator).getConsumerKeyPair(any(Consumer.class));

        doAnswer(iom -> {
            CertificateSerial serial = (CertificateSerial) iom.getArgument(0);

            if (serial != null) {
                serial.setId(Util.generateUniqueLong());
            }

            return serial;
        }).when(this.mockCertSerialCurator).create(any(CertificateSerial.class));
    }


    private ContentAccessManager createManager() {
        return new ContentAccessManager(
            this.config, this.pkiUtility, this.x509V3ExtensionUtil, this.mockContentAccessCertCurator,
            this.mockKeyPairCurator, this.mockCertSerialCurator, this.mockOwnerContentCurator,
            this.mockOwnerCurator, this.mockOwnerEnvContentAccessCurator, this.mockConsumerCurator,
            this.mockConsumerTypeCurator, this.mockEnvironmentCurator, this.mockContentAccessCertCurator,
            this.mockEventSink);
    }

    private Owner mockOwner() {
        Owner owner = new Owner();
        owner.setId("test_owner");
        owner.setContentAccessModeList(entitlementMode + "," + orgEnvironmentMode);
        owner.setContentAccessMode(orgEnvironmentMode);

        doReturn(owner).when(this.mockOwnerCurator).findOwnerById(eq(owner.getId()));

        return owner;
    }

    private Consumer mockConsumer(Owner owner) {
        ConsumerType type = new ConsumerType(ConsumerType.ConsumerTypeEnum.SYSTEM);
        type.setId("test-id");

        Consumer consumer = new Consumer("Test Consumer", "bob", owner, type);
        consumer.setUuid("test-consumer-uuid");
        consumer.setId("test-consumer-id");

        consumer.setFact("system.certificate_version", X509V3ExtensionUtil.CERT_VERSION);
        consumer.setCapabilities(Util.asSet(new ConsumerCapability(consumer, "cert_v3")));

        doReturn(type).when(this.mockConsumerTypeCurator).getConsumerType(eq(consumer));

        return consumer;
    }

    private Content mockContent(Owner owner) {
        Content content = new Content();
        content.setUuid("test_content-uuid");
        content.setId("1234");
        content.setName("test_content");
        content.setLabel("test_content-label");
        content.setType("yum");
        content.setVendor("vendor");
        content.setContentUrl("/content/dist/rhel/$releasever/$basearch/os");
        content.setGpgUrl("gpgUrl");
        content.setArches("x86_64");
        content.setMetadataExpiration(3200L);
        content.setRequiredTags("TAG1,TAG2");

        List<Content> contentList = Arrays.asList(content);

        CandlepinQuery cqmock = mock(CandlepinQuery.class);
        doReturn(contentList).when(cqmock).list();
        doAnswer(iom -> contentList.iterator()).when(cqmock).iterator();

        doReturn(cqmock).when(this.mockOwnerContentCurator).getContentByOwner(eq(owner));

        return content;
    }

    private Product mockProduct(Content content) {
        Product product = new Product("test_product_id", "test_product", null);
        product.setAttribute(Product.Attributes.VERSION, "version");
        product.setAttribute(Product.Attributes.VARIANT, "variant");
        product.setAttribute(Product.Attributes.TYPE, "SVC");
        product.setAttribute(Product.Attributes.ARCHITECTURE, "x86_64");
        product.addContent(content, false);

        return product;
    }

    private Pool mockPool(Product product) {
        Pool pool = new Pool();
        pool.setQuantity(1L);
        pool.setProduct(product);
        pool.setStartDate(TestUtil.createDate(2000, 1, 1));
        pool.setEndDate(TestUtil.createDate(2050, 1, 1));

        return pool;
    }

    private Entitlement mockEntitlement(Owner owner, Consumer consumer, Pool pool) {
        Entitlement entitlement = new Entitlement();
        entitlement.setQuantity(10);
        entitlement.setOwner(owner);
        entitlement.setConsumer(consumer);
        entitlement.setPool(pool);

        return entitlement;
    }

    private Environment mockEnvironment(Owner owner, Consumer consumer, Content content) {
        Environment environment = new Environment("test_environment", "test_environment", owner);
        environment.setEnvironmentContent(Util.asSet(new EnvironmentContent(environment, content, true)));

        consumer.setEnvironment(environment);

        doReturn(environment).when(this.mockEnvironmentCurator).get(eq(environment.getId()));
        doReturn(environment).when(this.mockEnvironmentCurator).getConsumerEnvironment(eq(consumer));

        return environment;
    }

    @Test
    public void testContentAccessModeResolveNameWithValidNames() {
        ContentAccessMode output = ContentAccessMode.resolveModeName(entitlementMode);
        assertSame(ContentAccessMode.ENTITLEMENT, output);

        output = ContentAccessMode.resolveModeName(orgEnvironmentMode);
        assertSame(ContentAccessMode.ORG_ENVIRONMENT, output);
    }

    @Test
    public void testContentAccessModeResolveNameWithValidNamesAndNullResolution() {
        ContentAccessMode output = ContentAccessMode.resolveModeName(entitlementMode, true);
        assertSame(ContentAccessMode.ENTITLEMENT, output);

        output = ContentAccessMode.resolveModeName(orgEnvironmentMode, true);
        assertSame(ContentAccessMode.ORG_ENVIRONMENT, output);
    }

    @Test
    public void testContentAccessModeDefaultIsEntitlement() {
        ContentAccessMode output = ContentAccessMode.getDefault();
        assertSame(ContentAccessMode.ENTITLEMENT, output);
    }

    @Test
    public void testContentAccessModeResolveNameWithEmptyName() {
        ContentAccessMode output = ContentAccessMode.resolveModeName("");
        assertSame(ContentAccessMode.getDefault(), output);
    }

    @Test
    public void testContentAccessModeResolveNameWithNullName() {
        ContentAccessMode output = ContentAccessMode.resolveModeName(null);
        assertNull(output);
    }

    @Test
    public void testContentAccessModeResolveNameWithEmptyNameAndNullResolution() {
        ContentAccessMode output = ContentAccessMode.resolveModeName("", true);
        assertSame(ContentAccessMode.getDefault(), output);
    }

    @Test
    public void testContentAccessModeResolveNameWithNullNameAndNullResolution() {
        ContentAccessMode output = ContentAccessMode.resolveModeName(null, true);
        assertSame(ContentAccessMode.getDefault(), output);
    }

    @Test
    public void testUpdateOwnerContentAccessSetEmpty() {
        Owner owner = new Owner();

        String contentAccessModeList = "";
        String contentAccessMode = "";

        ContentAccessManager manager = this.createManager();
        manager.updateOwnerContentAccess(owner, contentAccessModeList, contentAccessMode);

        assertEquals(owner.getContentAccessModeList(), ContentAccessMode.getDefault().toDatabaseValue());
        assertEquals(owner.getContentAccessMode(), ContentAccessMode.getDefault().toDatabaseValue());
    }

    @Test
    public void testUpdateOwnerContentAccessSetNull() {
        Owner owner = new Owner();

        String initialAccessModeList = owner.getContentAccessModeList();
        String initialAccessMode = owner.getContentAccessMode();

        String contentAccessModeList = null;
        String contentAccessMode = null;

        ContentAccessManager manager = this.createManager();
        manager.updateOwnerContentAccess(owner, contentAccessModeList, contentAccessMode);

        assertEquals(initialAccessModeList, owner.getContentAccessModeList());
        assertEquals(initialAccessMode, owner.getContentAccessMode());
    }

    @Test
    public void testUpdateOwnerContentAccessSetNullRemainsUnchanged() {
        String initialAccessModeList = entitlementMode + "," + orgEnvironmentMode;
        String initialAccessMode = entitlementMode;

        Owner owner = new Owner()
            .setContentAccessModeList(initialAccessModeList)
            .setContentAccessMode(initialAccessMode);

        String updatedAccessModeList = null;
        String updatedAccessMode = null;

        ContentAccessManager manager = this.createManager();
        manager.updateOwnerContentAccess(owner, updatedAccessModeList, updatedAccessMode);

        assertEquals(initialAccessModeList, owner.getContentAccessModeList());
        assertEquals(initialAccessMode, owner.getContentAccessMode());
    }

    @Test
    public void testUpdateOwnerContentAccessUpdateListValidExistingMode() {
        String initialAccessModeList = entitlementMode + "," + orgEnvironmentMode;
        String initialAccessMode = orgEnvironmentMode;

        Owner owner = new Owner("test_owner", "test_owner")
            .setContentAccessModeList(initialAccessModeList)
            .setContentAccessMode(initialAccessMode);

        String updatedAccessModeList = orgEnvironmentMode;
        String updatedAccessMode = null;

        ContentAccessManager manager = this.createManager();
        manager.updateOwnerContentAccess(owner, updatedAccessModeList, updatedAccessMode);

        assertEquals(updatedAccessModeList, owner.getContentAccessModeList());
        assertEquals(orgEnvironmentMode, owner.getContentAccessMode());
    }

    @Test
    public void testUpdateOwnerContentAccessModeNotOnList() {
        Owner owner = new Owner("test_owner", "test_owner");

        String updatedAccessModeList = entitlementMode;
        String updatedAccessMode = orgEnvironmentMode;

        ContentAccessManager manager = this.createManager();

        assertThrows(IllegalArgumentException.class, () ->
            manager.updateOwnerContentAccess(owner, updatedAccessModeList, updatedAccessMode));
    }

    @Test
    public void testUpdateOwnerContentAccessExistingModeNotInUpdatedListDefaultPresent() {
        String initialAccessModeList = entitlementMode + "," + orgEnvironmentMode;
        String initialAccessMode = orgEnvironmentMode;

        Owner owner = new Owner("test_owner", "test_owner")
            .setContentAccessModeList(initialAccessModeList)
            .setContentAccessMode(initialAccessMode);

        String updatedAccessModeList = entitlementMode;
        String updatedAccessMode = null;

        ContentAccessManager manager = this.createManager();
        manager.updateOwnerContentAccess(owner, updatedAccessModeList, updatedAccessMode);

        // If the default mode (entitlement) is present in the new list, and we're not explicitly
        // updating the mode, *and* the current mode is no longer valid, it should be set to
        // "entitlement"

        assertEquals(updatedAccessModeList, owner.getContentAccessModeList());
        assertEquals(entitlementMode, owner.getContentAccessMode());
    }

    @Test
    public void testUpdateOwnerContentAccessExistingModeNotInUpdatedList() {
        String initialAccessModeList = entitlementMode + "," + orgEnvironmentMode;
        String initialAccessMode = entitlementMode;

        Owner owner = new Owner("test_owner", "test_owner")
            .setContentAccessModeList(initialAccessModeList)
            .setContentAccessMode(initialAccessMode);

        String updatedAccessModeList = orgEnvironmentMode;
        String updatedAccessMode = null;

        ContentAccessManager manager = this.createManager();
        manager.updateOwnerContentAccess(owner, updatedAccessModeList, updatedAccessMode);

        // If the default mode (entitlement) is not present in the new list, and we're not
        // explicitly updating the mode, *and* the current mode is no longer valid, it should
        // be set to the first value in the list.

        assertEquals(updatedAccessModeList, owner.getContentAccessModeList());
        assertEquals(orgEnvironmentMode, owner.getContentAccessMode());
    }

    @Test
    public void testUpdateOwnerContentAccessUpdatedModeNotInUpdatedList() {
        String initialAccessModeList = entitlementMode + "," + orgEnvironmentMode;
        String initialAccessMode = orgEnvironmentMode;

        Owner owner = new Owner("test_owner", "test_owner")
            .setContentAccessModeList(initialAccessModeList)
            .setContentAccessMode(initialAccessMode);

        String updatedAccessModeList = orgEnvironmentMode;
        String updatedAccessMode = entitlementMode;

        ContentAccessManager manager = this.createManager();

        assertThrows(IllegalArgumentException.class, () ->
            manager.updateOwnerContentAccess(owner, updatedAccessModeList, updatedAccessMode));
    }

    @Test
    public void testUpdateOwnerContentAccessDefaultModeNotInUpdatedList() {
        String initialAccessModeList = entitlementMode + "," + orgEnvironmentMode;
        String initialAccessMode = orgEnvironmentMode;

        Owner owner = new Owner("test_owner", "test_owner")
            .setContentAccessModeList(initialAccessModeList)
            .setContentAccessMode(initialAccessMode);

        String updatedAccessModeList = orgEnvironmentMode;
        String updatedAccessMode = "";

        // We should convert the requested access mode to the default value, which is still invalid
        // as the default is not in the list.

        ContentAccessManager manager = this.createManager();

        assertThrows(IllegalArgumentException.class, () ->
            manager.updateOwnerContentAccess(owner, updatedAccessModeList, updatedAccessMode));
    }

    @Test
    public void testUpdateOwnerContentAccessModeNoList() {
        Owner owner = new Owner("test_owner", "test_owner");

        String contentAccessModeList = "";
        String contentAccessMode = orgEnvironmentMode;

        ContentAccessManager manager = this.createManager();
        assertThrows(IllegalArgumentException.class, () ->
            manager.updateOwnerContentAccess(owner, contentAccessModeList, contentAccessMode));
    }

    @Test
    public void testUpdateOwnerContentAccessModeChanged() {
        Owner owner = new Owner();

        String contentAccessModeList = entitlementMode + "," + orgEnvironmentMode;
        String contentAccessMode = orgEnvironmentMode;

        ContentAccessManager manager = this.createManager();
        manager.updateOwnerContentAccess(owner, contentAccessModeList, contentAccessMode);

        assertEquals(owner.getContentAccessModeList(), contentAccessModeList);
        assertEquals(owner.getContentAccessMode(), contentAccessMode);
    }

    @Test
    public void testContentPrefixShouldBeOmittedInHosted() throws Exception {
        this.config.setProperty(ConfigProperties.STANDALONE, "true");

        Owner owner = this.mockOwner();
        Consumer consumer = this.mockConsumer(owner);
        Content content = this.mockContent(owner);
        Product product = this.mockProduct(content);
        Pool pool = this.mockPool(product);
        Entitlement entitlement = this.mockEntitlement(owner, consumer, pool);

        String contentPrefix = "/testPrefix/";
        String expectedPrefix = "";

        owner.setContentPrefix(contentPrefix);

        ContentAccessManager manager = this.createManager();
        manager.getCertificate(consumer);

        verify(this.x509V3ExtensionUtil, times(1)).mapProduct(any(Product.class), any(Product.class),
            eq(expectedPrefix), any(Map.class), any(Consumer.class), any(Pool.class), any(Set.class));
    }

    @Test
    public void testPrefixesShouldBeUsedInStandalone() throws Exception {
        this.config.setProperty(ConfigProperties.STANDALONE, "false");

        Owner owner = this.mockOwner();
        Consumer consumer = this.mockConsumer(owner);
        Content content = this.mockContent(owner);
        Product product = this.mockProduct(content);
        Pool pool = this.mockPool(product);
        Entitlement entitlement = this.mockEntitlement(owner, consumer, pool);

        String contentPrefix = "/testPrefix/";
        String expectedPrefix = "testPrefix/";

        owner.setContentPrefix(contentPrefix);

        ContentAccessManager manager = this.createManager();
        manager.getCertificate(consumer);

        verify(this.x509V3ExtensionUtil, times(1)).mapProduct(any(Product.class), any(Product.class),
            eq(expectedPrefix), any(Map.class), any(Consumer.class), any(Pool.class), any(Set.class));
    }

    @Test
    public void testContentPrefixEnvironmentVariableExpansion() throws Exception {
        this.config.setProperty(ConfigProperties.STANDALONE, "false");

        Owner owner = this.mockOwner();
        Consumer consumer = this.mockConsumer(owner);
        Content content = this.mockContent(owner);
        Product product = this.mockProduct(content);
        Pool pool = this.mockPool(product);
        Entitlement entitlement = this.mockEntitlement(owner, consumer, pool);
        Environment environment = this.mockEnvironment(owner, consumer, content);

        String contentPrefix = "/someorg/$env/";
        String expectedPrefix = "someorg/" + environment.getName() + "/";

        owner.setContentPrefix(contentPrefix);

        ContentAccessManager manager = this.createManager();
        manager.getCertificate(consumer);

        verify(this.x509V3ExtensionUtil, times(1)).mapProduct(any(Product.class), any(Product.class),
            eq(expectedPrefix), any(Map.class), any(Consumer.class), any(Pool.class), any(Set.class));
    }

    @Test
    public void testContentPrefixEncoding() throws Exception {
        this.config.setProperty(ConfigProperties.STANDALONE, "false");

        Owner owner = this.mockOwner();
        Consumer consumer = this.mockConsumer(owner);
        Content content = this.mockContent(owner);
        Product product = this.mockProduct(content);
        Pool pool = this.mockPool(product);
        Entitlement entitlement = this.mockEntitlement(owner, consumer, pool);

        String contentPrefix = "/abc! #$%&'()*+,/untouched/123:;=?@[]\"-.<>\\^_`{|}~£円/";
        String expectedPrefix = "abc%21+%23$%25%26%27%28%29*%2B%2C/untouched/" +
            "123%3A%3B%3D%3F%40%5B%5D%22-.%3C%3E%5C%5E_%60%7B%7C%7D%7E%C2%A3%E5%86%86/";

        owner.setContentPrefix(contentPrefix);

        ContentAccessManager manager = this.createManager();
        manager.getCertificate(consumer);

        verify(this.x509V3ExtensionUtil, times(1)).mapProduct(any(Product.class), any(Product.class),
            eq(expectedPrefix), any(Map.class), any(Consumer.class), any(Pool.class), any(Set.class));
    }

    @Test
    public void testContentPrefixEnvironmentVariableExpansionIsEncoded() throws Exception {
        this.config.setProperty(ConfigProperties.STANDALONE, "false");

        Owner owner = this.mockOwner();
        Consumer consumer = this.mockConsumer(owner);
        Content content = this.mockContent(owner);
        Product product = this.mockProduct(content);
        Pool pool = this.mockPool(product);
        Entitlement entitlement = this.mockEntitlement(owner, consumer, pool);
        Environment environment = this.mockEnvironment(owner, consumer, content);
        environment.setName("test environment #1");

        String contentPrefix = "/someorg/$env/";
        String expectedPrefix = "someorg/test+environment+%231/";

        owner.setContentPrefix(contentPrefix);

        ContentAccessManager manager = this.createManager();
        manager.getCertificate(consumer);

        verify(this.x509V3ExtensionUtil, times(1)).mapProduct(any(Product.class), any(Product.class),
            eq(expectedPrefix), any(Map.class), any(Consumer.class), any(Pool.class), any(Set.class));
    }
}
