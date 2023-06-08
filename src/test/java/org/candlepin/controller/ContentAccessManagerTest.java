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
package org.candlepin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.audit.EventSink;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.controller.ContentAccessManager.ContentAccessMode;
import org.candlepin.model.AbstractHibernateObject;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCapability;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Content;
import org.candlepin.model.ContentAccessCertificate;
import org.candlepin.model.ContentAccessCertificateCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentContent;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.KeyPairDataCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerContentCurator;
import org.candlepin.model.OwnerCurator;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;

import javax.persistence.EntityManager;


@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ContentAccessManagerTest {

    private static KeyPair testingKeyPair;

    private DevConfig config;

    @Mock private EventSink mockEventSink;
    @Mock private KeyPairDataCurator mockKeyPairDataCurator;
    @Mock private CertificateSerialCurator mockCertSerialCurator;
    @Mock private ConsumerCurator mockConsumerCurator;
    @Mock private ConsumerTypeCurator mockConsumerTypeCurator;
    @Mock private ContentAccessCertificateCurator mockContentAccessCertCurator;
    @Mock private OwnerCurator mockOwnerCurator;
    @Mock private OwnerContentCurator mockOwnerContentCurator;
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
        assertNotNull(testingKeyPair.getPrivate());
        assertNotNull(testingKeyPair.getPrivate().getEncoded());
    }

    @BeforeEach
    public void setup() throws Exception {
        this.config = TestConfig.defaults();

        PrivateKeyReader keyReader = new JSSPrivateKeyReader();
        CertificateReader certReader = new CertificateReader(this.config, keyReader);
        SubjectKeyIdentifierWriter keyIdWriter = new DefaultSubjectKeyIdentifierWriter();
        this.pkiUtility = spy(new JSSPKIUtility(certReader, keyIdWriter, this.config,
            this.mockKeyPairDataCurator));

        this.objMapper = new ObjectMapper();
        this.x509V3ExtensionUtil = spy(new X509V3ExtensionUtil(this.config, this.mockEntitlementCurator,
            this.objMapper));

        // FIXME: This mess of mocks is why we should not be using mocks in this way. We should be
        // using a test database framework and our actual curators and objects.

        doAnswer(new PersistSimulator<>()).when(this.mockOwnerCurator).merge(any(Owner.class));
        doAnswer(new PersistSimulator<>()).when(this.mockConsumerCurator).merge(any(Consumer.class));
        doAnswer(new PersistSimulator<>()).when(this.mockContentAccessCertCurator)
            .create(any(ContentAccessCertificate.class));
        doReturn(this.testingKeyPair).when(this.pkiUtility).getConsumerKeyPair(any(Consumer.class));

        doAnswer(iom -> {
            CertificateSerial serial = iom.getArgument(0);

            if (serial != null) {
                serial.setId(Util.generateUniqueLong());
            }

            return serial;
        }).when(this.mockCertSerialCurator).create(any(CertificateSerial.class));

        EntityManager entityManager = mock(EntityManager.class);
        TestUtil.mockTransactionalFunctionality(entityManager, this.mockConsumerCurator);
    }

    public static class PersistSimulator<T extends AbstractHibernateObject> implements Answer<T> {
        public T answer(InvocationOnMock iom) {
            T obj = iom.getArgument(0);

            if (obj != null) {
                Date now = new Date();

                if (obj.getCreated() == null) {
                    obj.setCreated(now);
                }

                obj.setUpdated(now);
            }

            return obj;
        }
    }

    private ContentAccessManager createManager(PKIUtility pkiUtil) {
        return new ContentAccessManager(
            this.config, pkiUtil, this.x509V3ExtensionUtil, this.mockContentAccessCertCurator,
            this.mockCertSerialCurator, this.mockOwnerCurator, this.mockOwnerContentCurator,
            this.mockConsumerCurator, this.mockConsumerTypeCurator, this.mockEnvironmentCurator,
            this.mockContentAccessCertCurator, this.mockEventSink);
    }

    private ContentAccessManager createManager() {
        return this.createManager(this.pkiUtility);
    }

    private Owner mockOwner() {
        Owner owner = new Owner();
        owner.setId("test_owner");
        owner.setKey("test_owner");
        owner.setContentAccessModeList(entitlementMode + "," + orgEnvironmentMode);
        owner.setContentAccessMode(orgEnvironmentMode);

        doReturn(owner).when(this.mockOwnerCurator).findOwnerById(eq(owner.getId()));

        return owner;
    }

    private Consumer mockConsumer(Owner owner) {
        ConsumerType type = new ConsumerType(ConsumerType.ConsumerTypeEnum.SYSTEM);
        type.setId("test-id");

        Consumer consumer = new Consumer()
            .setUuid("test-consumer-uuid")
            .setId("test-consumer-id")
            .setName("Test Consumer")
            .setUsername("bob")
            .setOwner(owner)
            .setType(type)
            .setFact(Consumer.Facts.SYSTEM_CERTIFICATE_VERSION, X509V3ExtensionUtil.CERT_VERSION)
            .setCapabilities(Set.of(new ConsumerCapability("cert_v3")));

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

        return content;
    }

    private Product mockProduct(Owner owner, Content content) {
        Product product = new Product()
            .setId(TestUtil.randomString("test_product-"))
            .setName("test_product")
            .setAttribute(Product.Attributes.VERSION, "version")
            .setAttribute(Product.Attributes.VARIANT, "variant")
            .setAttribute(Product.Attributes.TYPE, "SVC")
            .setAttribute(Product.Attributes.ARCHITECTURE, "x86_64");

        product.addContent(content, false);
        List<Product> productList = Arrays.asList(product);

        CandlepinQuery cqmock = mock(CandlepinQuery.class);
        doReturn(productList).when(cqmock).list();
        doAnswer(iom -> productList.iterator()).when(cqmock).iterator();
        return product;
    }

    private Pool mockPool(Product product) {
        Pool pool = new Pool();
        pool.setQuantity(1L);
        pool.setProduct(product);
        pool.setStartDate(Util.yesterday());
        pool.setEndDate(Util.tomorrow());

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
        int rnd = TestUtil.randomInt();

        Environment environment = new Environment()
            .setId("test_environment-" + rnd)
            .setName("test_environment-" + rnd)
            .setOwner(owner);

        EnvironmentContent ec = new EnvironmentContent()
            .setEnvironment(environment)
            .setContent(content)
            .setEnabled(true);

        environment.addEnvironmentContent(ec);

        consumer.addEnvironment(environment);

        doReturn(environment).when(this.mockEnvironmentCurator).get(eq(environment.getId()));
        doReturn(List.of(environment)).when(this.mockEnvironmentCurator)
            .getConsumerEnvironments(eq(consumer));

        return environment;
    }

    private ContentAccessCertificate mockContentAccessCertificate(Consumer consumer) {
        ContentAccessCertificate cert = new ContentAccessCertificate();
        cert.setId("123456789");
        cert.setKey("test-key");
        cert.setConsumer(consumer);
        return cert;
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
    public void testContentAccessModeDefaultIsOrgEntitlement() {
        ContentAccessMode output = ContentAccessMode.getDefault();
        assertSame(ContentAccessMode.ORG_ENVIRONMENT, output);
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

        assertEquals(owner.getContentAccessModeList(), ContentAccessManager.getListDefaultDatabaseValue());
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

        String updatedAccessModeList = entitlementMode;
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
        Owner updatedOwner = manager.updateOwnerContentAccess(owner,
            contentAccessModeList, contentAccessMode);
        assertEquals(ContentAccessManager.getListDefaultDatabaseValue(),
            updatedOwner.getContentAccessModeList());
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
    public void testUpdateOwnerContentAccessThrowsExceptionWhenOwnerIsNull() {
        String contentAccessModeList = entitlementMode + "," + orgEnvironmentMode;
        String contentAccessMode = orgEnvironmentMode;

        ContentAccessManager manager = this.createManager();
        assertThrows(IllegalArgumentException.class,
            () -> manager.updateOwnerContentAccess(null, contentAccessModeList, contentAccessMode));
    }

    private void verifyContainerContentPath(String expected) throws Exception {
        ArgumentCaptor<List<org.candlepin.model.dto.Product>> captor = ArgumentCaptor.forClass(List.class);

        verify(this.x509V3ExtensionUtil, times(1)).getByteExtensions(captor.capture());

        List<org.candlepin.model.dto.Product> products = captor.getValue();

        assertNotNull(products);
        assertEquals(1, products.size());

        org.candlepin.model.dto.Product product = products.get(0);
        assertNotNull(product);

        List<org.candlepin.model.dto.Content> contents = product.getContent();
        assertNotNull(contents);
        assertEquals(1, contents.size());

        org.candlepin.model.dto.Content content = contents.get(0);
        assertNotNull(content);

        assertEquals(expected, content.getPath());
    }

    @Test
    public void testContainerContentPathShouldUseOwnerKeyInHosted() throws Exception {
        this.config.setProperty(ConfigProperties.STANDALONE, "false");

        Owner owner = this.mockOwner();
        Consumer consumer = this.mockConsumer(owner);
        Content content = this.mockContent(owner);
        Environment environment = this.mockEnvironment(owner, consumer, content);
        when(mockEnvironmentCurator.getConsumerEnvironments(any(Consumer.class)))
            .thenReturn(List.of(environment));

        String expectedPath = "/sca/" + owner.getKey();

        ContentAccessManager manager = this.createManager();
        ContentAccessCertificate output = manager.getCertificate(consumer);
        assertNotNull(output);

        this.verifyContainerContentPath(expectedPath);
    }

    @Test
    public void testContainerContentPathShouldBeContentPrefixInStandalone() throws Exception {
        this.config.setProperty(ConfigProperties.STANDALONE, "true");

        Owner owner = this.mockOwner();
        Consumer consumer = this.mockConsumer(owner);
        Content content = this.mockContent(owner);
        Environment environment = this.mockEnvironment(owner, consumer, content);
        when(mockEnvironmentCurator.getConsumerEnvironments(any(Consumer.class)))
            .thenReturn(List.of(environment));

        String expectedPath = "/" + owner.getKey() + "/" + environment.getName();

        ContentAccessManager manager = this.createManager();
        ContentAccessCertificate output = manager.getCertificate(consumer);
        assertNotNull(output);

        this.verifyContainerContentPath(expectedPath);
    }

    @Test
    public void testContentPrefixIncludesEnvironmentWhenPresent() throws Exception {
        this.config.setProperty(ConfigProperties.STANDALONE, "true");

        Owner owner = this.mockOwner();
        Consumer consumer = this.mockConsumer(owner);
        Content content = this.mockContent(owner);
        Environment environment = this.mockEnvironment(owner, consumer, content);
        when(mockEnvironmentCurator.getConsumerEnvironments(any(Consumer.class)))
            .thenReturn(List.of(environment));

        String expectedPrefix = "/" + owner.getKey() + "/" + environment.getName();

        ContentAccessManager manager = this.createManager();
        ContentAccessCertificate output = manager.getCertificate(consumer);
        assertNotNull(output);

        this.verifyContainerContentPath(expectedPrefix);
    }

    @Test
    public void testGetCertificateReturnsNullOnException() throws Exception {
        Owner owner = this.mockOwner();
        Consumer consumer = this.mockConsumer(owner);
        Content content = this.mockContent(owner);
        Product product = this.mockProduct(owner, content);
        Pool pool = this.mockPool(product);

        doThrow(RuntimeException.class).when(this.mockConsumerCurator).merge(consumer);
        ContentAccessManager manager = this.createManager();

        assertNull(manager.getCertificate(consumer));
    }

    @Test
    public void testGetCertificateReturnsNullIfConsumerDoesNotSupportV3Cert() throws Exception {
        Owner owner = this.mockOwner();
        Consumer consumer = this.mockConsumer(owner);
        consumer.setFact(Consumer.Facts.SYSTEM_CERTIFICATE_VERSION, null); // remove v3 cert capability
        Content content = this.mockContent(owner);
        Product product = this.mockProduct(owner, content);
        Pool pool = this.mockPool(product);

        ContentAccessManager manager = this.createManager();

        assertNull(manager.getCertificate(consumer));
    }

    @Test
    public void testRemoveContentAccessCert() {
        Owner owner = this.mockOwner();
        Consumer consumer = this.mockConsumer(owner);
        ContentAccessManager manager = this.createManager();
        ContentAccessCertificate cert = this.mockContentAccessCertificate(consumer);
        consumer.setContentAccessCert(cert);
        doNothing().when(this.mockContentAccessCertCurator).delete(any(ContentAccessCertificate.class));
        manager.removeContentAccessCert(consumer);

        assertNull(consumer.getContentAccessCert());
    }

    @Test
    public void testHasCertChangedSinceOnlyCheckSecondsOnCertUpdatedDate() {
        Owner owner = this.mockOwner();
        Consumer consumer = this.mockConsumer(owner);
        ContentAccessManager manager = this.createManager();
        ContentAccessCertificate cert = this.mockContentAccessCertificate(consumer);
        consumer.setContentAccessCert(cert);

        // Set Owner's last content update to be long ago
        owner.setLastContentUpdate(TestUtil.createDateOffset(-1, 0, 0));

        // Set SCA cert 'updated' date (but without any rounding)
        long currentTimeMillis = System.currentTimeMillis();
        Date currentTime = new Date(currentTimeMillis);
        cert.setUpdated(currentTime);

        // Set expiration long enough into the future
        CertificateSerial serial = new CertificateSerial();
        serial.setExpiration(TestUtil.createDateOffset(1, 0, 0));
        cert.setSerial(serial);

        // Simulate 'lastUpdate' date sent by subscription-manager to be the same as
        // the SCA cert 'updated' date, but without the milliseconds
        Date lastUpdateRoundedDown = Util.roundDownToSeconds(currentTime);
        boolean changed = manager.hasCertChangedSince(consumer, lastUpdateRoundedDown);

        assertFalse(changed);
    }

    @Test
    public void testHasCertChangedSinceOnlyCheckSecondsOnCertExpirationDate() {
        Owner owner = this.mockOwner();
        Consumer consumer = this.mockConsumer(owner);
        ContentAccessManager manager = this.createManager();
        ContentAccessCertificate cert = this.mockContentAccessCertificate(consumer);
        consumer.setContentAccessCert(cert);

        // Set Owner's last content update to be long ago
        owner.setLastContentUpdate(TestUtil.createDateOffset(-1, 0, 0));

        // Set SCA cert 'updated' date (but without any rounding)
        long currentTimeMillis = System.currentTimeMillis();
        Date currentTime = new Date(currentTimeMillis);
        cert.setUpdated(currentTime);

        // Set expiration date to be the same as the 'updated' date, but without the rounding
        CertificateSerial serial = new CertificateSerial();
        serial.setExpiration(currentTime);
        cert.setSerial(serial);

        // Simulate 'lastUpdate' date sent by subscription-manager to be the same as
        // the SCA cert 'updated' date, but without the milliseconds
        Date lastUpdateRoundedDown = Util.roundDownToSeconds(currentTime);
        boolean changed = manager.hasCertChangedSince(consumer, lastUpdateRoundedDown);

        assertFalse(changed);
    }

    @Test
    public void testHasCertChangedSinceOwnerContentUpdated() {
        Owner owner = this.mockOwner();
        Consumer consumer = this.mockConsumer(owner);
        ContentAccessManager manager = this.createManager();
        ContentAccessCertificate cert = this.mockContentAccessCertificate(consumer);
        consumer.setContentAccessCert(cert);

        // Set Owner's last content update to be now
        owner.setLastContentUpdate(new Date());

        // Set SCA cert 'updated' date to be in the past
        Date lastUpdated = TestUtil.createDateOffset(-1, 0, 0);
        cert.setUpdated(lastUpdated);

        boolean changed = manager.hasCertChangedSince(consumer, lastUpdated);

        assertTrue(changed);
    }

    @Test
    public void testHasCertChangedSinceReturnAlwaysFalseWhenNotSimpleContentAccess() {
        Owner owner = this.mockOwner();
        owner.setContentAccessMode(ContentAccessMode.ENTITLEMENT.toDatabaseValue());
        Consumer consumer = this.mockConsumer(owner);
        ContentAccessManager manager = this.createManager();

        // Set SCA cert 'updated' date to be in the past
        Date lastUpdated = TestUtil.createDateOffset(-1, 0, 0);
        boolean changed = manager.hasCertChangedSince(consumer, lastUpdated);
        assertFalse(changed);

        // Set SCA cert 'updated' date to be in the future
        lastUpdated = TestUtil.createDateOffset(1, 0, 0);
        changed = manager.hasCertChangedSince(consumer, lastUpdated);
        assertFalse(changed);

        // Set SCA cert 'updated' date to be now
        changed = manager.hasCertChangedSince(consumer, new Date());
        assertFalse(changed);
    }

    @Test
    public void testHasCertChangedSinceReturnAlwaysTrueWhenSCACertIsNull() {
        Owner owner = this.mockOwner();
        Consumer consumer = this.mockConsumer(owner);
        consumer.setContentAccessCert(null);
        ContentAccessManager manager = this.createManager();

        // Set SCA cert 'updated' date to be in the past
        Date lastUpdated = TestUtil.createDateOffset(-1, 0, 0);
        boolean changed = manager.hasCertChangedSince(consumer, lastUpdated);
        assertTrue(changed);

        // Set SCA cert 'updated' date to be in the future
        lastUpdated = TestUtil.createDateOffset(1, 0, 0);
        changed = manager.hasCertChangedSince(consumer, lastUpdated);
        assertTrue(changed);

        // Set SCA cert 'updated' date to be now
        changed = manager.hasCertChangedSince(consumer, new Date());
        assertTrue(changed);
    }

    @Test
    public void testHasCertChangedSinceThrowsExceptionWhenConsumerIsNull() {
        ContentAccessManager manager = this.createManager();

        assertThrows(IllegalArgumentException.class, () -> manager.hasCertChangedSince(null, new Date()));
    }

    @Test
    public void testHasCertChangedSinceThrowsExceptionWhenDateIsNull() {
        Owner owner = this.mockOwner();
        Consumer consumer = this.mockConsumer(owner);
        ContentAccessManager manager = this.createManager();

        assertThrows(IllegalArgumentException.class, () -> manager.hasCertChangedSince(consumer, null));
    }

    @Test
    public void testSyncOwnerLastContentUpdateThrowsExceptionWhenOwnerIsNull() {
        ContentAccessManager manager = this.createManager();

        assertThrows(IllegalArgumentException.class, () -> manager.syncOwnerLastContentUpdate(null));
    }
}
