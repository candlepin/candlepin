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
package org.fedoraproject.candlepin.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.fedoraproject.candlepin.config.CandlepinCommonTestConfig;
import org.fedoraproject.candlepin.config.ConfigProperties;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerTypeCurator;
import org.fedoraproject.candlepin.model.EntitlementCurator;
import org.fedoraproject.candlepin.model.Rules;
import org.fedoraproject.candlepin.model.RulesCurator;
import org.fedoraproject.candlepin.pki.PKIUtility;
import org.fedoraproject.candlepin.service.EntitlementCertServiceAdapter;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;

import org.apache.commons.io.FileUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
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
    }

    @Test
    public void exportMetadata() throws ExportCreationException, IOException {
        Date start = new Date();
        config.setProperty(ConfigProperties.SYNC_WORK_DIR, "/tmp/");
        Rules mrules = mock(Rules.class);
        when(mrules.getRules()).thenReturn("foobar");
        when(pki.getSHA256WithRSAHash(any(InputStream.class))).thenReturn(
            "signature".getBytes());
        when(rc.getRules()).thenReturn(mrules);

        Exporter e = new Exporter(ctc, me, ce, cte, re, ece, ecsa, pe, psa,
            pce, ec, ee, pki, config);
        Consumer consumer = mock(Consumer.class);
        File export = e.getExport(consumer);

        assertNotNull(export);
        assertTrue(export.exists());
        verifyMetadata(export, start);

        // cleanup the mess
        FileUtils.deleteDirectory(export.getParentFile());
        assertTrue(new File("/tmp/consumer_export.zip").delete());
        assertTrue(new File("/tmp/meta.json").delete());
    }

    private void verifyMetadata(File export, Date start) {
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
                    verifyMetadata(exportdata, start);
                }
                else if (entry.getName().endsWith("meta.json")) {
                    OutputStream os = new FileOutputStream("/tmp/meta.json");
                    int n;
                    while ((n = zis.read(buf, 0, 1024)) > -1) {
                        os.write(buf, 0, n);
                    }
                    os.flush();
                    os.close();
                    ObjectMapper om = SyncUtils.getObjectMapper();
                    Meta m = om.readValue(
                        new FileInputStream("/tmp/meta.json"), Meta.class);
                    assertNotNull(m);
                    assertEquals("0.0.0", m.getVersion());
                    assertTrue(start.before(m.getCreated()));
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
}
