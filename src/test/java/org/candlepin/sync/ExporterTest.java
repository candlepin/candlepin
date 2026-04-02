/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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
package org.candlepin.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.auth.Principal;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.StandardTranslator;
import org.candlepin.dto.manifest.v1.ConsumerDTO;
import org.candlepin.dto.manifest.v1.ProductDTO;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.CdnCurator;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.DistributorVersion;
import org.candlepin.model.DistributorVersionCapability;
import org.candlepin.model.DistributorVersionCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.KeyPairData;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.model.SCACertificate;
import org.candlepin.pki.CryptoCapabilitiesException;
import org.candlepin.pki.CryptoManager;
import org.candlepin.pki.Scheme;
import org.candlepin.pki.certs.SCACertificateGenerator;
import org.candlepin.policy.js.export.ExportRules;
import org.candlepin.service.EntitlementCertServiceAdapter;
import org.candlepin.test.CryptoUtil;
import org.candlepin.test.TestUtil;
import org.candlepin.util.ObjectMapperFactory;
import org.candlepin.version.VersionUtil;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyException;
import java.security.KeyPair;
import java.security.cert.CertificateEncodingException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;


// TODO: FIXME: Rewrite this test to not be so reliant upon mocks. It's making things incredibly brittle and
// wasting dev time tracking down non-issues when a mock silently fails because the implementation changes.



