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
package org.candlepin.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.auth.Principal;
import org.candlepin.common.config.MapConfiguration;
import org.candlepin.common.util.VersionUtil;
import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.config.ConfigProperties;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.StandardTranslator;
import org.candlepin.dto.manifest.v1.ConsumerDTO;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.jackson.ProductCachedSerializationModule;
import org.candlepin.model.CandlepinQuery;
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
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.KeyPair;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCertificate;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.pki.PKIUtility;
import org.candlepin.policy.js.export.ExportRules;
import org.candlepin.service.EntitlementCertServiceAdapter;
import org.candlepin.service.ExportExtensionAdapter;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.test.MockResultIterator;
import org.candlepin.test.TestUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


// TODO: FIXME: Rewrite this test to not be so reliant upon mocks. It's making things incredibly brittle and
// wasting dev time tracking down non-issues when a mock silently fails because the implementation changes.


/**
 * ExporterTest
 */
public class ExporterTest {

    private ConsumerTypeCurator ctc;
    private OwnerCurator oc;
    private MetaExporter me;
    private ConsumerExporter ce;
    private ConsumerTypeExporter cte;
    private RulesCurator rc;
    private RulesExporter re;
    private EntitlementCertExporter ece;
    private EntitlementCertServiceAdapter ecsa;
    private ProductExporter pe;
    private ProductServiceAdapter psa;
    private ProductCertExporter pce;
    private EntitlementCurator ec;
    private DistributorVersionCurator dvc;
    private DistributorVersionExporter dve;
    private CdnCurator cdnc;
    private CdnExporter cdne;
    private EntitlementExporter ee;
    private EnvironmentCurator mockEnvironmentCurator;
    private PKIUtility pki;
    private CandlepinCommonTestConfig config;
    private ExportRules exportRules;
    private PrincipalProvider pprov;
    private ProductCurator pc;
    private SyncUtils su;
    private ExportExtensionAdapter exportExtensionAdapter;
    private ModelTranslator translator;

    @Before
    public void setUp() {
        ctc = mock(ConsumerTypeCurator.class);
        mockEnvironmentCurator = mock(EnvironmentCurator.class);
        oc = mock(OwnerCurator.class);
        me = new MetaExporter();
        translator = new StandardTranslator(ctc, mockEnvironmentCurator, oc);
        ce = new ConsumerExporter(translator);
        cte = new ConsumerTypeExporter(translator);
        rc = mock(RulesCurator.class);
        re = new RulesExporter(rc);
        ece = new EntitlementCertExporter();
        ecsa = mock(EntitlementCertServiceAdapter.class);
        pe = new ProductExporter(translator);
        psa = mock(ProductServiceAdapter.class);
        pce = new ProductCertExporter();
        ec = mock(EntitlementCurator.class);
        ee = new EntitlementExporter(translator);
        pki = mock(PKIUtility.class);
        config = new CandlepinCommonTestConfig();
        exportRules = mock(ExportRules.class);
        pprov = mock(PrincipalProvider.class);
        dvc = mock(DistributorVersionCurator.class);
        dve = new DistributorVersionExporter(translator);
        cdnc = mock(CdnCurator.class);
        cdne = new CdnExporter(translator);
        pc = mock(ProductCurator.class);
        ProductCachedSerializationModule productCachedModule = new ProductCachedSerializationModule(pc);
        su = new SyncUtils(config, productCachedModule);
        exportExtensionAdapter = mock(ExportExtensionAdapter.class);

        when(exportRules.canExport(any(Entitlement.class))).thenReturn(Boolean.TRUE);
    }

    private KeyPair createKeyPair() {
        KeyPair cpKeyPair = null;

        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            java.security.KeyPair newPair = generator.generateKeyPair();
            cpKeyPair = new KeyPair(newPair.getPrivate(), newPair.getPublic());
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        return cpKeyPair;
    }

