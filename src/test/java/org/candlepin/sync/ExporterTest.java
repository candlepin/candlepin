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
package org.candlepin.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.auth.Principal;
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
import org.candlepin.pki.certs.SCACertificateGenerator;
import org.candlepin.pki.impl.Signer;
import org.candlepin.policy.js.export.ExportRules;
import org.candlepin.service.EntitlementCertServiceAdapter;
import org.candlepin.test.TestUtil;
import org.candlepin.util.ObjectMapperFactory;
import org.candlepin.version.VersionUtil;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


// TODO: FIXME: Rewrite this test to not be so reliant upon mocks. It's making things incredibly brittle and
// wasting dev time tracking down non-issues when a mock silently fails because the implementation changes.



/**
 * ExporterTest
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ExporterTest {
    @Mock
    private ConsumerTypeCurator ctc;
    @Mock
    private OwnerCurator oc;
    @Mock
    private RulesCurator rc;
    @Mock
    private EntitlementCertServiceAdapter ecsa;
    @Mock
    private EntitlementCurator ec;
    @Mock
    private DistributorVersionCurator dvc;
    @Mock
    private CdnCurator cdnc;
    @Mock
    private EnvironmentCurator mockEnvironmentCurator;
    @Mock
    private Signer signer;
    @Mock
    private ExportRules exportRules;
    @Mock
    private PrincipalProvider pprov;
    @Mock
    private SCACertificateGenerator scaCertificateGenerator;
    private ModelTranslator translator;
    private SyncUtils su;
    private DevConfig config;
    private MetaExporter me;
    private ConsumerExporter ce;
    private ConsumerTypeExporter cte;
    private RulesExporter re;
    private ProductExporter pe;
    private CdnExporter cdne;
    private DistributorVersionExporter dve;
    private EntitlementExporter ee;

    @BeforeEach
    public void setUp() {
        me = new MetaExporter();
        translator = new StandardTranslator(ctc, mockEnvironmentCurator, oc);
        ce = new ConsumerExporter(translator);
        cte = new ConsumerTypeExporter(translator);
        re = new RulesExporter(rc);
        pe = new ProductExporter(translator);
        ee = new EntitlementExporter(translator);
        config = TestConfig.defaults();
        dve = new DistributorVersionExporter(translator);
        cdne = new CdnExporter(translator);
        su = new SyncUtils(config);
        when(exportRules.canExport(any(Entitlement.class))).thenReturn(Boolean.TRUE);
    }

    private KeyPairData buildConsumerKeyPairData() {
        Random rnd = new Random();
        final int keySize = 4096;

        byte[] pubKeyBytes = new byte[keySize];
        byte[] privKeyBytes = new byte[keySize];

        rnd.nextBytes(pubKeyBytes);
        rnd.nextBytes(privKeyBytes);

        // This isn't even remotely close to a valid pair of keys, but we don't need valid keys
        // for the purposes of these tests; everything is mocked out.
        return new KeyPairData()
            .setPublicKeyData(pubKeyBytes)
            .setPrivateKeyData(privKeyBytes);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void exportProducts() throws Exception {
        config.setProperty(ConfigProperties.SYNC_WORK_DIR, "/tmp/");
        Consumer consumer = mock(Consumer.class);
        Entitlement ent = mock(Entitlement.class);
        Rules mrules = mock(Rules.class);
        Principal principal = mock(Principal.class);
        IdentityCertificate idcert = new IdentityCertificate();

        Set<Entitlement> entitlements = new HashSet<>();
        entitlements.add(ent);

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
        when(this.signer.sign(any(InputStream.class))).thenReturn("signature".getBytes());
        when(rc.getRules()).thenReturn(mrules);
        when(consumer.getEntitlements()).thenReturn(entitlements);
        when(pprov.get()).thenReturn(principal);
        when(principal.getUsername()).thenReturn("testUser");
        idcert.setSerial(new CertificateSerial(10L, new Date()));
        idcert.setKey("euh0876puhapodifbvj094");
        idcert.setCert("hpj-08ha-w4gpoknpon*)&^%#");
        idcert.setCreated(new Date());
        idcert.setUpdated(new Date());
        when(consumer.getIdCert()).thenReturn(idcert);

        KeyPairData keyPairData = this.buildConsumerKeyPairData();
        when(consumer.getKeyPairData()).thenReturn(keyPairData);

        when(ctc.listAll()).thenReturn(List.of(new ConsumerType("system")));

        when(cdnc.listAll()).thenReturn(new LinkedList<>());
        when(ctc.listAll()).thenReturn(new LinkedList<>());

        when(consumer.getOwnerId()).thenReturn(owner.getId());
        when(oc.findOwnerById(owner.getId())).thenReturn(owner);
        ObjectMapper mapper = ObjectMapperFactory.getSyncObjectMapper(config);

        // FINALLY test this badboy
        Exporter e = new Exporter(ctc, me, ce, cte, re, ecsa, pe,
            ec, ee, signer, config, exportRules, pprov, dvc, dve, cdnc, cdne, su, mapper,
            translator, scaCertificateGenerator);

        File export = e.getFullExport(consumer, null, null, null);

        // VERIFY
        assertNotNull(export);
        verifyContent(export, "export/products/12345.json", new VerifyProduct("12345.json", prod12345));
        verifyContent(export, "export/products/MKT-prod.json", new VerifyProduct("MKT-prod.json", mktProd));

        verifyContent(export, "export/products/332211.json",
            new VerifyProduct("332211.json", subProvidedProduct332211));
        verifyContent(export, "export/products/MKT-sub-prod.json",
            new VerifyProduct("MKT-sub-prod.json", mktSubProd));

        FileUtils.deleteDirectory(export.getParentFile());
        assertTrue(new File("/tmp/consumer_export.zip").delete());
        assertTrue(new File("/tmp/12345.json").delete());
        assertTrue(new File("/tmp/332211.json").delete());
    }

    @Test
    public void doNotExportDirtyEntitlements() {
        config.setProperty(ConfigProperties.SYNC_WORK_DIR, "/tmp/");
        Consumer consumer = mock(Consumer.class);
        Entitlement ent = mock(Entitlement.class);
        Principal principal = mock(Principal.class);
        IdentityCertificate idcert = new IdentityCertificate();

        List<Entitlement> entitlements = new ArrayList<>();
        entitlements.add(ent);

        when(this.signer.sign(any(InputStream.class))).thenReturn(
            "signature".getBytes());
        when(pprov.get()).thenReturn(principal);
        when(principal.getUsername()).thenReturn("testUser");

        when(ec.listByConsumer(consumer)).thenReturn(entitlements);
        when(ent.isDirty()).thenReturn(true);
        idcert.setSerial(new CertificateSerial(10L, new Date()));
        idcert.setKey("euh0876puhapodifbvj094");
        idcert.setCert("hpj-08ha-w4gpoknpon*)&^%#");
        idcert.setCreated(new Date());
        idcert.setUpdated(new Date());
        when(consumer.getIdCert()).thenReturn(idcert);

        KeyPairData keyPairData = this.buildConsumerKeyPairData();
        when(consumer.getKeyPairData()).thenReturn(keyPairData);

        when(ctc.listAll()).thenReturn(List.of(new ConsumerType("system")));
        ObjectMapper mapper = ObjectMapperFactory.getSyncObjectMapper(config);

        Exporter e = new Exporter(ctc, me, ce, cte, re, ecsa, pe,
            ec, ee, signer, config, exportRules, pprov, dvc, dve, cdnc, cdne, su, mapper,
            translator, scaCertificateGenerator);

        assertThrows(ExportCreationException.class, () -> e.getFullExport(consumer, null, null, null));
    }

    @Test
    public void exportMetadata() throws ExportCreationException, IOException {
        config.setProperty(ConfigProperties.SYNC_WORK_DIR, "/tmp/");
        Instant start = Instant.now().minusSeconds(1L);
        Rules mrules = mock(Rules.class);
        Consumer consumer = mock(Consumer.class);
        Principal principal = mock(Principal.class);
        IdentityCertificate idcert = new IdentityCertificate();

        when(mrules.getRules()).thenReturn("foobar");
        when(this.signer.sign(any(InputStream.class))).thenReturn("signature".getBytes());
        when(rc.getRules()).thenReturn(mrules);
        when(pprov.get()).thenReturn(principal);
        when(principal.getUsername()).thenReturn("testUser");

        idcert.setSerial(new CertificateSerial(10L, new Date()));
        idcert.setKey("euh0876puhapodifbvj094");
        idcert.setCert("hpj-08ha-w4gpoknpon*)&^%#");
        idcert.setCreated(new Date());
        idcert.setUpdated(new Date());
        when(consumer.getIdCert()).thenReturn(idcert);

        KeyPairData keyPairData = this.buildConsumerKeyPairData();
        when(consumer.getKeyPairData()).thenReturn(keyPairData);

        when(ctc.listAll()).thenReturn(List.of(new ConsumerType("system")));

        when(cdnc.listAll()).thenReturn(new LinkedList<>());
        ObjectMapper mapper = ObjectMapperFactory.getSyncObjectMapper(config);

        // FINALLY test this badboy
        Exporter e = new Exporter(ctc, me, ce, cte, re, ecsa, pe,
            ec, ee, signer, config, exportRules, pprov, dvc, dve, cdnc, cdne, su, mapper,
            translator, scaCertificateGenerator);
        File export = e.getFullExport(consumer, null, null, null);

        // VERIFY
        assertNotNull(export);
        assertTrue(export.exists());
        verifyContent(export, "export/meta.json", new VerifyMetadata(new Date(start.toEpochMilli())));

        // cleanup the mess
        FileUtils.deleteDirectory(export.getParentFile());
        assertTrue(new File("/tmp/consumer_export.zip").delete());
        assertTrue(new File("/tmp/meta.json").delete());
    }

    @Test
    public void exportIdentityCertificate() throws Exception {
        config.setProperty(ConfigProperties.SYNC_WORK_DIR, "/tmp/");
        Rules mrules = mock(Rules.class);
        Consumer consumer = mock(Consumer.class);
        Principal principal = mock(Principal.class);

        when(mrules.getRules()).thenReturn("foobar");
        when(this.signer.sign(any(InputStream.class))).thenReturn("signature".getBytes());
        when(rc.getRules()).thenReturn(mrules);
        when(pprov.get()).thenReturn(principal);
        when(principal.getUsername()).thenReturn("testUser");

        // specific to this test
        IdentityCertificate idcert = new IdentityCertificate();
        idcert.setSerial(new CertificateSerial(10L, new Date()));
        idcert.setKey("euh0876puhapodifbvj094");
        idcert.setCert("hpj-08ha-w4gpoknpon*)&^%#");
        idcert.setCreated(new Date());
        idcert.setUpdated(new Date());
        when(consumer.getIdCert()).thenReturn(idcert);

        KeyPairData keyPairData = this.buildConsumerKeyPairData();
        when(consumer.getKeyPairData()).thenReturn(keyPairData);

        when(ctc.listAll()).thenReturn(List.of(new ConsumerType("system")));

        when(cdnc.listAll()).thenReturn(new LinkedList<>());
        ObjectMapper mapper = ObjectMapperFactory.getSyncObjectMapper(config);

        // FINALLY test this badboy
        Exporter e = new Exporter(ctc, me, ce, cte, re, ecsa, pe,
            ec, ee, signer, config, exportRules, pprov, dvc, dve, cdnc, cdne, su, mapper,
            translator, scaCertificateGenerator);
        File export = e.getFullExport(consumer, null, null, null);

        // VERIFY
        assertNotNull(export);
        assertTrue(export.exists());
        verifyContent(export, "export/upstream_consumer/10.pem", new VerifyIdentityCert("10.pem"));
    }

    @Test
    public void exportConsumer() throws ExportCreationException {
        config.setProperty(ConfigProperties.SYNC_WORK_DIR, "/tmp/");
        config.setProperty(ConfigProperties.PREFIX_WEBURL, "localhost:8443/weburl");
        config.setProperty(ConfigProperties.PREFIX_APIURL, "localhost:8443/apiurl");
        Rules mrules = mock(Rules.class);
        Consumer consumer = mock(Consumer.class);
        Principal principal = mock(Principal.class);

        when(mrules.getRules()).thenReturn("foobar");
        when(this.signer.sign(any(InputStream.class))).thenReturn("signature".getBytes());
        when(rc.getRules()).thenReturn(mrules);
        when(pprov.get()).thenReturn(principal);
        when(principal.getUsername()).thenReturn("testUser");

        // specific to this test
        IdentityCertificate idcert = new IdentityCertificate();
        idcert.setSerial(new CertificateSerial(10L, new Date()));
        idcert.setKey("euh0876puhapodifbvj094");
        idcert.setCert("hpj-08ha-w4gpoknpon*)&^%#");
        idcert.setCreated(new Date());
        idcert.setUpdated(new Date());
        when(consumer.getIdCert()).thenReturn(idcert);

        ConsumerType ctype = new ConsumerType(ConsumerTypeEnum.CANDLEPIN);
        ctype.setId("test-ctype");

        KeyPairData keyPairData = this.buildConsumerKeyPairData();
        when(consumer.getKeyPairData()).thenReturn(keyPairData);
        when(consumer.getUuid()).thenReturn("8auuid");
        when(consumer.getName()).thenReturn("consumer_name");
        when(consumer.getContentAccessMode()).thenReturn("access_mode");
        when(consumer.getTypeId()).thenReturn(ctype.getId());

        when(ctc.getConsumerType(consumer)).thenReturn(ctype);
        when(ctc.get(ctype.getId())).thenReturn(ctype);

        when(ctc.listAll()).thenReturn(List.of(new ConsumerType("system")));

        when(cdnc.listAll()).thenReturn(new LinkedList<>());
        ObjectMapper mapper = ObjectMapperFactory.getSyncObjectMapper(config);

        // FINALLY test this badboy
        Exporter e = new Exporter(ctc, me, ce, cte, re, ecsa, pe,
            ec, ee, signer, config, exportRules, pprov, dvc, dve, cdnc, cdne, su, mapper,
            translator, scaCertificateGenerator);
        File export = e.getFullExport(consumer, null, null, null);

        verifyContent(export, "export/consumer.json", new VerifyConsumer("consumer.json"));
    }

    @Test
    public void exportDistributorVersions() throws ExportCreationException {
        config.setProperty(ConfigProperties.SYNC_WORK_DIR, "/tmp/");
        config.setProperty(ConfigProperties.PREFIX_WEBURL, "localhost:8443/weburl");
        config.setProperty(ConfigProperties.PREFIX_APIURL, "localhost:8443/apiurl");
        Rules mrules = mock(Rules.class);
        Consumer consumer = mock(Consumer.class);
        Principal principal = mock(Principal.class);

        when(mrules.getRules()).thenReturn("foobar");
        when(this.signer.sign(any(InputStream.class))).thenReturn("signature".getBytes());
        when(rc.getRules()).thenReturn(mrules);
        when(pprov.get()).thenReturn(principal);
        when(principal.getUsername()).thenReturn("testUser");

        IdentityCertificate idcert = new IdentityCertificate();
        idcert.setSerial(new CertificateSerial(10L, new Date()));
        idcert.setKey("euh0876puhapodifbvj094");
        idcert.setCert("hpj-08ha-w4gpoknpon*)&^%#");
        idcert.setCreated(new Date());
        idcert.setUpdated(new Date());
        when(consumer.getIdCert()).thenReturn(idcert);

        ConsumerType ctype = new ConsumerType(ConsumerTypeEnum.CANDLEPIN);
        ctype.setId("test-ctype");

        KeyPairData keyPairData = this.buildConsumerKeyPairData();
        when(consumer.getKeyPairData()).thenReturn(keyPairData);
        when(consumer.getUuid()).thenReturn("8auuid");
        when(consumer.getName()).thenReturn("consumer_name");
        when(consumer.getTypeId()).thenReturn(ctype.getId());
        when(ctc.getConsumerType(consumer)).thenReturn(ctype);
        when(ctc.get(ctype.getId())).thenReturn(ctype);

        DistributorVersion dv = new DistributorVersion("test-dist-ver");
        Set<DistributorVersionCapability> dvcSet = new HashSet<>();
        dvcSet.add(new DistributorVersionCapability(dv, "capability-1"));
        dvcSet.add(new DistributorVersionCapability(dv, "capability-2"));
        dvcSet.add(new DistributorVersionCapability(dv, "capability-3"));
        dv.setCapabilities(dvcSet);
        List<DistributorVersion> dvList = new ArrayList<>();
        dvList.add(dv);
        when(dvc.listAll()).thenReturn(dvList);

        when(ctc.listAll()).thenReturn(List.of(new ConsumerType("system")));

        when(cdnc.listAll()).thenReturn(new LinkedList<>());
        when(ctc.listAll()).thenReturn(new LinkedList<>());
        ObjectMapper mapper = ObjectMapperFactory.getSyncObjectMapper(config);

        // FINALLY test this badboy
        Exporter e = new Exporter(ctc, me, ce, cte, re, ecsa, pe,
            ec, ee, signer, config, exportRules, pprov, dvc, dve, cdnc, cdne, su, mapper,
            translator, scaCertificateGenerator);
        File export = e.getFullExport(consumer, null, null, null);

        verifyContent(export, "export/distributor_version/test-dist-ver.json",
            new VerifyDistributorVersion("test-dist-ver.json"));
    }

    @Test
    public void testGetEntitlementExport() throws Exception {
        config.setProperty(ConfigProperties.SYNC_WORK_DIR, "/tmp/");

        // Setup consumer
        Consumer consumer = mock(Consumer.class);
        ConsumerType ctype = new ConsumerType(ConsumerTypeEnum.CANDLEPIN);
        ctype.setId("test-ctype");
        KeyPairData keyPairData = this.buildConsumerKeyPairData();
        when(consumer.getKeyPairData()).thenReturn(keyPairData);
        when(consumer.getUuid()).thenReturn("consumer");
        when(consumer.getName()).thenReturn("consumer_name");
        when(consumer.getTypeId()).thenReturn(ctype.getId());
        when(ctc.getConsumerType(consumer)).thenReturn(ctype);
        when(ctc.get(ctype.getId())).thenReturn(ctype);

        when(this.signer.sign(any(InputStream.class))).thenReturn("signature".getBytes());

        // Setup principal
        Principal principal = mock(Principal.class);
        when(pprov.get()).thenReturn(principal);
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

        when(ecsa.listForConsumer(consumer)).thenReturn(List.of(entCert));
        when(scaCertificateGenerator.generate(consumer)).thenReturn(cac);
        ObjectMapper mapper = ObjectMapperFactory.getSyncObjectMapper(config);

        Exporter e = new Exporter(ctc, me, ce, cte, re, ecsa, pe,
            ec, ee, signer, config, exportRules, pprov, dvc, dve, cdnc, cdne, su, mapper,
            translator, scaCertificateGenerator);
        File export = e.getEntitlementExport(consumer, null);

        // Verify
        assertNotNull(export);
        assertTrue(export.exists());

        // Check consumer export has entitlement cert.
        assertTrue(verifyHasEntry(export, "export/entitlement_certificates/123456.pem"));

        // Check consumer export has content access cert.
        assertTrue(verifyHasEntry(export, "export/content_access_certificates/654321.pem"));
    }

    @Test
    public void testGetEntitlementExportWithUnknownSerialId() throws Exception {
        config.setProperty(ConfigProperties.SYNC_WORK_DIR, "/tmp/");

        // Setup consumer
        Consumer consumer = mock(Consumer.class);
        ConsumerType ctype = new ConsumerType(ConsumerTypeEnum.CANDLEPIN);
        ctype.setId("test-ctype");
        KeyPairData keyPairData = this.buildConsumerKeyPairData();
        doReturn(keyPairData).when(consumer).getKeyPairData();
        doReturn("consumer").when(consumer).getUuid();
        doReturn("consumer_name").when(consumer).getName();
        doReturn(ctype.getId()).when(consumer).getTypeId();
        doReturn(ctype).when(ctc).getConsumerType(consumer);
        doReturn(ctype).when(ctc).get(ctype.getId());

        when(this.signer.sign(any(InputStream.class))).thenReturn("signature".getBytes());

        // Setup principal
        Principal principal = mock(Principal.class);
        doReturn(principal).when(pprov).get();
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

        doReturn(List.of(entCert)).when(ecsa).listForConsumer(consumer);
        doReturn(cac).when(scaCertificateGenerator).generate(consumer);
        ObjectMapper mapper = ObjectMapperFactory.getSyncObjectMapper(config);

        Exporter e = new Exporter(ctc, me, ce, cte, re, ecsa, pe,
            ec, ee, signer, config, exportRules, pprov, dvc, dve, cdnc, cdne, su, mapper,
            translator, scaCertificateGenerator);
        Set<Long> serials = new HashSet<>();
        serials.add(12345678910L);
        File export = e.getEntitlementExport(consumer, serials);

        // Verify
        assertNotNull(export);
        assertTrue(export.exists());

        // Check consumer export does not have entitlement cert.
        assertFalse(verifyHasEntry(export, "export/entitlement_certificates/123456.pem"));

        // Check consumer export does not have content access cert.
        assertFalse(verifyHasEntry(export, "export/content_access_certificates/654321.pem"));
    }

    @Test
    public void testGetEntitlementExportWithValidEntitlementCertSerial() throws Exception {
        config.setProperty(ConfigProperties.SYNC_WORK_DIR, "/tmp/");

        // Setup consumer
        Consumer consumer = mock(Consumer.class);
        ConsumerType ctype = new ConsumerType(ConsumerTypeEnum.CANDLEPIN);
        ctype.setId("test-ctype");
        KeyPairData keyPairData = this.buildConsumerKeyPairData();
        doReturn(keyPairData).when(consumer).getKeyPairData();
        doReturn("consumer").when(consumer).getUuid();
        doReturn("consumer_name").when(consumer).getName();
        doReturn(ctype.getId()).when(consumer).getTypeId();
        doReturn(ctype).when(ctc).getConsumerType(consumer);
        doReturn(ctype).when(ctc).get(ctype.getId());

        when(this.signer.sign(any(InputStream.class))).thenReturn("signature".getBytes());

        // Setup principal
        Principal principal = mock(Principal.class);
        doReturn(principal).when(pprov).get();
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

        doReturn(List.of(entCert)).when(ecsa).listForConsumer(consumer);
        doReturn(cac).when(scaCertificateGenerator).generate(consumer);
        ObjectMapper mapper = ObjectMapperFactory.getSyncObjectMapper(config);

        Exporter e = new Exporter(ctc, me, ce, cte, re, ecsa, pe,
            ec, ee, signer, config, exportRules, pprov, dvc, dve, cdnc, cdne, su, mapper,
            translator, scaCertificateGenerator);
        Set<Long> serials = new HashSet<>();
        serials.add(entSerial.getId());
        File export = e.getEntitlementExport(consumer, serials);

        // Verify
        assertNotNull(export);
        assertTrue(export.exists());

        // Check consumer export has entitlement cert.
        assertTrue(verifyHasEntry(export, "export/entitlement_certificates/123456.pem"));

        // Check consumer export does not have content access cert.
        assertFalse(verifyHasEntry(export, "export/content_access_certificates/654321.pem"));
    }

    @Test
    public void testGetEntitlementExportWithValidContentAccessCertSerial() throws Exception {
        config.setProperty(ConfigProperties.SYNC_WORK_DIR, "/tmp/");

        // Setup consumer
        Consumer consumer = mock(Consumer.class);
        ConsumerType ctype = new ConsumerType(ConsumerTypeEnum.CANDLEPIN);
        ctype.setId("test-ctype");
        KeyPairData keyPairData = this.buildConsumerKeyPairData();
        doReturn(keyPairData).when(consumer).getKeyPairData();
        doReturn("consumer").when(consumer).getUuid();
        doReturn("consumer_name").when(consumer).getName();
        doReturn(ctype.getId()).when(consumer).getTypeId();
        doReturn(ctype).when(ctc).getConsumerType(consumer);
        doReturn(ctype).when(ctc).get(ctype.getId());

        when(this.signer.sign(any(InputStream.class))).thenReturn("signature".getBytes());

        // Setup principal
        Principal principal = mock(Principal.class);
        doReturn(principal).when(pprov).get();
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

        doReturn(List.of(entCert)).when(ecsa).listForConsumer(consumer);
        doReturn(cac).when(scaCertificateGenerator).generate(consumer);
        ObjectMapper mapper = ObjectMapperFactory.getSyncObjectMapper(config);

        Exporter e = new Exporter(ctc, me, ce, cte, re, ecsa, pe,
            ec, ee, signer, config, exportRules, pprov, dvc, dve, cdnc, cdne, su, mapper,
            translator, scaCertificateGenerator);
        Set<Long> serials = new HashSet<>();
        serials.add(cacSerial.getId());
        File export = e.getEntitlementExport(consumer, serials);

        // Verify
        assertNotNull(export);
        assertTrue(export.exists());

        // Check consumer export does not have entitlement cert.
        assertFalse(verifyHasEntry(export, "export/entitlement_certificates/123456.pem"));

        // Check consumer export has content access cert.
        assertTrue(verifyHasEntry(export, "export/content_access_certificates/654321.pem"));
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
