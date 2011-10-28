/**
 * Copyright (c) 2009 Red Hat, Inc.
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

import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCertificate;
import org.candlepin.model.ProvidedProduct;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.pki.PKIUtility;
import org.candlepin.policy.js.export.JsExportRules;
import org.candlepin.service.EntitlementCertServiceAdapter;
import org.candlepin.service.ProductServiceAdapter;

import org.apache.commons.io.FileUtils;
import org.codehaus.jackson.map.ObjectMapper;
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
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


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
    private EntitlementExporter ee;
    private PKIUtility pki;
    private CandlepinCommonTestConfig config;
    private JsExportRules exportRules;

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
        exportRules = mock(JsExportRules.class);

        when(exportRules.canExport(any(Entitlement.class))).thenReturn(Boolean.TRUE);
    }

    @Test
    public void exportProducts() throws Exception {
        config.setProperty(ConfigProperties.SYNC_WORK_DIR, "/tmp/");
        Consumer consumer = mock(Consumer.class);
        Entitlement ent = mock(Entitlement.class);
        ProvidedProduct pp = mock(ProvidedProduct.class);
        Pool pool = mock(Pool.class);
        Rules mrules = mock(Rules.class);

        Set<ProvidedProduct> ppset = new HashSet<ProvidedProduct>();
        ppset.add(pp);

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

        ProductCertificate pcert = new ProductCertificate();
        pcert.setKey("euh0876puhapodifbvj094");
        pcert.setCert("hpj-08ha-w4gpoknpon*)&^%#");
        pcert.setCreated(new Date());
        pcert.setUpdated(new Date());

        when(pp.getProductId()).thenReturn("12345");
        when(pool.getProvidedProducts()).thenReturn(ppset);
        when(pool.getProductId()).thenReturn("MKT-prod");
        when(ent.getPool()).thenReturn(pool);
        when(mrules.getRules()).thenReturn("foobar");
        when(pki.getSHA256WithRSAHash(any(InputStream.class))).thenReturn(
            "signature".getBytes());
        when(rc.getRules()).thenReturn(mrules);
        when(consumer.getEntitlements()).thenReturn(entitlements);
        when(psa.getProductById("12345")).thenReturn(prod);
        when(psa.getProductById("MKT-prod")).thenReturn(prod1);
        when(psa.getProductCertificate(any(Product.class))).thenReturn(pcert);

        // FINALLY test this badboy
        Exporter e = new Exporter(ctc, me, ce, cte, re, ece, ecsa, pe, psa,
            pce, ec, ee, pki, config, exportRules);

        File export = e.getFullExport(consumer);

        // VERIFY
        assertNotNull(export);
        verifyContent(export, "export/products/12345.pem",
            new VerifyProductCert("12345.pem"));
        assertFalse(verifyHasEntry(export, "export/products/MKT-prod.pem"));
        FileUtils.deleteDirectory(export.getParentFile());
        assertTrue(new File("/tmp/consumer_export.zip").delete());
        assertTrue(new File("/tmp/12345.pem").delete());
    }

    @Test
    public void exportMetadata() throws ExportCreationException, IOException {
        config.setProperty(ConfigProperties.SYNC_WORK_DIR, "/tmp/");
        Date start = new Date();
        Rules mrules = mock(Rules.class);
        Consumer consumer = mock(Consumer.class);

        when(mrules.getRules()).thenReturn("foobar");
        when(pki.getSHA256WithRSAHash(any(InputStream.class))).thenReturn(
            "signature".getBytes());
        when(rc.getRules()).thenReturn(mrules);

        // FINALLY test this badboy
        Exporter e = new Exporter(ctc, me, ce, cte, re, ece, ecsa, pe, psa,
            pce, ec, ee, pki, config, exportRules);
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

}