    @SuppressWarnings("unchecked")
    @Test
    public void exportProducts() throws Exception {
        config.setProperty(ConfigProperties.SYNC_WORK_DIR, "/tmp/");
        Consumer consumer = mock(Consumer.class);
        Entitlement ent = mock(Entitlement.class);
        Pool pool = mock(Pool.class);
        Rules mrules = mock(Rules.class);
        Principal principal = mock(Principal.class);
        IdentityCertificate idcert = new IdentityCertificate();

        Set<Entitlement> entitlements = new HashSet<>();
        entitlements.add(ent);

        Owner owner = TestUtil.createOwner("Example-Corporation");

        Product prod = TestUtil.createProduct("12345", "RHEL Product");
        prod.setMultiplier(1L);
        prod.setCreated(new Date());
        prod.setUpdated(new Date());
        prod.setAttributes(Collections.<String, String>emptyMap());

        Product prod1 = TestUtil.createProduct("MKT-prod", "RHEL Product");
        prod1.setMultiplier(1L);
        prod1.setCreated(new Date());
        prod1.setUpdated(new Date());
        prod1.setAttributes(Collections.<String, String>emptyMap());

        Product subProduct = TestUtil.createProduct("MKT-sub-prod", "Sub Product");
        subProduct.setMultiplier(1L);
        subProduct.setCreated(new Date());
        subProduct.setUpdated(new Date());
        subProduct.setAttributes(Collections.<String, String>emptyMap());

        Product subProvidedProduct = TestUtil.createProduct("332211", "Sub Product");
        subProvidedProduct.setMultiplier(1L);
        subProvidedProduct.setCreated(new Date());
        subProvidedProduct.setUpdated(new Date());
        subProvidedProduct.setAttributes(Collections.<String, String>emptyMap());

        ProductCertificate pcert = new ProductCertificate();
        pcert.setKey("euh0876puhapodifbvj094");
        pcert.setCert("hpj-08ha-w4gpoknpon*)&^%#");
        pcert.setCreated(new Date());
        pcert.setUpdated(new Date());

        Set<Product> ppset = new HashSet<>();
        ppset.add(prod);

        prod1.setProvidedProducts(ppset);

        Set<Product> sppSet = new HashSet<>();
        sppSet.add(subProvidedProduct);

        subProduct.setProvidedProducts(sppSet);

        when(pool.getId()).thenReturn("MockedPoolId");
        when(pool.getProduct()).thenReturn(prod1);
        when(pool.getDerivedProduct()).thenReturn(subProduct);
        when(ent.getPool()).thenReturn(pool);
        when(mrules.getRules()).thenReturn("foobar");
        when(pki.getSHA256WithRSAHash(any(InputStream.class))).thenReturn("signature".getBytes());
        when(rc.getRules()).thenReturn(mrules);
        when(consumer.getEntitlements()).thenReturn(entitlements);
        when(psa.getProductCertificate(any(String.class), any(String.class))).thenReturn(pcert);
        when(pprov.get()).thenReturn(principal);
        when(principal.getUsername()).thenReturn("testUser");
        idcert.setSerial(new CertificateSerial(10L, new Date()));
        idcert.setKey("euh0876puhapodifbvj094");
        idcert.setCert("hpj-08ha-w4gpoknpon*)&^%#");
        idcert.setCreated(new Date());
        idcert.setUpdated(new Date());
        when(consumer.getIdCert()).thenReturn(idcert);

        KeyPair keyPair = createKeyPair();
        when(consumer.getKeyPair()).thenReturn(keyPair);
        when(pki.getPemEncoded(keyPair.getPrivateKey())).thenReturn("privateKey".getBytes());

        CandlepinQuery cqmock = mock(CandlepinQuery.class);
        when(cqmock.iterator()).thenReturn(Arrays.asList(new ConsumerType("system")).iterator());
        when(ctc.listAll()).thenReturn(cqmock);

        CandlepinQuery emptyIteratorMock = mock(CandlepinQuery.class);
        when(emptyIteratorMock.iterate()).thenReturn(new MockResultIterator(Arrays.asList().iterator()));
        when(emptyIteratorMock.iterator()).thenReturn(Arrays.asList().iterator());
        when(cdnc.listAll()).thenReturn(emptyIteratorMock);
        when(ctc.listAll()).thenReturn(emptyIteratorMock);

        when(consumer.getOwnerId()).thenReturn(owner.getId());
        when(oc.findOwnerById(eq(owner.getId()))).thenReturn(owner);

        // FINALLY test this badboy
        Exporter e = new Exporter(ctc, oc, me, ce, cte, re, ece, ecsa, pe, psa,
            pce, ec, ee, pki, config, exportRules, pprov, dvc, dve, cdnc, cdne, pc, su,
            exportExtensionAdapter, translator);

        File export = e.getFullExport(consumer, null, null, null, new HashMap<>());

        // VERIFY
        assertNotNull(export);
        verifyContent(export, "export/products/12345.pem",
            new VerifyProductCert("12345.pem"));
        assertFalse(verifyHasEntry(export, "export/products/MKT-prod.pem"));

        verifyContent(export, "export/products/332211.pem",
            new VerifyProductCert("332211.pem"));
        assertFalse(verifyHasEntry(export, "export/products/MKT-sub-prod.pem"));

        FileUtils.deleteDirectory(export.getParentFile());
        assertTrue(new File("/tmp/consumer_export.zip").delete());
        assertTrue(new File("/tmp/12345.pem").delete());
        assertTrue(new File("/tmp/332211.pem").delete());
    }

