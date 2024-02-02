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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.audit.EventSink;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.controller.ContentAccessManager.ContentAccessMode;
import org.candlepin.model.AbstractHibernateObject;
import org.candlepin.model.AnonymousCloudConsumer;
import org.candlepin.model.AnonymousCloudConsumerCurator;
import org.candlepin.model.AnonymousContentAccessCertificate;
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
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.pki.KeyPairGenerator;
import org.candlepin.pki.certs.AnonymousCertificateGenerator;
import org.candlepin.pki.certs.ContentAccessCertificateGenerator;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;
import org.candlepin.util.X509V3ExtensionUtil;

import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @Mock
    private EventSink mockEventSink;
    @Mock
    private CertificateSerialCurator mockCertSerialCurator;
    @Mock
    private ConsumerCurator mockConsumerCurator;
    @Mock
    private ConsumerTypeCurator mockConsumerTypeCurator;
    @Mock
    private ContentAccessCertificateCurator mockContentAccessCertCurator;
    @Mock
    private OwnerCurator mockOwnerCurator;
    @Mock
    private EnvironmentCurator mockEnvironmentCurator;
    @Mock
    private EntitlementCurator mockEntitlementCurator;
    @Mock
    private AnonymousCloudConsumerCurator mockAnonCloudConsumerCurator;
    @Mock
    private KeyPairGenerator keyPairGenerator;
    @Mock
    private ContentAccessCertificateGenerator contentAccessCertificateGenerator;
    @Mock
    private AnonymousCertificateGenerator anonymousCertificateGenerator;

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

        // FIXME: This mess of mocks is why we should not be using mocks in this way. We should be
        // using a test database framework and our actual curators and objects.

        doAnswer(new PersistSimulator<>()).when(this.mockOwnerCurator).merge(any(Owner.class));
        doAnswer(new PersistSimulator<>()).when(this.mockConsumerCurator).merge(any(Consumer.class));
        doAnswer(new PersistSimulator<>()).when(this.mockContentAccessCertCurator)
            .create(any(ContentAccessCertificate.class));
        when(this.keyPairGenerator.getKeyPair(any(Consumer.class))).thenReturn(testingKeyPair);
        when(this.keyPairGenerator.generateKeyPair()).thenReturn(testingKeyPair);

        doAnswer(iom -> {
            CertificateSerial serial = iom.getArgument(0);

            if (serial != null) {
                serial.setId(Util.generateUniqueLong());
            }

            return serial;
        }).when(this.mockCertSerialCurator).create(any(CertificateSerial.class));

        EntityManager entityManager = mock(EntityManager.class);
        TestUtil.mockTransactionalFunctionality(entityManager, this.mockConsumerCurator,
            this.mockAnonCloudConsumerCurator);
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

    private ContentAccessManager createManager() {
        return new ContentAccessManager(
            this.config, this.mockContentAccessCertCurator, this.mockOwnerCurator, this.mockConsumerCurator,
            this.mockConsumerTypeCurator, this.mockEventSink, this.contentAccessCertificateGenerator,
            this.anonymousCertificateGenerator);
    }

    private Owner mockOwner() {
        Owner owner = new Owner()
            .setId("test_owner")
            .setKey("test_owner")
            .setContentAccessModeList(entitlementMode + "," + orgEnvironmentMode)
            .setContentAccessMode(orgEnvironmentMode);

        doReturn(owner).when(this.mockOwnerCurator).findOwnerById(owner.getId());

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

        doReturn(type).when(this.mockConsumerTypeCurator).getConsumerType(consumer);

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

    private ContentAccessCertificate mockContentAccessCertificate(Consumer consumer) {
        ContentAccessCertificate cert = new ContentAccessCertificate();
        cert.setId("123456789");
        cert.setKey("test-key");
        cert.setCert("test-cert");
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

        Owner owner = new Owner()
            .setKey("test_owner")
            .setDisplayName("test_owner")
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
        Owner owner = new Owner()
            .setKey("test_owner")
            .setDisplayName("test_owner");

        String updatedAccessModeList = entitlementMode;
        String updatedAccessMode = orgEnvironmentMode;

        ContentAccessManager manager = this.createManager();

        assertThrows(IllegalArgumentException.class,
            () -> manager.updateOwnerContentAccess(owner, updatedAccessModeList, updatedAccessMode));
    }

    @Test
    public void testUpdateOwnerContentAccessExistingModeNotInUpdatedListDefaultPresent() {
        String initialAccessModeList = entitlementMode + "," + orgEnvironmentMode;
        String initialAccessMode = orgEnvironmentMode;

        Owner owner = new Owner()
            .setKey("test_owner")
            .setDisplayName("test_owner")
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

        Owner owner = new Owner()
            .setKey("test_owner")
            .setDisplayName("test_owner")
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

        Owner owner = new Owner()
            .setKey("test_owner")
            .setDisplayName("test_owner")
            .setContentAccessModeList(initialAccessModeList)
            .setContentAccessMode(initialAccessMode);

        String updatedAccessModeList = orgEnvironmentMode;
        String updatedAccessMode = entitlementMode;

        ContentAccessManager manager = this.createManager();

        assertThrows(IllegalArgumentException.class,
            () -> manager.updateOwnerContentAccess(owner, updatedAccessModeList, updatedAccessMode));
    }

    @Test
    public void testUpdateOwnerContentAccessDefaultModeNotInUpdatedList() {
        String initialAccessModeList = entitlementMode + "," + orgEnvironmentMode;
        String initialAccessMode = orgEnvironmentMode;

        Owner owner = new Owner()
            .setKey("test_owner")
            .setDisplayName("test_owner")
            .setContentAccessModeList(initialAccessModeList)
            .setContentAccessMode(initialAccessMode);

        String updatedAccessModeList = entitlementMode;
        String updatedAccessMode = "";

        // We should convert the requested access mode to the default value, which is still invalid
        // as the default is not in the list.

        ContentAccessManager manager = this.createManager();

        assertThrows(IllegalArgumentException.class,
            () -> manager.updateOwnerContentAccess(owner, updatedAccessModeList, updatedAccessMode));
    }

    @Test
    public void testUpdateOwnerContentAccessModeNoList() {
        Owner owner = new Owner()
            .setKey("test_owner")
            .setDisplayName("test_owner");

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

    @Test
    public void testGetCertificateReturnsNullOnException() {
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
    public void testGetCertificateReturnsNullIfConsumerDoesNotSupportV3Cert() {
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

    @Test
    public void testGetCertificateForAnonConsumerWithNullAnonymousCloudConsumer() {
        this.config.setProperty(ConfigProperties.STANDALONE, "false");
        ContentAccessManager manager = this.createManager();
        AnonymousCloudConsumer consumer = null;

        assertThrows(IllegalArgumentException.class, () ->
            manager.getCertificate(consumer));
    }

    @Test
    public void testGetCertificateForAnonConsumerWithStandaloneMode() {
        this.config.setProperty(ConfigProperties.STANDALONE, "true");
        AnonymousCloudConsumer consumer = createAnonConsumer();

        ContentAccessManager manager = this.createManager();

        assertThrows(RuntimeException.class, () -> manager.getCertificate(consumer));
    }

    @Test
    public void testGetCertificateForAnonConsumerWithExistingCertificate() throws Exception {
        this.config.setProperty(ConfigProperties.STANDALONE, "false");
        CertificateSerial serial = new CertificateSerial(12345L);
        serial.setExpiration(Util.tomorrow());
        AnonymousContentAccessCertificate expected = createAnonCertificate(serial);
        AnonymousCloudConsumer consumer = createAnonConsumer();
        consumer.setContentAccessCert(expected);

        ContentAccessManager manager = this.createManager();

        AnonymousContentAccessCertificate actual = manager.getCertificate(consumer);

        assertThat(actual)
            .isNotNull()
            .returns(expected.getId(), AnonymousContentAccessCertificate::getId)
            .returns(expected.getCert(), AnonymousContentAccessCertificate::getCert)
            .returns(expected.getCreated(), AnonymousContentAccessCertificate::getCreated)
            .returns(expected.getUpdated(), AnonymousContentAccessCertificate::getUpdated)
            .returns(expected.getAnonymousCloudConsumer(),
                AnonymousContentAccessCertificate::getAnonymousCloudConsumer)
            .returns(expected.getKey(), AnonymousContentAccessCertificate::getKey)
            .returns(expected.getSerial(), AnonymousContentAccessCertificate::getSerial);
    }

    @Test
    public void testGetCertificateForAnonConsumerWithNoExistingCertificate() throws Exception {
        this.config.setProperty(ConfigProperties.STANDALONE, "false");

        AnonymousCloudConsumer consumer = createAnonConsumer();
        consumer.setContentAccessCert(null);
        consumer.setProductIds(List.of(TestUtil.randomString()));

        CertificateSerial expectedSerial = new CertificateSerial(678910L);
        expectedSerial.setExpiration(Util.tomorrow());
        AnonymousContentAccessCertificate expected = createAnonCertificate(expectedSerial);
        when(this.anonymousCertificateGenerator.createCertificate(consumer)).thenReturn(expected);

        ContentAccessManager manager = this.createManager();
        AnonymousContentAccessCertificate actual = manager.getCertificate(consumer);

        assertThat(actual)
            .isNotNull()
            .returns(expected.getId(), AnonymousContentAccessCertificate::getId)
            .returns(expected.getCert(), AnonymousContentAccessCertificate::getCert)
            .returns(expected.getCreated(), AnonymousContentAccessCertificate::getCreated)
            .returns(expected.getUpdated(), AnonymousContentAccessCertificate::getUpdated)
            .returns(expected.getAnonymousCloudConsumer(),
                AnonymousContentAccessCertificate::getAnonymousCloudConsumer)
            .returns(expected.getKey(), AnonymousContentAccessCertificate::getKey)
            .returns(expected.getSerial(), AnonymousContentAccessCertificate::getSerial);
    }

    @Test
    public void testGetCertificateForAnonConsumerWithExpiredCertificate() throws Exception {
        this.config.setProperty(ConfigProperties.STANDALONE, "false");

        CertificateSerial expiredSerial = new CertificateSerial(12345L);
        expiredSerial.setExpiration(TestUtil.createDateOffset(0, 0, -7));

        AnonymousContentAccessCertificate cert = createAnonCertificate(expiredSerial);

        List<String> prodIds = List.of("prod-id");
        AnonymousCloudConsumer consumer = createAnonConsumer();
        consumer.setProductIds(prodIds);
        consumer.setContentAccessCert(cert);

        CertificateSerial expectedSerial = new CertificateSerial(678910L);
        expectedSerial.setExpiration(Util.tomorrow());
        AnonymousContentAccessCertificate expected = new AnonymousContentAccessCertificate();
        expected.setId("id-2");
        expected.setKey("key-2");
        expected.setCert("cert-2");
        expected.setSerial(expectedSerial);

        when(this.anonymousCertificateGenerator.createCertificate(consumer)).thenReturn(expected);

        ContentAccessManager manager = this.createManager();
        AnonymousContentAccessCertificate actual = manager.getCertificate(consumer);

        assertThat(actual)
            .isNotNull()
            .returns(expected.getId(), AnonymousContentAccessCertificate::getId)
            .returns(expected.getCert(), AnonymousContentAccessCertificate::getCert)
            .returns(expected.getCreated(), AnonymousContentAccessCertificate::getCreated)
            .returns(expected.getUpdated(), AnonymousContentAccessCertificate::getUpdated)
            .returns(expected.getAnonymousCloudConsumer(),
                AnonymousContentAccessCertificate::getAnonymousCloudConsumer)
            .returns(expected.getKey(), AnonymousContentAccessCertificate::getKey)
            .returns(expected.getSerial(), AnonymousContentAccessCertificate::getSerial);
    }

    private AnonymousContentAccessCertificate createAnonCertificate(CertificateSerial serial) {
        int i = TestUtil.randomInt();
        AnonymousContentAccessCertificate expected = new AnonymousContentAccessCertificate();
        expected.setId("id-" + i);
        expected.setKey("key-" + i);
        expected.setCert("cert-" + i);
        expected.setSerial(serial);
        return expected;
    }

    private AnonymousCloudConsumer createAnonConsumer() {
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer();
        consumer.setId("id");
        consumer.setUuid("uuid");
        consumer.setCloudAccountId("account-id");
        consumer.setCloudInstanceId("instance-id");
        consumer.setCloudProviderShortName(TestUtil.randomString());
        return consumer;
    }
}