/**
 * ExporterTest
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ExporterTest {
    private static Stream<Arguments> schemeSource() {
        return CryptoUtil.SUPPORTED_SCHEMES.values()
            .stream()
            .map(Arguments::of);
    }

    @Mock
    private ConsumerTypeCurator consumerTypeCurator;
    @Mock
    private OwnerCurator ownerCurator;
    @Mock
    private RulesCurator rulesCurator;
    @Mock
    private EntitlementCertServiceAdapter entitlementCertService;
    @Mock
    private EntitlementCurator entitlementCurator;
    @Mock
    private DistributorVersionCurator distributorVersionCurator;
    @Mock
    private CdnCurator cdnCurator;
    @Mock
    private EnvironmentCurator environmentCurator;
    @Mock
    private ExportRules exportRules;
    @Mock
    private PrincipalProvider principalProvider;
    @Mock
    private SCACertificateGenerator scaCertificateGenerator;

    private DevConfig config;
    private CryptoManager cryptoManager;
    private ObjectMapper mapper;

    private ModelTranslator translator;
    private SyncUtils syncUtil;
    private MetaExporter metaExporter;
    private ConsumerExporter consumerExporter;
    private ConsumerTypeExporter consumerTypeExporter;
    private RulesExporter rulesExporter;
    private ProductExporter productExporter;
    private CdnExporter cdnExporter;
    private DistributorVersionExporter distributorVersionExporter;
    private EntitlementExporter entitlementExporter;
    private SchemeFileExporter schemeFileExporter;

    @BeforeEach
    public void setUp() {
        this.config = TestConfig.defaults();
        this.cryptoManager = CryptoUtil.getCryptoManager(this.config);
        this.mapper = ObjectMapperFactory.getSyncObjectMapper(this.config);

        metaExporter = new MetaExporter();
        translator = new StandardTranslator(consumerTypeCurator, environmentCurator, ownerCurator);
        consumerExporter = new ConsumerExporter(translator);
        consumerTypeExporter = new ConsumerTypeExporter(translator);
        rulesExporter = new RulesExporter(rulesCurator);
        productExporter = new ProductExporter(translator);
        entitlementExporter = new EntitlementExporter(translator);
        distributorVersionExporter = new DistributorVersionExporter(translator);
        schemeFileExporter = new SchemeFileExporter(mapper);
        cdnExporter = new CdnExporter(translator);
        syncUtil = new SyncUtils(config);
        when(exportRules.canExport(any(Entitlement.class))).thenReturn(Boolean.TRUE);
    }

    private KeyPairData generateConsumerKeyPairData(Scheme scheme) throws KeyException {
        if (scheme == null) {
            scheme = this.cryptoManager.getDefaultCryptoScheme();
        }

        KeyPair keypair = CryptoUtil.generateKeyPair(scheme);

        return new KeyPairData()
            .setPublicKeyData(keypair.getPublic().getEncoded())
            .setPrivateKeyData(keypair.getPrivate().getEncoded())
            .setAlgorithm(scheme.keyAlgorithm());
    }

    private Exporter buildExporter() {
        return new Exporter(
            this.consumerTypeCurator,
            this.metaExporter,
            this.consumerExporter,
            this.consumerTypeExporter,
            this.rulesExporter,
            this.entitlementCertService,
            this.productExporter,
            this.entitlementCurator,
            this.entitlementExporter,
            this.cryptoManager,
            this.config,
            this.exportRules,
            this.principalProvider,
            this.distributorVersionCurator,
            this.distributorVersionExporter,
            this.cdnCurator,
            this.cdnExporter,
            this.syncUtil,
            this.mapper,
            this.translator,
            this.scaCertificateGenerator,
            this.schemeFileExporter);
    }

    @Test
    public void exportProducts() throws Exception {
        config.setProperty(ConfigProperties.SYNC_WORK_DIR, "/tmp/");
        Entitlement ent = mock(Entitlement.class);

        Rules mrules = mock(Rules.class);
        Principal principal = mock(Principal.class);
        IdentityCertificate idcert = new IdentityCertificate();

        Owner owner = TestUtil.createOwner("Example-Corporation");

        Product prod12345 = TestUtil.createProduct("12345", "RHEL Product");
        prod12345.setMultiplier(1L);
        prod12345.setCreated(new Date());
        prod12345.setUpdated(new Date());
        prod12345.setAttributes(Collections.emptyMap());

        Product mktProd = TestUtil.createProduct("MKT-prod", "RHEL Product");
        mktProd.setMultiplier(1L);
        mktProd.setCreated(new Date());
        mktProd.setUpdated(new Date());
        mktProd.setAttributes(Collections.emptyMap());

        Product mktSubProd = TestUtil.createProduct("MKT-sub-prod", "Sub Product");
        mktSubProd.setMultiplier(1L);
        mktSubProd.setCreated(new Date());
        mktSubProd.setUpdated(new Date());
        mktSubProd.setAttributes(Collections.emptyMap());

        Product subProvidedProduct332211 = TestUtil.createProduct("332211", "Sub Product");
        subProvidedProduct332211.setMultiplier(1L);
        subProvidedProduct332211.setCreated(new Date());
        subProvidedProduct332211.setUpdated(new Date());
        subProvidedProduct332211.setAttributes(Collections.emptyMap());

        mktProd.addProvidedProduct(prod12345);
        mktProd.setDerivedProduct(mktSubProd);
        mktSubProd.addProvidedProduct(subProvidedProduct332211);

        Pool pool = TestUtil.createPool(owner)
            .setId("MockedPoolId")
            .setProduct(mktProd);

        when(ent.getPool()).thenReturn(pool);
        when(mrules.getRules()).thenReturn("foobar");
        when(rulesCurator.getRules()).thenReturn(mrules);
        when(principalProvider.get()).thenReturn(principal);
        when(principal.getUsername()).thenReturn("testUser");
        idcert.setSerial(new CertificateSerial(10L, new Date()));
        idcert.setKey("euh0876puhapodifbvj094");
        idcert.setCert("hpj-08ha-w4gpoknpon*)&^%#");
        idcert.setCreated(new Date());
        idcert.setUpdated(new Date());

        KeyPairData keyPairData = this.generateConsumerKeyPairData(null);

        when(consumerTypeCurator.listAll()).thenReturn(List.of(new ConsumerType("system")));
        when(cdnCurator.listAll()).thenReturn(new LinkedList<>());
        when(consumerTypeCurator.listAll()).thenReturn(new LinkedList<>());
        when(ownerCurator.findOwnerById(owner.getId())).thenReturn(owner);

        Consumer consumer = new Consumer()
            .setEntitlements(Set.of(ent))
            .setIdCert(idcert)
            .setKeyPairData(keyPairData)
            .setOwner(owner);

        // FINALLY test this badboy
        Exporter exporter = this.buildExporter();
        File export = exporter.getFullExport(consumer, null, null, null);

        // VERIFY
        assertNotNull(export);
        verifyContent(export, "export/products/12345.json", new VerifyProduct("12345.json", prod12345));
        verifyContent(export, "export/products/MKT-prod.json", new VerifyProduct("MKT-prod.json", mktProd));

        verifyContent(export, "export/products/332211.json",
            new VerifyProduct("332211.json", subProvidedProduct332211));
        verifyContent(export, "export/products/MKT-sub-prod.json",
            new VerifyProduct("MKT-sub-prod.json", mktSubProd));

        verifySignatureFile(export, null);

        FileUtils.deleteDirectory(export.getParentFile());
        assertTrue(new File("/tmp/consumer_export.zip").delete());
        assertTrue(new File("/tmp/12345.json").delete());
        assertTrue(new File("/tmp/332211.json").delete());
    }

    @Test
    public void doNotExportDirtyEntitlements() throws Exception {
        config.setProperty(ConfigProperties.SYNC_WORK_DIR, "/tmp/");
        Entitlement ent = mock(Entitlement.class);
        Principal principal = mock(Principal.class);
        IdentityCertificate idcert = new IdentityCertificate();

        when(principalProvider.get()).thenReturn(principal);
        when(principal.getUsername()).thenReturn("testUser");
        when(ent.isDirty()).thenReturn(true);
        idcert.setSerial(new CertificateSerial(10L, new Date()));
        idcert.setKey("euh0876puhapodifbvj094");
        idcert.setCert("hpj-08ha-w4gpoknpon*)&^%#");
        idcert.setCreated(new Date());
        idcert.setUpdated(new Date());

        KeyPairData keyPairData = this.generateConsumerKeyPairData(null);

        Consumer consumer = new Consumer()
            .setEntitlements(Set.of(ent))
            .setIdCert(idcert)
            .setKeyPairData(keyPairData);

        when(entitlementCurator.listByConsumer(consumer)).thenReturn(List.of(ent));
        when(consumerTypeCurator.listAll()).thenReturn(List.of(new ConsumerType("system")));

        Exporter exporter = this.buildExporter();
        assertThrows(ExportCreationException.class, () -> exporter.getFullExport(consumer, null, null, null));
    }

    @Test
    public void exportMetadata() throws Exception {
        config.setProperty(ConfigProperties.SYNC_WORK_DIR, "/tmp/");
        Instant start = Instant.now().minusSeconds(1L);
        Rules mrules = mock(Rules.class);
        Principal principal = mock(Principal.class);
        IdentityCertificate idcert = new IdentityCertificate();

        when(mrules.getRules()).thenReturn("foobar");
        when(rulesCurator.getRules()).thenReturn(mrules);
        when(principalProvider.get()).thenReturn(principal);
        when(principal.getUsername()).thenReturn("testUser");

        idcert.setSerial(new CertificateSerial(10L, new Date()));
        idcert.setKey("euh0876puhapodifbvj094");
        idcert.setCert("hpj-08ha-w4gpoknpon*)&^%#");
        idcert.setCreated(new Date());
        idcert.setUpdated(new Date());

        KeyPairData keyPairData = this.generateConsumerKeyPairData(null);

        Consumer consumer = new Consumer()
            .setIdCert(idcert)
            .setKeyPairData(keyPairData);

        when(consumerTypeCurator.listAll()).thenReturn(List.of(new ConsumerType("system")));
        when(cdnCurator.listAll()).thenReturn(new LinkedList<>());

        // FINALLY test this badboy
        Exporter exporter = this.buildExporter();
        File export = exporter.getFullExport(consumer, null, null, null);

        // VERIFY
        assertNotNull(export);
        assertTrue(export.exists());
        verifyContent(export, "export/meta.json", new VerifyMetadata(new Date(start.toEpochMilli())));

        verifySignatureFile(export, null);

        // cleanup the mess
        FileUtils.deleteDirectory(export.getParentFile());
        assertTrue(new File("/tmp/consumer_export.zip").delete());
        assertTrue(new File("/tmp/meta.json").delete());
    }

    @Test
    public void exportIdentityCertificate() throws Exception {
        config.setProperty(ConfigProperties.SYNC_WORK_DIR, "/tmp/");
        Rules mrules = mock(Rules.class);
        Principal principal = mock(Principal.class);

        when(mrules.getRules()).thenReturn("foobar");
        when(rulesCurator.getRules()).thenReturn(mrules);
        when(principalProvider.get()).thenReturn(principal);
        when(principal.getUsername()).thenReturn("testUser");

        // specific to this test
        IdentityCertificate idcert = new IdentityCertificate();
        idcert.setSerial(new CertificateSerial(10L, new Date()));
        idcert.setKey("euh0876puhapodifbvj094");
        idcert.setCert("hpj-08ha-w4gpoknpon*)&^%#");
        idcert.setCreated(new Date());
        idcert.setUpdated(new Date());

        KeyPairData keyPairData = this.generateConsumerKeyPairData(null);

        Consumer consumer = new Consumer()
            .setIdCert(idcert)
            .setKeyPairData(keyPairData);

        when(consumerTypeCurator.listAll()).thenReturn(List.of(new ConsumerType("system")));
        when(cdnCurator.listAll()).thenReturn(new LinkedList<>());

        // FINALLY test this badboy
        Exporter exporter = this.buildExporter();
        File export = exporter.getFullExport(consumer, null, null, null);

        // VERIFY
        assertNotNull(export);
        assertTrue(export.exists());
        verifyContent(export, "export/upstream_consumer/10.pem", new VerifyIdentityCert("10.pem"));

        verifySignatureFile(export, null);
    }

    @Test
    public void exportConsumer() throws Exception {
        config.setProperty(ConfigProperties.SYNC_WORK_DIR, "/tmp/");
        config.setProperty(ConfigProperties.PREFIX_WEBURL, "localhost:8443/weburl");
        config.setProperty(ConfigProperties.PREFIX_APIURL, "localhost:8443/apiurl");
        Rules mrules = mock(Rules.class);
        Principal principal = mock(Principal.class);

        when(mrules.getRules()).thenReturn("foobar");
        when(rulesCurator.getRules()).thenReturn(mrules);
        when(principalProvider.get()).thenReturn(principal);
        when(principal.getUsername()).thenReturn("testUser");

        // specific to this test
        IdentityCertificate idcert = new IdentityCertificate();
        idcert.setSerial(new CertificateSerial(10L, new Date()));
        idcert.setKey("euh0876puhapodifbvj094");
        idcert.setCert("hpj-08ha-w4gpoknpon*)&^%#");
        idcert.setCreated(new Date());
        idcert.setUpdated(new Date());

        ConsumerType ctype = new ConsumerType(ConsumerTypeEnum.CANDLEPIN);
        ctype.setId("test-ctype");

        KeyPairData keyPairData = this.generateConsumerKeyPairData(null);

        Consumer consumer = new Consumer()
            .setUuid("8auuid")
            .setName("consumer_name")
            .setContentAccessMode("access_mode")
            .setType(ctype)
            .setIdCert(idcert)
            .setKeyPairData(keyPairData);

        when(consumerTypeCurator.getConsumerType(consumer)).thenReturn(ctype);
        when(consumerTypeCurator.get(ctype.getId())).thenReturn(ctype);
        when(consumerTypeCurator.listAll()).thenReturn(List.of(new ConsumerType("system")));
        when(cdnCurator.listAll()).thenReturn(new LinkedList<>());

        // FINALLY test this badboy
        Exporter exporter = this.buildExporter();
        File export = exporter.getFullExport(consumer, null, null, null);

        verifyContent(export, "export/consumer.json", new VerifyConsumer("consumer.json"));

        verifySignatureFile(export, null);
    }

    @Test
    public void exportDistributorVersions() throws Exception {
        config.setProperty(ConfigProperties.SYNC_WORK_DIR, "/tmp/");
        config.setProperty(ConfigProperties.PREFIX_WEBURL, "localhost:8443/weburl");
        config.setProperty(ConfigProperties.PREFIX_APIURL, "localhost:8443/apiurl");
        Rules mrules = mock(Rules.class);
        Principal principal = mock(Principal.class);

        when(mrules.getRules()).thenReturn("foobar");
        when(rulesCurator.getRules()).thenReturn(mrules);
        when(principalProvider.get()).thenReturn(principal);
        when(principal.getUsername()).thenReturn("testUser");

        IdentityCertificate idcert = new IdentityCertificate();
        idcert.setSerial(new CertificateSerial(10L, new Date()));
        idcert.setKey("euh0876puhapodifbvj094");
        idcert.setCert("hpj-08ha-w4gpoknpon*)&^%#");
        idcert.setCreated(new Date());
        idcert.setUpdated(new Date());

        ConsumerType ctype = new ConsumerType(ConsumerTypeEnum.CANDLEPIN);
        ctype.setId("test-ctype");

        KeyPairData keyPairData = this.generateConsumerKeyPairData(null);

        Consumer consumer = new Consumer()
            .setUuid("8auuid")
            .setName("consumer_name")
            .setContentAccessMode("access_mode")
            .setType(ctype)
            .setIdCert(idcert)
            .setKeyPairData(keyPairData);

        when(consumerTypeCurator.getConsumerType(consumer)).thenReturn(ctype);
        when(consumerTypeCurator.get(ctype.getId())).thenReturn(ctype);

        DistributorVersion dv = new DistributorVersion("test-dist-ver");
        Set<DistributorVersionCapability> dvcSet = new HashSet<>();
        dvcSet.add(new DistributorVersionCapability(dv, "capability-1"));
        dvcSet.add(new DistributorVersionCapability(dv, "capability-2"));
        dvcSet.add(new DistributorVersionCapability(dv, "capability-3"));
        dv.setCapabilities(dvcSet);
        List<DistributorVersion> dvList = new ArrayList<>();
        dvList.add(dv);
        when(distributorVersionCurator.listAll()).thenReturn(dvList);

        when(consumerTypeCurator.listAll()).thenReturn(List.of(new ConsumerType("system")));

        when(cdnCurator.listAll()).thenReturn(new LinkedList<>());
        when(consumerTypeCurator.listAll()).thenReturn(new LinkedList<>());

        // FINALLY test this badboy
        Exporter exporter = this.buildExporter();
        File export = exporter.getFullExport(consumer, null, null, null);

        verifyContent(export, "export/distributor_version/test-dist-ver.json",
            new VerifyDistributorVersion("test-dist-ver.json"));

        verifySignatureFile(export, null);
    }

    @Test
    public void testGetEntitlementExport() throws Exception {
        config.setProperty(ConfigProperties.SYNC_WORK_DIR, "/tmp/");

        ConsumerType ctype = new ConsumerType(ConsumerTypeEnum.CANDLEPIN);
        ctype.setId("test-ctype");
        KeyPairData keyPairData = this.generateConsumerKeyPairData(null);

        Consumer consumer = new Consumer()
            .setUuid("consumer")
            .setName("consumer_name")
            .setContentAccessMode("access_mode")
            .setType(ctype)
            .setKeyPairData(keyPairData);

        when(consumerTypeCurator.getConsumerType(consumer)).thenReturn(ctype);
        when(consumerTypeCurator.get(ctype.getId())).thenReturn(ctype);

        // Setup principal
        Principal principal = mock(Principal.class);
        when(principalProvider.get()).thenReturn(principal);
        when(principal.getUsername()).thenReturn("testUser");

        // Create dummy ent cert
        EntitlementCertificate entCert = new EntitlementCertificate();
        CertificateSerial entSerial = new CertificateSerial();
        entSerial.setId(123456L);
        entCert.setSerial(entSerial);
        entCert.setCert("ent-cert");
        entCert.setKey("ent-cert-key");

        // Create dummy content access cert
        SCACertificate cac = new SCACertificate();
        CertificateSerial cacSerial = new CertificateSerial();
        cacSerial.setId(654321L);
        cac.setSerial(cacSerial);
        cac.setCert("content-access-cert");
        cac.setKey("content-access-key");

        when(entitlementCertService.listForConsumer(consumer)).thenReturn(List.of(entCert));
        when(scaCertificateGenerator.generate(consumer)).thenReturn(cac);

        Exporter exporter = this.buildExporter();
        File export = exporter.getEntitlementExport(consumer, null);

        // Verify
        assertNotNull(export);
        assertTrue(export.exists());

        // Check consumer export has entitlement cert.
        assertTrue(verifyHasEntry(export, "export/entitlement_certificates/123456.pem"));

        // Check consumer export has content access cert.
        assertTrue(verifyHasEntry(export, "export/content_access_certificates/654321.pem"));

        verifySignatureFile(export, null);
    }

    @Test
    public void testGetEntitlementExportWithUnknownSerialId() throws Exception {
        config.setProperty(ConfigProperties.SYNC_WORK_DIR, "/tmp/");

        ConsumerType ctype = new ConsumerType(ConsumerTypeEnum.CANDLEPIN);
        ctype.setId("test-ctype");
        KeyPairData keyPairData = this.generateConsumerKeyPairData(null);

        Consumer consumer = new Consumer()
            .setUuid("consumer")
            .setName("consumer_name")
            .setContentAccessMode("access_mode")
            .setType(ctype)
            .setKeyPairData(keyPairData);

        doReturn(ctype).when(consumerTypeCurator).getConsumerType(consumer);
        doReturn(ctype).when(consumerTypeCurator).get(ctype.getId());

        // Setup principal
        Principal principal = mock(Principal.class);
        doReturn(principal).when(principalProvider).get();
        doReturn("testUser").when(principal).getUsername();

        // Create dummy ent cert
        EntitlementCertificate entCert = new EntitlementCertificate();
        CertificateSerial entSerial = new CertificateSerial();
        entSerial.setId(123456L);
        entCert.setSerial(entSerial);
        entCert.setCert("ent-cert");
        entCert.setKey("ent-cert-key");

        // Create dummy content access cert
        SCACertificate cac = new SCACertificate();
        CertificateSerial cacSerial = new CertificateSerial();
        cacSerial.setId(654321L);
        cac.setSerial(cacSerial);
        cac.setCert("content-access-cert");
        cac.setKey("content-access-key");

        doReturn(List.of(entCert)).when(entitlementCertService).listForConsumer(consumer);
        doReturn(cac).when(scaCertificateGenerator).generate(consumer);

        Exporter exporter = this.buildExporter();
        Set<Long> serials = Set.of(12345678910L);
        File export = exporter.getEntitlementExport(consumer, serials);

        // Verify
        assertNotNull(export);
        assertTrue(export.exists());

        // Check consumer export does not have entitlement cert.
        assertFalse(verifyHasEntry(export, "export/entitlement_certificates/123456.pem"));

        // Check consumer export does not have content access cert.
        assertFalse(verifyHasEntry(export, "export/content_access_certificates/654321.pem"));

        verifySignatureFile(export, null);
    }

    @Test
    public void testGetEntitlementExportWithValidEntitlementCertSerial() throws Exception {
        config.setProperty(ConfigProperties.SYNC_WORK_DIR, "/tmp/");

        ConsumerType ctype = new ConsumerType(ConsumerTypeEnum.CANDLEPIN);
        ctype.setId("test-ctype");

        KeyPairData keyPairData = this.generateConsumerKeyPairData(null);

        Consumer consumer = new Consumer()
            .setUuid("consumer")
            .setName("consumer_name")
            .setContentAccessMode("access_mode")
            .setType(ctype)
            .setKeyPairData(keyPairData);

        doReturn(ctype).when(consumerTypeCurator).getConsumerType(consumer);
        doReturn(ctype).when(consumerTypeCurator).get(ctype.getId());

        // Setup principal
        Principal principal = mock(Principal.class);
        doReturn(principal).when(principalProvider).get();
        doReturn("testUser").when(principal).getUsername();

        // Create dummy ent cert
        EntitlementCertificate entCert = new EntitlementCertificate();
        CertificateSerial entSerial = new CertificateSerial();
        entSerial.setId(123456L);
        entCert.setSerial(entSerial);
        entCert.setCert("ent-cert");
        entCert.setKey("ent-cert-key");

        // Create dummy content access cert
        SCACertificate cac = new SCACertificate();
        CertificateSerial cacSerial = new CertificateSerial();
        cacSerial.setId(654321L);
        cac.setSerial(cacSerial);
        cac.setCert("content-access-cert");
        cac.setKey("content-access-key");

        doReturn(List.of(entCert)).when(entitlementCertService).listForConsumer(consumer);
        doReturn(cac).when(scaCertificateGenerator).generate(consumer);

        Exporter exporter = this.buildExporter();
        Set<Long> serials = Set.of(entSerial.getId());
        File export = exporter.getEntitlementExport(consumer, serials);

        // Verify
        assertNotNull(export);
        assertTrue(export.exists());

        // Check consumer export has entitlement cert.
        assertTrue(verifyHasEntry(export, "export/entitlement_certificates/123456.pem"));

        // Check consumer export does not have content access cert.
        assertFalse(verifyHasEntry(export, "export/content_access_certificates/654321.pem"));

        verifySignatureFile(export, null);
    }

    @Test
    public void testGetEntitlementExportWithValidContentAccessCertSerial() throws Exception {
        config.setProperty(ConfigProperties.SYNC_WORK_DIR, "/tmp/");

        ConsumerType ctype = new ConsumerType(ConsumerTypeEnum.CANDLEPIN);
        ctype.setId("test-ctype");

        KeyPairData keyPairData = this.generateConsumerKeyPairData(null);

        Consumer consumer = new Consumer()
            .setUuid("consumer")
            .setName("consumer_name")
            .setContentAccessMode("access_mode")
            .setType(ctype)
            .setKeyPairData(keyPairData);

        doReturn(ctype).when(consumerTypeCurator).getConsumerType(consumer);
        doReturn(ctype).when(consumerTypeCurator).get(ctype.getId());

        // Setup principal
        Principal principal = mock(Principal.class);
        doReturn(principal).when(principalProvider).get();
        doReturn("testUser").when(principal).getUsername();

        // Create dummy ent cert
        EntitlementCertificate entCert = new EntitlementCertificate();
        CertificateSerial entSerial = new CertificateSerial();
        entSerial.setId(123456L);
        entCert.setSerial(entSerial);
        entCert.setCert("ent-cert");
        entCert.setKey("ent-cert-key");

        // Create dummy content access cert
        SCACertificate cac = new SCACertificate();
        CertificateSerial cacSerial = new CertificateSerial();
        cacSerial.setId(654321L);
        cac.setSerial(cacSerial);
        cac.setCert("content-access-cert");
        cac.setKey("content-access-key");

        doReturn(List.of(entCert)).when(entitlementCertService).listForConsumer(consumer);
        doReturn(cac).when(scaCertificateGenerator).generate(consumer);

        Exporter exporter = this.buildExporter();
        Set<Long> serials = Set.of(cacSerial.getId());
        File export = exporter.getEntitlementExport(consumer, serials);

        // Verify
        assertNotNull(export);
        assertTrue(export.exists());

        // Check consumer export does not have entitlement cert.
        assertFalse(verifyHasEntry(export, "export/entitlement_certificates/123456.pem"));

        // Check consumer export has content access cert.
        assertTrue(verifyHasEntry(export, "export/content_access_certificates/654321.pem"));

        verifySignatureFile(export, null);
    }

    @Test
    public void testGetFullExportWithUnknownScheme() throws KeyException {
        this.config.setProperty(ConfigProperties.SYNC_WORK_DIR, "/tmp/");

        IdentityCertificate idcert = new IdentityCertificate();
        idcert.setSerial(new CertificateSerial(10L, new Date()));
        idcert.setKey(TestUtil.randomString("key-"));
        idcert.setCert(TestUtil.randomString("cert-"));
        idcert.setCreated(new Date());
        idcert.setUpdated(new Date());

        Consumer consumer = CryptoUtil.configureConsumerWithNoSelectableScheme(new Consumer()
            .setIdCert(idcert)
            .setKeyPairData(generateConsumerKeyPairData(null)));

        Rules mockRules = mock(Rules.class);
        when(mockRules.getRules()).thenReturn("rules");

        when(rulesCurator.getRules()).thenReturn(mockRules);
        when(principalProvider.get()).thenReturn(new UserPrincipal("testUser", List.of(), false));
        when(consumerTypeCurator.listAll()).thenReturn(List.of(new ConsumerType("system")));
        when(cdnCurator.listAll()).thenReturn(new LinkedList<>());
        when(entitlementCurator.listByConsumer(consumer)).thenReturn(List.of());
        when(entitlementCertService.listForConsumer(consumer)).thenReturn(List.of());

        Exporter exporter = this.buildExporter();

        assertThrows(CryptoCapabilitiesException.class, () -> exporter.getFullExport(consumer,
            TestUtil.randomString(), TestUtil.randomString(), TestUtil.randomString()));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testGetFullExportWithSupportedSchemes(Scheme scheme) throws Exception {
        this.config.setProperty(ConfigProperties.SYNC_WORK_DIR, "/tmp/");

        IdentityCertificate idcert = new IdentityCertificate();
        idcert.setSerial(new CertificateSerial(10L, new Date()));
        idcert.setKey(TestUtil.randomString("key-"));
        idcert.setCert(TestUtil.randomString("cert-"));
        idcert.setCreated(new Date());
        idcert.setUpdated(new Date());

        Consumer consumer = CryptoUtil.configureConsumerForSchemes(new Consumer()
            .setIdCert(idcert)
            .setKeyPairData(generateConsumerKeyPairData(scheme)), scheme);

        Rules mockRules = mock(Rules.class);
        when(mockRules.getRules()).thenReturn("rules");

        when(rulesCurator.getRules()).thenReturn(mockRules);
        when(principalProvider.get()).thenReturn(new UserPrincipal("testUser", List.of(), false));
        when(consumerTypeCurator.listAll()).thenReturn(List.of(new ConsumerType("system")));
        when(cdnCurator.listAll()).thenReturn(new LinkedList<>());
        when(entitlementCurator.listByConsumer(consumer)).thenReturn(List.of());
        when(entitlementCertService.listForConsumer(consumer)).thenReturn(List.of());

        Exporter exporter = this.buildExporter();
        File export = exporter.getFullExport(consumer, TestUtil.randomString(), TestUtil.randomString(),
            TestUtil.randomString());

        assertNotNull(export);
        assertTrue(export.exists());
        verifySignatureFile(export, scheme);
    }

    @Test
    public void testGetEntitlementExportWithUnknownScheme() throws KeyException {
        this.config.setProperty(ConfigProperties.SYNC_WORK_DIR, "/tmp/");

        Consumer consumer = CryptoUtil.configureConsumerWithNoSelectableScheme(new Consumer()
            .setUuid("consumer")
            .setName("consumer_name")
            .setKeyPairData(generateConsumerKeyPairData(null)));

        when(principalProvider.get()).thenReturn(new UserPrincipal("testUser", List.of(), false));
        when(entitlementCertService.listForConsumer(consumer)).thenReturn(List.of());

        Exporter exporter = this.buildExporter();

        ExportCreationException ex = assertThrows(ExportCreationException.class,
            () -> exporter.getEntitlementExport(consumer, null));
        assertInstanceOf(CryptoCapabilitiesException.class, ex.getCause());
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testGetEntitlementExportWithSupportedSchemes(Scheme scheme) throws Exception {
        config.setProperty(ConfigProperties.SYNC_WORK_DIR, "/tmp/");

        Consumer consumer = CryptoUtil.configureConsumerForSchemes(new Consumer()
            .setUuid("consumer")
            .setName("consumer_name")
            .setKeyPairData(generateConsumerKeyPairData(scheme)), scheme);

        when(principalProvider.get()).thenReturn(new UserPrincipal("testUser", List.of(), false));
        when(entitlementCertService.listForConsumer(consumer)).thenReturn(List.of());

        Exporter exporter = this.buildExporter();
        File export = exporter.getEntitlementExport(consumer, null);

        assertNotNull(export);
        assertTrue(export.exists());
        verifySignatureFile(export, scheme);
    }

    /**
     * return true if export has a given entry named name.
     * @param export zip file to inspect
     * @param name entry
     * @return
     */
    private boolean verifyHasEntry(File export, String name) {
        ZipInputStream zis = null;
        boolean found = false;

        try {
            zis = new ZipInputStream(new FileInputStream(export));
            ZipEntry entry = null;

            while ((entry = zis.getNextEntry()) != null) {
                byte[] buf = new byte[1024];

                if (entry.getName().equals("consumer_export.zip")) {
                    OutputStream os = new FileOutputStream("/tmp/consumer_export.zip");

                    int n;
                    while ((n = zis.read(buf, 0, 1024)) > -1) {
                        os.write(buf, 0, n);
                    }
                    os.flush();
                    os.close();
                    File exportdata = new File("/tmp/consumer_export.zip");
                    // open up the zip and look for the metadata
                    found = verifyHasEntry(exportdata, name);
                }
                else if (entry.getName().equals(name)) {
                    found = true;
                }

                zis.closeEntry();
            }

        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            if (zis != null) {
                try {
                    zis.close();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return found;
    }

    private void verifyContent(File export, String name, Verify v) {
        ZipInputStream zis = null;

        try {
            zis = new ZipInputStream(new FileInputStream(export));
            ZipEntry entry = null;
            while ((entry = zis.getNextEntry()) != null) {
                byte[] buf = new byte[1024];

                if (entry.getName().equals("consumer_export.zip")) {
                    OutputStream os = new FileOutputStream("/tmp/consumer_export.zip");

                    int n;
                    while ((n = zis.read(buf, 0, 1024)) > -1) {
                        os.write(buf, 0, n);
                    }
                    os.flush();
                    os.close();
                    File exportdata = new File("/tmp/consumer_export.zip");
                    // open up the zip and look for the metadata
                    verifyContent(exportdata, name, v);
                }
                else if (entry.getName().equals(name)) {
                    v.verify(zis, buf);
                }
                zis.closeEntry();
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        finally {
            if (zis != null) {
                try {
                    zis.close();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void verifySignatureFile(File export, Scheme scheme)
        throws CertificateEncodingException, IOException {

        if (export == null) {
            throw new IllegalArgumentException("export is null");
        }

        if (scheme == null) {
            scheme = this.cryptoManager.getDefaultCryptoScheme();
        }

        try (ZipFile zipFile = new ZipFile(export)) {
            ZipEntry consumerExportEntry = zipFile.getEntry("consumer_export.zip");
            assertNotNull(consumerExportEntry, "consumer_export.zip not found");

            File tempZip = File.createTempFile("temp", ".zip");
            tempZip.deleteOnExit();
            try (InputStream is = zipFile.getInputStream(consumerExportEntry);
                OutputStream os = new FileOutputStream(tempZip)) {
                is.transferTo(os);
            }

            try (ZipFile archiveZip = new ZipFile(tempZip)) {
                ZipEntry schemeEntry = archiveZip.getEntry("export/" + SchemeFile.FILENAME);
                assertNotNull(schemeEntry, "scheme file not found");

                SchemeFile actual = this.mapper
                    .readValue(archiveZip.getInputStream(schemeEntry), SchemeFile.class);

                String expectedCert = Base64.getEncoder()
                    .encodeToString(scheme.certificate().getEncoded());

                assertThat(actual)
                    .isNotNull()
                    .returns(scheme.name(), SchemeFile::name)
                    .returns(expectedCert, SchemeFile::certificate)
                    .returns(scheme.signatureAlgorithm(), SchemeFile::signatureAlgorithm)
                    .returns(scheme.keyAlgorithm(), SchemeFile::keyAlgorithm);
            }
        }
    }

    public interface Verify {
        void verify(ZipInputStream zis, byte[] buf) throws IOException;
    }

    public static class VerifyMetadata implements Verify {
        private final Date start;

        public VerifyMetadata(Date start) {
            this.start = start;
        }

        public void verify(ZipInputStream zis, byte[] buf) throws IOException {
            OutputStream os = new FileOutputStream("/tmp/meta.json");
            int n;
            while ((n = zis.read(buf, 0, 1024)) > -1) {
                os.write(buf, 0, n);
            }
            os.flush();
            os.close();

            DevConfig config = TestConfig.custom(Map.of(
                ConfigProperties.FAIL_ON_UNKNOWN_IMPORT_PROPERTIES, "false"));

            ObjectMapper mapper = ObjectMapperFactory.getSyncObjectMapper(config);

            Meta m = mapper.readValue(new FileInputStream("/tmp/meta.json"), Meta.class);

            Map<String, String> vmap = VersionUtil.getVersionMap();

            assertNotNull(m);
            assertEquals(vmap.get("version") + '-' + vmap.get("release"), m.getVersion());
            assertTrue(start.before(m.getCreated()));
        }
    }

    public static class VerifyProduct implements Verify {
        private final String filename;
        private final Product originalProduct;

        public VerifyProduct(String filename, Product originalProduct) {
            this.filename = filename;
            this.originalProduct = originalProduct;
        }

        public void verify(ZipInputStream zis, byte[] buf) throws IOException {
            OutputStream os = new FileOutputStream("/tmp/" + filename);
            int n;
            while ((n = zis.read(buf, 0, 1024)) > -1) {
                os.write(buf, 0, n);
            }
            os.flush();
            os.close();

            DevConfig config = TestConfig.custom(Map.of(
                ConfigProperties.FAIL_ON_UNKNOWN_IMPORT_PROPERTIES, "false"));

            ObjectMapper mapper = ObjectMapperFactory.getSyncObjectMapper(config);

            ProductDTO prod = mapper.readValue(new FileInputStream("/tmp/" + filename), ProductDTO.class);

            assertEquals(originalProduct.getId(), prod.getId());
            assertEquals(originalProduct.getName(), prod.getName());
            assertEquals(originalProduct.getMultiplier(), prod.getMultiplier());
            assertEquals(originalProduct.getCreated(), prod.getCreated());
            assertEquals(originalProduct.getUpdated(), prod.getUpdated());
        }
    }

    public static class VerifyIdentityCert implements Verify {
        private final String filename;

        public VerifyIdentityCert(String filename) {
            this.filename = filename;
        }

        public void verify(ZipInputStream zis, byte[] buf) throws IOException {
            OutputStream os = new FileOutputStream("/tmp/" + filename);
            int n;
            while ((n = zis.read(buf, 0, 1024)) > -1) {
                os.write(buf, 0, n);
            }
            os.flush();
            os.close();

            BufferedReader br = new BufferedReader(new FileReader("/tmp/" + filename));
            assertEquals("hpj-08ha-w4gpoknpon*)&^%#euh0876puhapodifbvj094", br.readLine());
            br.close();
        }
    }

    public static class VerifyConsumer implements Verify {
        private final String filename;

        public VerifyConsumer(String filename) {
            this.filename = filename;
        }

        public void verify(ZipInputStream zis, byte[] buf) throws IOException {
            OutputStream os = new FileOutputStream("/tmp/" + filename);
            int n;
            while ((n = zis.read(buf, 0, 1024)) > -1) {
                os.write(buf, 0, n);
            }
            os.flush();
            os.close();

            DevConfig config = TestConfig.custom(Map.of(
                ConfigProperties.FAIL_ON_UNKNOWN_IMPORT_PROPERTIES, "false"));

            ObjectMapper mapper = ObjectMapperFactory.getSyncObjectMapper(config);

            ConsumerDTO c = mapper.readValue(new FileInputStream("/tmp/" + filename), ConsumerDTO.class);

            assertEquals("localhost:8443/apiurl", c.getUrlApi());
            assertEquals("localhost:8443/weburl", c.getUrlWeb());
            assertEquals("8auuid", c.getUuid());
            assertEquals("consumer_name", c.getName());
            assertEquals("access_mode", c.getContentAccessMode());

            ConsumerType type = new ConsumerType(ConsumerTypeEnum.CANDLEPIN);
            assertEquals(type.getLabel(), c.getType().getLabel());
            assertEquals(type.isManifest(), c.getType().isManifest());
        }
    }

    public static class VerifyDistributorVersion implements Verify {
        private final String filename;

        public VerifyDistributorVersion(String filename) {
            this.filename = filename;
        }

        public void verify(ZipInputStream zis, byte[] buf) throws IOException {
            OutputStream os = new FileOutputStream("/tmp/" + filename);
            int n;
            while ((n = zis.read(buf, 0, 1024)) > -1) {
                os.write(buf, 0, n);
            }
            os.flush();
            os.close();

            DevConfig config = TestConfig.custom(Map.of(
                ConfigProperties.FAIL_ON_UNKNOWN_IMPORT_PROPERTIES, "false"));

            ObjectMapper mapper = ObjectMapperFactory.getSyncObjectMapper(config);

            DistributorVersion dv = mapper.readValue(
                new FileInputStream("/tmp/" + filename),
                DistributorVersion.class);
            assertNotNull(dv);
            assertEquals("test-dist-ver", dv.getName());
            assertEquals(3, dv.getCapabilities().size());
        }
    }
}
