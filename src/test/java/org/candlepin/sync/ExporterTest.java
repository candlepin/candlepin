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
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.FileUtils;
import org.candlepin.auth.Principal;
import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.guice.PrincipalProvider;
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
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.KeyPair;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCertificate;
import org.candlepin.model.ProvidedProduct;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.model.SubProvidedProduct;
import org.candlepin.pki.PKIUtility;
import org.candlepin.policy.js.export.ExportRules;
import org.candlepin.service.EntitlementCertServiceAdapter;
import org.candlepin.service.ProductServiceAdapter;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Before;
import org.junit.Test;


/**
 * ExporterTest
 */
public class ExporterTest {

    private ConsumerTypeCurator ctc;
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
    private EntitlementExporter ee;
    private PKIUtility pki;
    private CandlepinCommonTestConfig config;
    private ExportRules exportRules;
    private PrincipalProvider pprov;

    @Before
    public void setUp() {
        ctc = mock(ConsumerTypeCurator.class);
        me = new MetaExporter();
        ce = new ConsumerExporter();
        cte = new ConsumerTypeExporter();
        rc = mock(RulesCurator.class);
        re = new RulesExporter(rc);
        ece = new EntitlementCertExporter();
        ecsa = mock(EntitlementCertServiceAdapter.class);
        pe = new ProductExporter();
        psa = mock(ProductServiceAdapter.class);
        pce = new ProductCertExporter();
        ec = mock(EntitlementCurator.class);
        ee = new EntitlementExporter();
        pki = mock(PKIUtility.class);
        config = new CandlepinCommonTestConfig();
        exportRules = mock(ExportRules.class);
        pprov = mock(PrincipalProvider.class);
        dvc = mock(DistributorVersionCurator.class);
        dve = new DistributorVersionExporter();

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
        ProvidedProduct pp = mock(ProvidedProduct.class);
        SubProvidedProduct spp = mock(SubProvidedProduct.class);
        Pool pool = mock(Pool.class);
        Rules mrules = mock(Rules.class);
        Principal principal = mock(Principal.class);
        IdentityCertificate idcert = new IdentityCertificate();

        Set<ProvidedProduct> ppset = new HashSet<ProvidedProduct>();
        ppset.add(pp);

        Set<SubProvidedProduct> sppSet = new HashSet<SubProvidedProduct>();
        sppSet.add(spp);

        Set<Entitlement> entitlements = new HashSet<Entitlement>();
        entitlements.add(ent);

        Product prod = new Product("12345", "RHEL Product");
        prod.setMultiplier(1L);
        prod.setCreated(new Date());
        prod.setUpdated(new Date());
        prod.setHref("http://localhost");
        prod.setAttributes(Collections.EMPTY_SET);

        Product prod1 = new Product("MKT-prod", "RHEL Product");
        prod1.setMultiplier(1L);
        prod1.setCreated(new Date());
        prod1.setUpdated(new Date());
        prod1.setHref("http://localhost");
        prod1.setAttributes(Collections.EMPTY_SET);

        Product subProduct = new Product("MKT-sub-prod", "Sub Product");
        subProduct.setMultiplier(1L);
        subProduct.setCreated(new Date());
        subProduct.setUpdated(new Date());
        subProduct.setHref("http://localhost");
        subProduct.setAttributes(Collections.EMPTY_SET);

        Product subProvidedProduct = new Product("332211", "Sub Product");
        subProvidedProduct.setMultiplier(1L);
        subProvidedProduct.setCreated(new Date());
        subProvidedProduct.setUpdated(new Date());
        subProvidedProduct.setHref("http://localhost");
        subProvidedProduct.setAttributes(Collections.EMPTY_SET);

        ProductCertificate pcert = new ProductCertificate();
        pcert.setKey("euh0876puhapodifbvj094");
        pcert.setCert("hpj-08ha-w4gpoknpon*)&^%#");
        pcert.setCreated(new Date());
        pcert.setUpdated(new Date());

        when(pp.getProductId()).thenReturn("12345");
        when(pool.getProvidedProducts()).thenReturn(ppset);
        when(pool.getProductId()).thenReturn("MKT-prod");

        when(pool.getSubProvidedProducts()).thenReturn(sppSet);
        when(pool.getSubProductId()).thenReturn(subProduct.getId());
        when(spp.getProductId()).thenReturn(subProvidedProduct.getId());

        when(ent.getPool()).thenReturn(pool);
        when(mrules.getRules()).thenReturn("foobar");
        when(pki.getSHA256WithRSAHash(any(InputStream.class))).thenReturn(
            "signature".getBytes());
        when(rc.getRules()).thenReturn(mrules);
        when(consumer.getEntitlements()).thenReturn(entitlements);
        when(psa.getProductById("12345")).thenReturn(prod);
        when(psa.getProductById("MKT-prod")).thenReturn(prod1);
        when(psa.getProductById("MKT-sub-prod")).thenReturn(subProduct);
        when(psa.getProductById("332211")).thenReturn(subProvidedProduct);
        when(psa.getProductCertificate(any(Product.class))).thenReturn(pcert);
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
        when(pki.getPemEncoded(keyPair.getPrivateKey()))
            .thenReturn("privateKey".getBytes());
        when(pki.getPemEncoded(keyPair.getPublicKey()))
            .thenReturn("publicKey".getBytes());

        // FINALLY test this badboy
        Exporter e = new Exporter(ctc, me, ce, cte, re, ece, ecsa, pe, psa,
            pce, ec, ee, pki, config, exportRules, pprov, dvc, dve);

        File export = e.getFullExport(consumer);

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

        List<Entitlement> entitlements = new ArrayList<Entitlement>();
        entitlements.add(ent);

        when(pki.getSHA256WithRSAHash(any(InputStream.class))).thenReturn(
            "signature".getBytes());
        when(pprov.get()).thenReturn(principal);
        when(principal.getUsername()).thenReturn("testUser");

        when(ec.listByConsumer(consumer)).thenReturn(entitlements);
        when(ent.getDirty()).thenReturn(true);
        idcert.setSerial(new CertificateSerial(10L, new Date()));
        idcert.setKey("euh0876puhapodifbvj094");
        idcert.setCert("hpj-08ha-w4gpoknpon*)&^%#");
        idcert.setCreated(new Date());
        idcert.setUpdated(new Date());
        when(consumer.getIdCert()).thenReturn(idcert);

        KeyPair keyPair = createKeyPair();
        when(consumer.getKeyPair()).thenReturn(keyPair);
        when(pki.getPemEncoded(keyPair.getPrivateKey()))
            .thenReturn("privateKey".getBytes());
        when(pki.getPemEncoded(keyPair.getPublicKey()))
            .thenReturn("publicKey".getBytes());

        Exporter e = new Exporter(ctc, me, ce, cte, re, ece, ecsa, pe, psa,
            pce, ec, ee, pki, config, exportRules, pprov, dvc, dve);

        e.getFullExport(consumer);
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
        when(pki.getSHA256WithRSAHash(any(InputStream.class))).thenReturn(
            "signature".getBytes());
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
        when(pki.getPemEncoded(keyPair.getPrivateKey()))
            .thenReturn("privateKey".getBytes());
        when(pki.getPemEncoded(keyPair.getPublicKey()))
            .thenReturn("publicKey".getBytes());

        // FINALLY test this badboy
        Exporter e = new Exporter(ctc, me, ce, cte, re, ece, ecsa, pe, psa,
            pce, ec, ee, pki, config, exportRules, pprov, dvc, dve);
        File export = e.getFullExport(consumer);

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
        when(pki.getSHA256WithRSAHash(any(InputStream.class))).thenReturn(
            "signature".getBytes());
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
        when(pki.getPemEncoded(keyPair.getPrivateKey()))
            .thenReturn("privateKey".getBytes());
        when(pki.getPemEncoded(keyPair.getPublicKey()))
            .thenReturn("publicKey".getBytes());

        // FINALLY test this badboy
        Exporter e = new Exporter(ctc, me, ce, cte, re, ece, ecsa, pe, psa,
            pce, ec, ee, pki, config, exportRules, pprov, dvc, dve);
        File export = e.getFullExport(consumer);

        // VERIFY
        assertNotNull(export);
        assertTrue(export.exists());
        verifyContent(export, "export/upstream_consumer/10.pem",
            new VerifyIdentityCert("10.pem"));
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
        when(pki.getSHA256WithRSAHash(any(InputStream.class))).thenReturn(
            "signature".getBytes());
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
        when(pki.getPemEncoded(keyPair.getPrivateKey()))
            .thenReturn("privateKey".getBytes());
        when(pki.getPemEncoded(keyPair.getPublicKey()))
            .thenReturn("publicKey".getBytes());
        when(consumer.getUuid()).thenReturn("8auuid");
        when(consumer.getName()).thenReturn("consumer_name");
        when(consumer.getType()).thenReturn(new ConsumerType(ConsumerTypeEnum.CANDLEPIN));

        // FINALLY test this badboy
        Exporter e = new Exporter(ctc, me, ce, cte, re, ece, ecsa, pe, psa,
            pce, ec, ee, pki, config, exportRules, pprov, dvc, dve);
        File export = e.getFullExport(consumer);

        verifyContent(export, "export/consumer.json",
            new VerifyConsumer("consumer.json"));
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
        when(pki.getSHA256WithRSAHash(any(InputStream.class))).thenReturn(
            "signature".getBytes());
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

        KeyPair keyPair = createKeyPair();
        when(consumer.getKeyPair()).thenReturn(keyPair);
        when(pki.getPemEncoded(keyPair.getPrivateKey()))
            .thenReturn("privateKey".getBytes());
        when(pki.getPemEncoded(keyPair.getPublicKey()))
            .thenReturn("publicKey".getBytes());
        when(consumer.getUuid()).thenReturn("8auuid");
        when(consumer.getName()).thenReturn("consumer_name");
        when(consumer.getType()).thenReturn(new ConsumerType(ConsumerTypeEnum.CANDLEPIN));

        DistributorVersion dv = new DistributorVersion("test-dist-ver");
        Set<DistributorVersionCapability> dvcSet =
            new HashSet<DistributorVersionCapability>();
        dvcSet.add(new DistributorVersionCapability(dv, "capability-1"));
        dvcSet.add(new DistributorVersionCapability(dv, "capability-2"));
        dvcSet.add(new DistributorVersionCapability(dv, "capability-3"));
        dv.setCapabilities(dvcSet);
        List<DistributorVersion> dvList = new ArrayList<DistributorVersion>();
        dvList.add(dv);
        when(dvc.findAll()).thenReturn(dvList);

        // FINALLY test this badboy
        Exporter e = new Exporter(ctc, me, ce, cte, re, ece, ecsa, pe, psa,
            pce, ec, ee, pki, config, exportRules, pprov, dvc, dve);
        File export = e.getFullExport(consumer);

        verifyContent(export, "export/distributor_version/test-dist-ver.json",
            new VerifyDistributorVersion("test-dist-ver.json"));
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
            ObjectMapper om = SyncUtils.getObjectMapper(
                new Config(new HashMap<String, String>()));
            Meta m = om.readValue(
                new FileInputStream("/tmp/meta.json"), Meta.class);
            assertNotNull(m);
            assertEquals("${version}-${release}", m.getVersion());
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

            ObjectMapper om = SyncUtils.getObjectMapper(
                new Config(new HashMap<String, String>()));

            ConsumerDto c = om.readValue(
                new FileInputStream("/tmp/" + filename), ConsumerDto.class);

            assertEquals("localhost:8443/apiurl", c.getUrlApi());
            assertEquals("localhost:8443/weburl", c.getUrlWeb());
            assertEquals("8auuid", c.getUuid());
            assertEquals("consumer_name", c.getName());
            assertEquals(new ConsumerType(ConsumerTypeEnum.CANDLEPIN), c.getType());
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
            ObjectMapper om = SyncUtils.getObjectMapper(
                new Config(new HashMap<String, String>()));
            DistributorVersion dv = om.readValue(
                new FileInputStream("/tmp/" + filename),
                DistributorVersion.class);
            assertNotNull(dv);
            assertEquals("test-dist-ver", dv.getName());
            assertEquals(3, dv.getCapabilities().size());
        }
    }
}
