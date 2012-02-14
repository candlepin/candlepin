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

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.exceptions.ConflictException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.ExporterMetadata;
import org.candlepin.model.ExporterMetadataCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.pki.PKIUtility;
import org.candlepin.policy.js.export.JsExportRules;
import org.candlepin.service.EntitlementCertServiceAdapter;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import javax.persistence.PersistenceException;


/**
 * ImporterTest
 */
public class ImporterTest {

    private ObjectMapper mapper;
    private I18n i18n;
    private static final String MOCK_JS_PATH = "/tmp/empty.js";

    @Before
    public void init() throws FileNotFoundException, URISyntaxException {
        mapper = SyncUtils.getObjectMapper(new Config(new HashMap<String, String>()));
        i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);

        PrintStream ps = new PrintStream(new File(this.getClass()
            .getClassLoader().getResource("candlepin_info.properties").toURI()));
        ps.println("version=0.0.3");
        ps.println("release=1");
        ps.close();

    }

    @Test
    public void validateMetaJson() throws Exception {
        /* read file
         *  read in version
         *  read in created date
         * make sure created date is XYZ
         * make sure version is > ABC
         */

        File f = createFile("/tmp/meta", "0.0.3");
        File actualmeta = createFile("/tmp/meta.json", "0.0.3");
        ExporterMetadataCurator emc = mock(ExporterMetadataCurator.class);
        ExporterMetadata em = new ExporterMetadata();
        Date daybefore = getDateBeforeDays(1);
        em.setExported(daybefore);
        em.setId("42");
        em.setType(ExporterMetadata.TYPE_SYSTEM);
        when(emc.lookupByType(ExporterMetadata.TYPE_SYSTEM)).thenReturn(em);
        Importer i = new Importer(null, null, null, null, null, null, null,
            null, null, emc, null, null, i18n);
        i.validateMetadata(ExporterMetadata.TYPE_SYSTEM, null, actualmeta, false);

        assertTrue(f.delete());
        assertTrue(actualmeta.delete());
        assertTrue(daybefore.compareTo(em.getExported()) < 0);
    }

    @Test
    public void firstRun() throws Exception {
        File f = createFile("/tmp/meta", "0.0.3");
        File actualmeta = createFile("/tmp/meta.json", "0.0.3");
        ExporterMetadataCurator emc = mock(ExporterMetadataCurator.class);
        when(emc.lookupByType(ExporterMetadata.TYPE_SYSTEM)).thenReturn(null);
        Importer i = new Importer(null, null, null, null, null, null, null,
            null, null, emc, null, null, i18n);
        i.validateMetadata(ExporterMetadata.TYPE_SYSTEM, null, actualmeta, false);
        assertTrue(f.delete());
        assertTrue(actualmeta.delete());
        verify(emc).create(any(ExporterMetadata.class));
    }

    @Test(expected = ConflictException.class)
    public void oldImport() throws Exception {
        // create actual first
        File actualmeta = createFile("/tmp/meta.json", "0.0.3");
        ExporterMetadataCurator emc = mock(ExporterMetadataCurator.class);
        ExporterMetadata em = new ExporterMetadata();
        em.setCreated(getDateAfterDays(1));
        em.setId("42");
        em.setType(ExporterMetadata.TYPE_SYSTEM);
        when(emc.lookupByType(ExporterMetadata.TYPE_SYSTEM)).thenReturn(em);
        Importer i = new Importer(null, null, null, null, null, null, null,
            null, null, emc, null, null, i18n);
        i.validateMetadata(ExporterMetadata.TYPE_SYSTEM, null, actualmeta, false);
    }

    @Test
    public void newerVersionImport() throws Exception {
        // if we do are importing candlepin 0.0.10 data into candlepin 0.0.3,
        // import the rules.

        File actualmeta = createFile("/tmp/meta.json", "0.0.10");
        File[] jsArray = createMockJsFile(MOCK_JS_PATH);
        ExporterMetadataCurator emc = mock(ExporterMetadataCurator.class);
        RulesImporter ri = mock(RulesImporter.class);
        when(emc.lookupByType(ExporterMetadata.TYPE_SYSTEM)).thenReturn(null);
        Importer i = new Importer(null, null, ri, null, null, null, null,
            null, null, emc, null, null, i18n);
        i.importRules(jsArray, actualmeta);

        //verify that rules were imported
        verify(ri).importObject(any(Reader.class));

    }
    @Test
    public void olderVersionImport() throws Exception {
        // if we are importing candlepin 0.0.1 data into
        // candlepin 0.0.3, do not import the rules
        File actualmeta = createFile("/tmp/meta.json", "0.0.1");
        ExporterMetadataCurator emc = mock(ExporterMetadataCurator.class);
        RulesImporter ri = mock(RulesImporter.class);

        when(emc.lookupByType(ExporterMetadata.TYPE_SYSTEM)).thenReturn(null);
        Importer i = new Importer(null, null, ri, null, null, null, null,
            null, null, emc, null, null, i18n);
        i.validateMetadata(ExporterMetadata.TYPE_SYSTEM, null, actualmeta, false);
        //verify that rules were not imported
        verify(ri, never()).importObject(any(Reader.class));
    }

    @Test(expected = ImporterException.class)
    public void nullType() throws ImporterException, IOException {
        File actualmeta = createFile("/tmp/meta.json", "0.0.3");
        try {
            Importer i = new Importer(null, null, null, null, null, null, null,
                null, null, null, null, null, i18n);

            // null Type should cause exception
            i.validateMetadata(null, null, actualmeta, false);
        }
        finally {
            assertTrue(actualmeta.delete());
        }
    }

    @Test(expected = ImporterException.class)
    public void expectOwner() throws ImporterException, IOException {
        File actualmeta = createFile("/tmp/meta.json", "0.0.3");
        ExporterMetadataCurator emc = mock(ExporterMetadataCurator.class);
        when(emc.lookupByTypeAndOwner(ExporterMetadata.TYPE_PER_USER, null))
            .thenReturn(null);

        Importer i = new Importer(null, null, null, null, null, null, null,
            null, null, emc, null, null, i18n);

        // null Type should cause exception
        i.validateMetadata(ExporterMetadata.TYPE_PER_USER, null, actualmeta, false);
        verify(emc, never()).create(any(ExporterMetadata.class));
    }

    @Test(expected = SyncDataFormatException.class)
    public void constraintViolation() throws Exception {
        Owner o = mock(Owner.class);
        Consumer c = mock(Consumer.class);
        ConsumerTypeCurator ctc = mock(ConsumerTypeCurator.class);

        MetaExporter meta = new MetaExporter();
        ConsumerExporter ce = new ConsumerExporter();
        ConsumerTypeExporter cte = new ConsumerTypeExporter();
        ConsumerType ct = new ConsumerType(ConsumerType.ConsumerTypeEnum.SYSTEM);
        List<ConsumerType> cts = new ArrayList<ConsumerType>();
        cts.add(ct);
        RulesCurator rc = mock(RulesCurator.class);
        Rules r = mock(Rules.class);
        RulesImporter ri = new RulesImporter(rc);
        RulesExporter re = new RulesExporter(rc);
        EntitlementCertExporter ece = new EntitlementCertExporter();
        EntitlementCertServiceAdapter ecsa = mock(EntitlementCertServiceAdapter.class);
        EntitlementCurator ec = mock(EntitlementCurator.class);
        EntitlementExporter ee = new EntitlementExporter();
        PKIUtility pki = mock(PKIUtility.class);
        Config config = mock(Config.class);
        JsExportRules rules = mock(JsExportRules.class);
        ExporterMetadataCurator emc = mock(ExporterMetadataCurator.class);
        OwnerCurator oc = mock(OwnerCurator.class);

        when(c.getUuid()).thenReturn("fake-uuid");
        when(ctc.listAll()).thenReturn(cts);
        when(ctc.lookupByLabel(eq("system"))).thenReturn(ct);
        when(r.getRules()).thenReturn("rules");
        when(rc.getRules()).thenReturn(r);
        when(pki.getSHA256WithRSAHash(any(InputStream.class))).thenReturn("sig".getBytes());
        when(pki.verifySHA256WithRSAHashWithUpstreamCACert(
            any(InputStream.class), eq("sig".getBytes()))).thenReturn(true);
        when(config.getString(eq(ConfigProperties.SYNC_WORK_DIR))).thenReturn("/tmp/");
        when(oc.merge(any(Owner.class))).thenThrow(new PersistenceException(
            "fake exception", new ConstraintViolationException(null, null, null, null)));

        Exporter ex = new Exporter(ctc, meta, ce, cte, re, ece, ecsa, null,
            null, null, ec, ee, pki, config, rules);
        File export = ex.getFullExport(c);

        Importer i = new Importer(ctc, null, ri, oc, null, null, null,
            pki, config, emc, null, null, i18n);

        i.loadExport(o, export, false);
    }

    @After
    public void tearDown() throws Exception {
        PrintStream ps = new PrintStream(new File(this.getClass()
            .getClassLoader().getResource("candlepin_info.properties").toURI()));
        ps.println("version=${version}");
        ps.println("release=${release}");
        ps.close();
        File mockJs = new File(MOCK_JS_PATH);
        mockJs.delete();
    }

    private File createFile(String filename, String version)
        throws JsonGenerationException, JsonMappingException, IOException {

        File f = new File(filename);
        Meta meta = new Meta(version, new Date());
        mapper.writeValue(f, meta);
        return f;
    }

    private File[] createMockJsFile(String filename)
        throws IOException {

        FileWriter f = new FileWriter(filename);
        f.write("// nothing to see here");
        f.close();

        File[] fileArray = new File[1];
        fileArray[0] = new File(filename);
        return fileArray;
    }

    private Date getDateBeforeDays(int days) {
        long daysinmillis = 24 * 60 * 60 * 1000;
        long ms = System.currentTimeMillis() - (days * daysinmillis);
        Date backDate = new Date();
        backDate.setTime(ms);
        return backDate;
    }

    private Date getDateAfterDays(int days) {
        long daysinmillis = 24 * 60 * 60 * 1000;
        long ms = System.currentTimeMillis() + (days * daysinmillis);
        Date backDate = new Date();
        backDate.setTime(ms);
        return backDate;
    }
}