    @Test(expected = ExportCreationException.class)
    public void doNotExportDirtyEntitlements() throws Exception {
        config.setProperty(ConfigProperties.SYNC_WORK_DIR, "/tmp/");
        Consumer consumer = mock(Consumer.class);
        Entitlement ent = mock(Entitlement.class);
        Principal principal = mock(Principal.class);
        IdentityCertificate idcert = new IdentityCertificate();

        List<Entitlement> entitlements = new ArrayList<>();
        entitlements.add(ent);

        when(pki.getSHA256WithRSAHash(any(InputStream.class))).thenReturn(
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

        KeyPair keyPair = createKeyPair();
        when(consumer.getKeyPair()).thenReturn(keyPair);
        when(pki.getPemEncoded(keyPair.getPrivateKey())).thenReturn("privateKey".getBytes());

        CandlepinQuery cqmock = mock(CandlepinQuery.class);
        when(cqmock.iterator()).thenReturn(Arrays.asList(new ConsumerType("system")).iterator());
        when(ctc.listAll()).thenReturn(cqmock);

        Exporter e = new Exporter(ctc, oc, me, ce, cte, re, ece, ecsa, pe, psa,
            pce, ec, ee, pki, config, exportRules, pprov, dvc, dve, cdnc, cdne, pc, su,
            exportExtensionAdapter, translator);

        e.getFullExport(consumer, null, null, null, new HashMap<>());
    }

    @Test
    public void exportMetadata() throws ExportCreationException, IOException {
        config.setProperty(ConfigProperties.SYNC_WORK_DIR, "/tmp/");
        Date start = new Date();
        Rules mrules = mock(Rules.class);
        Consumer consumer = mock(Consumer.class);
        Principal principal = mock(Principal.class);
        IdentityCertificate idcert = new IdentityCertificate();

        when(mrules.getRules()).thenReturn("foobar");
        when(pki.getSHA256WithRSAHash(any(InputStream.class))).thenReturn("signature".getBytes());
        when(rc.getRules()).thenReturn(mrules);
        when(pprov.get()).thenReturn(principal);
        when(principal.getUsername()).thenReturn("testUser");

        idcert.setSerial(new CertificateSerial(10L, new Date()));
        idcert.setKey("euh0876puhapodifbvj094");
        idcert.setCert("hpj-08ha-w4gpoknpon*)&^%#");
        idcert.setCreated(new Date());
        idcert.setUpdated(new Date());
        when(consumer.getIdCert()).thenReturn(idcert);

        KeyPair keyPair = createKeyPair();
        when(consumer.getKeyPair()).thenReturn(keyPair);
        when(pki.getPemEncoded(keyPair.getPrivateKey())).thenReturn("privateKey".getBytes());

        CandlepinQuery cqmock = mock(CandlepinQuery.class);
        when(cqmock.iterator()).thenReturn(Arrays.asList(new ConsumerType("system")).iterator());
        when(ctc.listAll()).thenReturn(cqmock);

        CandlepinQuery emptyIteratorMock = mock(CandlepinQuery.class);
        when(emptyIteratorMock.iterate()).thenReturn(new MockResultIterator(Arrays.asList().iterator()));
        when(cdnc.listAll()).thenReturn(emptyIteratorMock);

        // FINALLY test this badboy
        Exporter e = new Exporter(ctc, oc, me, ce, cte, re, ece, ecsa, pe, psa,
            pce, ec, ee, pki, config, exportRules, pprov, dvc, dve, cdnc, cdne, pc, su,
            exportExtensionAdapter, translator);
        File export = e.getFullExport(consumer, null, null, null, new HashMap<>());

        // VERIFY
        assertNotNull(export);
        assertTrue(export.exists());
        verifyContent(export, "export/meta.json", new VerifyMetadata(start));

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
        when(pki.getSHA256WithRSAHash(any(InputStream.class))).thenReturn("signature".getBytes());
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

        KeyPair keyPair = createKeyPair();
        when(consumer.getKeyPair()).thenReturn(keyPair);
        when(pki.getPemEncoded(keyPair.getPrivateKey())).thenReturn("privateKey".getBytes());

        CandlepinQuery cqmock = mock(CandlepinQuery.class);
        when(cqmock.iterator()).thenReturn(Arrays.asList(new ConsumerType("system")).iterator());
        when(ctc.listAll()).thenReturn(cqmock);

        CandlepinQuery emptyIteratorMock = mock(CandlepinQuery.class);
        when(emptyIteratorMock.iterate()).thenReturn(new MockResultIterator(Arrays.asList().iterator()));
        when(emptyIteratorMock.iterator()).thenReturn(Arrays.asList().iterator());
        when(cdnc.listAll()).thenReturn(emptyIteratorMock);

        // FINALLY test this badboy
        Exporter e = new Exporter(ctc, oc, me, ce, cte, re, ece, ecsa, pe, psa,
            pce, ec, ee, pki, config, exportRules, pprov, dvc, dve, cdnc, cdne, pc, su,
            exportExtensionAdapter, translator);
        File export = e.getFullExport(consumer, null, null, null, new HashMap<>());

        // VERIFY
        assertNotNull(export);
        assertTrue(export.exists());
        verifyContent(export, "export/upstream_consumer/10.pem", new VerifyIdentityCert("10.pem"));
    }

    @Test
    public void exportConsumer() throws ExportCreationException, IOException {
        config.setProperty(ConfigProperties.SYNC_WORK_DIR, "/tmp/");
        config.setProperty(ConfigProperties.PREFIX_WEBURL, "localhost:8443/weburl");
        config.setProperty(ConfigProperties.PREFIX_APIURL, "localhost:8443/apiurl");
        Rules mrules = mock(Rules.class);
        Consumer consumer = mock(Consumer.class);
        Principal principal = mock(Principal.class);

        when(mrules.getRules()).thenReturn("foobar");
        when(pki.getSHA256WithRSAHash(any(InputStream.class))).thenReturn("signature".getBytes());
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

        KeyPair keyPair = createKeyPair();
        when(consumer.getKeyPair()).thenReturn(keyPair);
        when(pki.getPemEncoded(keyPair.getPrivateKey())).thenReturn("privateKey".getBytes());
        when(consumer.getUuid()).thenReturn("8auuid");
        when(consumer.getName()).thenReturn("consumer_name");
        when(consumer.getContentAccessMode()).thenReturn("access_mode");
        when(consumer.getTypeId()).thenReturn(ctype.getId());

        when(ctc.getConsumerType(eq(consumer))).thenReturn(ctype);
        when(ctc.get(eq(ctype.getId()))).thenReturn(ctype);

        CandlepinQuery cqmock = mock(CandlepinQuery.class);
        when(cqmock.iterator()).thenReturn(Arrays.asList(new ConsumerType("system")).iterator());
        when(ctc.listAll()).thenReturn(cqmock);

        CandlepinQuery emptyIteratorMock = mock(CandlepinQuery.class);
        when(emptyIteratorMock.iterate()).thenReturn(new MockResultIterator(Arrays.asList().iterator()));
        when(cdnc.listAll()).thenReturn(emptyIteratorMock);

        // FINALLY test this badboy
        Exporter e = new Exporter(ctc, oc, me, ce, cte, re, ece, ecsa, pe, psa,
            pce, ec, ee, pki, config, exportRules, pprov, dvc, dve, cdnc, cdne, pc, su,
            exportExtensionAdapter, translator);
        File export = e.getFullExport(consumer, null, null, null, new HashMap<>());

        verifyContent(export, "export/consumer.json", new VerifyConsumer("consumer.json"));
    }

    @Test
    public void exportDistributorVersions() throws ExportCreationException, IOException {
        config.setProperty(ConfigProperties.SYNC_WORK_DIR, "/tmp/");
        config.setProperty(ConfigProperties.PREFIX_WEBURL, "localhost:8443/weburl");
        config.setProperty(ConfigProperties.PREFIX_APIURL, "localhost:8443/apiurl");
        Rules mrules = mock(Rules.class);
        Consumer consumer = mock(Consumer.class);
        Principal principal = mock(Principal.class);

        when(mrules.getRules()).thenReturn("foobar");
        when(pki.getSHA256WithRSAHash(any(InputStream.class))).thenReturn("signature".getBytes());
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

        KeyPair keyPair = createKeyPair();
        when(consumer.getKeyPair()).thenReturn(keyPair);
        when(pki.getPemEncoded(keyPair.getPrivateKey())).thenReturn("privateKey".getBytes());
        when(consumer.getUuid()).thenReturn("8auuid");
        when(consumer.getName()).thenReturn("consumer_name");
        when(consumer.getTypeId()).thenReturn(ctype.getId());
        when(ctc.getConsumerType(eq(consumer))).thenReturn(ctype);
        when(ctc.get(eq(ctype.getId()))).thenReturn(ctype);

        DistributorVersion dv = new DistributorVersion("test-dist-ver");
        Set<DistributorVersionCapability> dvcSet = new HashSet<>();
        dvcSet.add(new DistributorVersionCapability(dv, "capability-1"));
        dvcSet.add(new DistributorVersionCapability(dv, "capability-2"));
        dvcSet.add(new DistributorVersionCapability(dv, "capability-3"));
        dv.setCapabilities(dvcSet);
        List<DistributorVersion> dvList = new ArrayList<>();
        dvList.add(dv);
        when(dvc.findAll()).thenReturn(dvList);

        CandlepinQuery cqmock = mock(CandlepinQuery.class);
        when(cqmock.iterator()).thenReturn(Arrays.asList(new ConsumerType("system")).iterator());
        when(ctc.listAll()).thenReturn(cqmock);

        CandlepinQuery emptyIteratorMock = mock(CandlepinQuery.class);
        when(emptyIteratorMock.iterate()).thenReturn(new MockResultIterator(Arrays.asList().iterator()));
        when(emptyIteratorMock.iterator()).thenReturn(Arrays.asList().iterator());
        when(cdnc.listAll()).thenReturn(emptyIteratorMock);
        when(ctc.listAll()).thenReturn(emptyIteratorMock);

        // FINALLY test this badboy
        Exporter e = new Exporter(ctc, oc, me, ce, cte, re, ece, ecsa, pe, psa,
            pce, ec, ee, pki, config, exportRules, pprov, dvc, dve, cdnc, cdne, pc, su,
            exportExtensionAdapter, translator);
        File export = e.getFullExport(consumer, null, null, null, new HashMap<>());

        verifyContent(export, "export/distributor_version/test-dist-ver.json",
            new VerifyDistributorVersion("test-dist-ver.json"));
    }

    @Test
    public void verifyExportExtension() throws Exception {
        CandlepinQuery emptyIteratorMock = mock(CandlepinQuery.class);
        when(emptyIteratorMock.iterate()).thenReturn(new MockResultIterator(Arrays.asList().iterator()));
        when(emptyIteratorMock.iterator()).thenReturn(Arrays.asList().iterator());
        when(cdnc.listAll()).thenReturn(emptyIteratorMock);
        when(ctc.listAll()).thenReturn(emptyIteratorMock);

        Map<String, String> extensionData = new HashMap<>();
        Exporter e = new Exporter(ctc, oc, me, ce, cte, re, ece, ecsa, pe, psa,
            pce, ec, ee, pki, config, exportRules, pprov, dvc, dve, cdnc, cdne, pc, su,
            exportExtensionAdapter, translator);

        Principal principal = mock(Principal.class);
        when(pprov.get()).thenReturn(principal);
        when(principal.getUsername()).thenReturn("testUser");

        Consumer consumer = mock(Consumer.class);

        Rules mrules = mock(Rules.class);
        when(mrules.getRules()).thenReturn("foobar");
        when(pki.getSHA256WithRSAHash(any(InputStream.class))).thenReturn("signature".getBytes());
        when(rc.getRules()).thenReturn(mrules);

        // specific to this test
        IdentityCertificate idcert = new IdentityCertificate();
        idcert.setSerial(new CertificateSerial(10L, new Date()));
        idcert.setKey("euh0876puhapodifbvj094");
        idcert.setCert("hpj-08ha-w4gpoknpon*)&^%#");
        idcert.setCreated(new Date());
        idcert.setUpdated(new Date());
        when(consumer.getIdCert()).thenReturn(idcert);

        e.getFullExport(consumer, "cdn-key", "webapp-prefix", "api-url", extensionData);
        // Default implementation of the ExportExtensionAdapter does nothing so
        // we only verify that the extension method was invoked.
        verify(exportExtensionAdapter).extendManifest(any(File.class), eq(consumer), eq(extensionData));
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
                    verifyHasEntry(exportdata, name);
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
        catch (FileNotFoundException e) {
            e.printStackTrace();
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
        private Date start;

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
            ObjectMapper om = TestSyncUtils.getTestSyncUtils(new MapConfiguration(
                new HashMap<String, String>() {
                    {
                        put(ConfigProperties.FAIL_ON_UNKNOWN_IMPORT_PROPERTIES, "false");
                    }
                }
            ));
            Meta m = om.readValue(new FileInputStream("/tmp/meta.json"), Meta.class);

            Map<String, String> vmap = VersionUtil.getVersionMap();

            assertNotNull(m);
            assertEquals(vmap.get("version") + '-' + vmap.get("release"), m.getVersion());
            assertTrue(start.before(m.getCreated()));
        }
    }

    public static class VerifyProductCert implements Verify {
        private String filename;
        public VerifyProductCert(String filename) {
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
            assertEquals("hpj-08ha-w4gpoknpon*)&^%#", br.readLine());
            br.close();
        }
    }

    public static class VerifyIdentityCert implements Verify {
        private String filename;
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

    public static class VerifyKeyPair implements Verify {
        private String filename;
        public VerifyKeyPair(String filename) {
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
            assertEquals("privateKeypublicKey", br.readLine());
            br.close();
        }
    }

    public static class VerifyConsumer implements Verify {
        private String filename;
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

            ObjectMapper om = TestSyncUtils.getTestSyncUtils(new MapConfiguration(
                new HashMap<String, String>() {
                    {
                        put(ConfigProperties.FAIL_ON_UNKNOWN_IMPORT_PROPERTIES, "false");
                    }
                }
            ));

            ConsumerDTO c = om.readValue(
                new FileInputStream("/tmp/" + filename), ConsumerDTO.class);

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
        private String filename;

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
            ObjectMapper om = TestSyncUtils.getTestSyncUtils(new MapConfiguration(
                new HashMap<String, String>() {
                    {
                        put(ConfigProperties.FAIL_ON_UNKNOWN_IMPORT_PROPERTIES, "false");
                    }
                }
            ));
            DistributorVersion dv = om.readValue(
                new FileInputStream("/tmp/" + filename),
                DistributorVersion.class);
            assertNotNull(dv);
            assertEquals("test-dist-ver", dv.getName());
            assertEquals(3, dv.getCapabilities().size());
        }
    }
}
