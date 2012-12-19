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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.ExporterMetadata;
import org.candlepin.model.ExporterMetadataCurator;
import org.candlepin.model.Owner;
import org.candlepin.pki.PKIUtility;
import org.candlepin.sync.Importer.ImportFile;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;


/**
 * ImporterTest
 */
public class ImporterTest {

    private ObjectMapper mapper;
    private I18n i18n;
    private static final String MOCK_JS_PATH = "/tmp/empty.js";
    private CandlepinCommonTestConfig config;

    @Before
    public void init() throws FileNotFoundException, URISyntaxException {
        mapper = SyncUtils.getObjectMapper(new Config(new HashMap<String, String>()));
        i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        config = new CandlepinCommonTestConfig();
        config.setProperty(ConfigProperties.SYNC_WORK_DIR, "/tmp");

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
        Date now = new Date();
        File file = createFile("/tmp/meta", "0.0.3", now,
            "test_user", "prefix");
        File actual = createFile("/tmp/meta.json", "0.0.3", now,
            "test_user", "prefix");
        ExporterMetadataCurator emc = mock(ExporterMetadataCurator.class);
        ExporterMetadata em = new ExporterMetadata();
        Date daybefore = getDateBeforeDays(1);
        em.setExported(daybefore);
        em.setId("42");
        em.setType(ExporterMetadata.TYPE_SYSTEM);
        when(emc.lookupByType(ExporterMetadata.TYPE_SYSTEM)).thenReturn(em);
        Importer i = new Importer(null, null, null, null, null, null, null,
            null, null, emc, null, null, i18n);
        i.validateMetadata(ExporterMetadata.TYPE_SYSTEM, null, actual,
            new ConflictOverrides());

        Meta fileMeta = mapper.readValue(file, Meta.class);
        Meta actualMeta = mapper.readValue(actual, Meta.class);
        assertEquals(fileMeta.getPrincipalName(), actualMeta.getPrincipalName());
        assertEquals(fileMeta.getCreated().getTime(), actualMeta.getCreated().getTime());
        assertEquals(fileMeta.getWebAppPrefix(), actualMeta.getWebAppPrefix());

        assertTrue(file.delete());
        assertTrue(actual.delete());
        assertTrue(daybefore.compareTo(em.getExported()) < 0);
    }

    @Test
    public void firstRun() throws Exception {
        File f = createFile("/tmp/meta", "0.0.3", new Date(),
            "test_user", "prefix");
        File actualmeta = createFile("/tmp/meta.json", "0.0.3", new Date(),
            "test_user", "prefix");
        ExporterMetadataCurator emc = mock(ExporterMetadataCurator.class);
        when(emc.lookupByType(ExporterMetadata.TYPE_SYSTEM)).thenReturn(null);
        Importer i = new Importer(null, null, null, null, null, null, null,
            null, null, emc, null, null, i18n);
        i.validateMetadata(ExporterMetadata.TYPE_SYSTEM, null, actualmeta,
            new ConflictOverrides());
        assertTrue(f.delete());
        assertTrue(actualmeta.delete());
        verify(emc).create(any(ExporterMetadata.class));
    }

    @Test
    public void oldImport() throws Exception {
        // actualmeta is the mock for the import itself
        File actualmeta = createFile("/tmp/meta.json", "0.0.3", getDateBeforeDays(10),
            "test_user", "prefix");
        ExporterMetadataCurator emc = mock(ExporterMetadataCurator.class);
        // emc is the mock for lastrun (i.e., the most recent import in CP)
        ExporterMetadata em = new ExporterMetadata();
        em.setExported(getDateBeforeDays(3));
        em.setId("42");
        em.setType(ExporterMetadata.TYPE_SYSTEM);
        when(emc.lookupByType(ExporterMetadata.TYPE_SYSTEM)).thenReturn(em);
        Importer i = new Importer(null, null, null, null, null, null, null,
            null, null, emc, null, null, i18n);
        try {
            i.validateMetadata(ExporterMetadata.TYPE_SYSTEM, null, actualmeta,
                new ConflictOverrides());
            fail();
        }
        catch (ImportConflictException e) {
            assertFalse(e.message().getConflicts().isEmpty());
            assertEquals(1, e.message().getConflicts().size());
            assertTrue(e.message().getConflicts().contains(
                Importer.Conflict.MANIFEST_OLD));
        }
    }

    @Test
    public void sameImport() throws Exception {
        // actualmeta is the mock for the import itself
        Date date = getDateBeforeDays(10);
        File actualmeta = createFile("/tmp/meta.json", "0.0.3", date,
            "test_user", "prefix");
        ExporterMetadataCurator emc = mock(ExporterMetadataCurator.class);
        // emc is the mock for lastrun (i.e., the most recent import in CP)
        ExporterMetadata em = new ExporterMetadata();
        em.setExported(date); // exact same date = assumed same manifest
        em.setId("42");
        em.setType(ExporterMetadata.TYPE_SYSTEM);
        when(emc.lookupByType(ExporterMetadata.TYPE_SYSTEM)).thenReturn(em);
        Importer i = new Importer(null, null, null, null, null, null, null,
            null, null, emc, null, null, i18n);
        try {
            i.validateMetadata(ExporterMetadata.TYPE_SYSTEM, null, actualmeta,
                new ConflictOverrides());
            fail();
        }
        catch (ImportConflictException e) {
            assertFalse(e.message().getConflicts().isEmpty());
            assertEquals(1, e.message().getConflicts().size());
            assertTrue(e.message().getConflicts().contains(
                Importer.Conflict.MANIFEST_SAME));
        }
    }

    @Test
    public void mergeConflicts() {
        ImportConflictException e2 = new ImportConflictException("testing",
            Importer.Conflict.DISTRIBUTOR_CONFLICT);
        ImportConflictException e3 = new ImportConflictException("testing2",
            Importer.Conflict.MANIFEST_OLD);
        List<ImportConflictException> exceptions =
            new LinkedList<ImportConflictException>();
        exceptions.add(e2);
        exceptions.add(e3);

        ImportConflictException e1 = new ImportConflictException(exceptions);
        assertEquals("testing\ntesting2", e1.message().getDisplayMessage());
        assertEquals(2, e1.message().getConflicts().size());
        assertTrue(e1.message().getConflicts().contains(
            Importer.Conflict.DISTRIBUTOR_CONFLICT));
        assertTrue(e1.message().getConflicts().contains(Importer.Conflict.MANIFEST_OLD));
    }

    @Test
    public void newerImport() throws Exception {
        // this tests bz #790751
        Date importDate = getDateBeforeDays(10);
        // actualmeta is the mock for the import itself
        File actualmeta = createFile("/tmp/meta.json", "0.0.3", importDate,
            "test_user", "prefix");
        ExporterMetadataCurator emc = mock(ExporterMetadataCurator.class);
        // em is the mock for lastrun (i.e., the most recent import in CP)
        ExporterMetadata em = new ExporterMetadata();
        em.setExported(getDateBeforeDays(30));
        em.setId("42");
        em.setType(ExporterMetadata.TYPE_SYSTEM);
        when(emc.lookupByType(ExporterMetadata.TYPE_SYSTEM)).thenReturn(em);
        Importer i = new Importer(null, null, null, null, null, null, null,
            null, null, emc, null, null, i18n);
        i.validateMetadata(ExporterMetadata.TYPE_SYSTEM, null, actualmeta,
            new ConflictOverrides());
        assertEquals(importDate, em.getExported());
    }

    @Test
    public void newerVersionImport() throws Exception {
        // if we do are importing candlepin 0.0.10 data into candlepin 0.0.3,
        // import the rules.

        String version = "0.0.10";
        File actualmeta = createFile("/tmp/meta.json", version, new Date(),
            "test_user", "prefix");
        File[] jsArray = createMockJsFile(MOCK_JS_PATH);
        ExporterMetadataCurator emc = mock(ExporterMetadataCurator.class);
        RulesImporter ri = mock(RulesImporter.class);
        when(emc.lookupByType(ExporterMetadata.TYPE_SYSTEM)).thenReturn(null);
        Importer i = new Importer(null, null, ri, null, null, null, null,
            null, null, emc, null, null, i18n);
        i.importRules(jsArray, actualmeta);

        //verify that rules were imported
        verify(ri).importObject(any(Reader.class), eq(version));
    }

    @Test
    public void olderVersionImport() throws Exception {
        // if we are importing candlepin 0.0.1 data into
        // candlepin 0.0.3, do not import the rules
        File actualmeta = createFile("/tmp/meta.json", "0.0.1", new Date(),
            "test_user", "prefix");
        ExporterMetadataCurator emc = mock(ExporterMetadataCurator.class);
        RulesImporter ri = mock(RulesImporter.class);

        when(emc.lookupByType(ExporterMetadata.TYPE_SYSTEM)).thenReturn(null);
        Importer i = new Importer(null, null, ri, null, null, null, null,
            null, null, emc, null, null, i18n);
        i.validateMetadata(ExporterMetadata.TYPE_SYSTEM, null, actualmeta,
            new ConflictOverrides());
        //verify that rules were not imported
        verify(ri, never()).importObject(any(Reader.class), any(String.class));
    }

    @Test(expected = ImporterException.class)
    public void nullType() throws ImporterException, IOException {
        File actualmeta = createFile("/tmp/meta.json", "0.0.3", new Date(),
            "test_user", "prefix");
        try {
            Importer i = new Importer(null, null, null, null, null, null, null,
                null, null, null, null, null, i18n);

            // null Type should cause exception
            i.validateMetadata(null, null, actualmeta, new ConflictOverrides());
        }
        finally {
            assertTrue(actualmeta.delete());
        }
    }

    @Test(expected = ImporterException.class)
    public void expectOwner() throws ImporterException, IOException {
        File actualmeta = createFile("/tmp/meta.json", "0.0.3", new Date(),
            "test_user", "prefix");
        ExporterMetadataCurator emc = mock(ExporterMetadataCurator.class);
        when(emc.lookupByTypeAndOwner(ExporterMetadata.TYPE_PER_USER, null))
            .thenReturn(null);

        Importer i = new Importer(null, null, null, null, null, null, null,
            null, null, emc, null, null, i18n);

        // null Type should cause exception
        i.validateMetadata(ExporterMetadata.TYPE_PER_USER, null, actualmeta,
            new ConflictOverrides());
        verify(emc, never()).create(any(ExporterMetadata.class));
    }

    @Test
    public void testImportWithNonZipArchive()
        throws IOException, ImporterException {
        Importer i = new Importer(null, null, null, null, null, null, null,
            null, config, null, null, null, i18n);

        Owner owner = mock(Owner.class);
        ConflictOverrides co = mock(ConflictOverrides.class);
        File archive = new File("/tmp/non_zip_file.zip");
        FileWriter fw = new FileWriter(archive);
        fw.write("Just a flat file");
        fw.close();

        try {
            i.loadExport(owner, archive, co);
        }
        catch (ImportExtractionException e) {
            assertEquals(e.getMessage(), i18n.tr("The archive {0} is " +
                "not a properly compressed file or is empty", "non_zip_file.zip"));
            return;
        }
        assertTrue(false);
    }

    @Test
    public void testImportZipArchiveNoContent()
        throws IOException, ImporterException {
        Importer i = new Importer(null, null, null, null, null, null, null,
            null, config, null, null, null, i18n);

        Owner owner = mock(Owner.class);
        ConflictOverrides co = mock(ConflictOverrides.class);

        File archive = new File("/tmp/file.zip");
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(archive));
        out.putNextEntry(new ZipEntry("This is just a zip file with no content"));
        out.close();

        try {
            i.loadExport(owner, archive, co);
        }
        catch (ImportExtractionException e) {
            assertEquals(e.getMessage(), i18n.tr("The archive does not " +
                "contain the required signature file"));
            return;
        }
        assertTrue(false);
    }

    @Test(expected = ImportConflictException.class)
    public void testImportBadSignature()
        throws IOException, ImporterException {
        PKIUtility pki = mock(PKIUtility.class);
        Importer i = new Importer(null, null, null, null, null, null, null,
            pki, config, null, null, null, i18n);

        Owner owner = mock(Owner.class);
        ConflictOverrides co = mock(ConflictOverrides.class);

        File archive = new File("/tmp/file.zip");
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(archive));
        out.putNextEntry(new ZipEntry("signature"));
        out.write("This is the placeholder for the signature file".getBytes());
        File ceArchive = new File("/tmp/consumer_export.zip");
        FileOutputStream fos = new FileOutputStream(ceArchive);
        fos.write("This is just a flat file".getBytes());
        fos.close();
        addFileToArchive(out, ceArchive);
        out.close();

        i.loadExport(owner, archive, co);
    }

    @Test
    public void testImportBadConsumerZip() throws Exception {
        PKIUtility pki = mock(PKIUtility.class);
        Importer i = new Importer(null, null, null, null, null, null, null,
            pki, config, null, null, null, i18n);

        Owner owner = mock(Owner.class);
        ConflictOverrides co = mock(ConflictOverrides.class);

        // Mock a passed signature check:
        when(pki.verifySHA256WithRSAHashWithUpstreamCACert(any(InputStream.class),
            any(byte [].class))).thenReturn(true);

        File archive = new File("/tmp/file.zip");
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(archive));
        out.putNextEntry(new ZipEntry("signature"));
        out.write("This is the placeholder for the signature file".getBytes());
        File ceArchive = new File("/tmp/consumer_export.zip");
        FileOutputStream fos = new FileOutputStream(ceArchive);
        fos.write("This is just a flat file".getBytes());
        fos.close();
        addFileToArchive(out, ceArchive);
        out.close();

        try {
            i.loadExport(owner, archive, co);
        }
        catch (ImportExtractionException e) {
            System.out.println(e.getMessage());
            assertTrue(e.getMessage().contains(
                "not a properly compressed file or is empty"));
            return;
        }
        fail();
    }

    @Test
    public void testImportZipSigAndEmptyConsumerZip()
        throws Exception {
        PKIUtility pki = mock(PKIUtility.class);
        Importer i = new Importer(null, null, null, null, null, null, null,
            pki, config, null, null, null, i18n);

        Owner owner = mock(Owner.class);
        ConflictOverrides co = mock(ConflictOverrides.class);

        // Mock a passed signature check:
        when(pki.verifySHA256WithRSAHashWithUpstreamCACert(any(InputStream.class),
            any(byte [].class))).thenReturn(true);

        File archive = new File("/tmp/file.zip");
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(archive));
        out.putNextEntry(new ZipEntry("signature"));
        out.write("This is the placeholder for the signature file".getBytes());
        File ceArchive = new File("/tmp/consumer_export.zip");
        ZipOutputStream cezip = new ZipOutputStream(new FileOutputStream(ceArchive));
        cezip.putNextEntry(new ZipEntry("This is just a zip file with no content"));
        cezip.close();
        addFileToArchive(out, ceArchive);
        out.close();

        try {
            i.loadExport(owner, archive, co);
        }
        catch (ImportExtractionException e) {
            assertTrue(e.getMessage().contains("consumer_export archive has no contents"));
            return;
        }
        fail();
    }

    @Test
    public void testImportNoMeta()
        throws IOException, ImporterException {
        Importer i = new Importer(null, null, null, null, null, null, null,
            null, config, null, null, null, i18n);
        Owner owner = mock(Owner.class);
        ConflictOverrides co = mock(ConflictOverrides.class);
        Map<String, File> importFiles = new HashMap<String, File>();
        File ruleDir = mock(File.class);
        File[] rulesFiles = new File[]{mock(File.class)};
        when(ruleDir.listFiles()).thenReturn(rulesFiles);

        importFiles.put(ImportFile.META.fileName(), null);
        importFiles.put(ImportFile.RULES.fileName(), ruleDir);
        importFiles.put(ImportFile.CONSUMER_TYPE.fileName(), mock(File.class));
        importFiles.put(ImportFile.CONSUMER.fileName(), mock(File.class));
        importFiles.put(ImportFile.PRODUCTS.fileName(), mock(File.class));
        importFiles.put(ImportFile.ENTITLEMENTS.fileName(), mock(File.class));

        try {
            i.importObjects(owner, importFiles, co);
        }
        catch (ImporterException e) {
            assertEquals(e.getMessage(), i18n.tr("The archive does not contain the " +
                "required meta.json file"));
            return;
        }
        assertTrue(false);
    }

    @Test
    public void testImportNoRulesDir()
        throws IOException, ImporterException {
        Importer i = new Importer(null, null, null, null, null, null, null,
            null, config, null, null, null, i18n);
        Owner owner = mock(Owner.class);
        ConflictOverrides co = mock(ConflictOverrides.class);
        Map<String, File> importFiles = new HashMap<String, File>();

        importFiles.put(ImportFile.META.fileName(), mock(File.class));
        importFiles.put(ImportFile.RULES.fileName(), null);
        importFiles.put(ImportFile.CONSUMER_TYPE.fileName(), mock(File.class));
        importFiles.put(ImportFile.CONSUMER.fileName(), mock(File.class));
        importFiles.put(ImportFile.PRODUCTS.fileName(), mock(File.class));
        importFiles.put(ImportFile.ENTITLEMENTS.fileName(), mock(File.class));

        try {
            i.importObjects(owner, importFiles, co);
        }
        catch (ImporterException e) {
            assertEquals(e.getMessage(), i18n.tr("The archive does not contain the " +
                "required rules directory"));
            return;
        }
        assertTrue(false);
    }

    @Test
    public void testImportNoRulesFile()
        throws IOException, ImporterException {
        Importer i = new Importer(null, null, null, null, null, null, null,
            null, config, null, null, null, i18n);
        Owner owner = mock(Owner.class);
        ConflictOverrides co = mock(ConflictOverrides.class);
        Map<String, File> importFiles = new HashMap<String, File>();
        File ruleDir = mock(File.class);
        when(ruleDir.listFiles()).thenReturn(new File[0]);

        importFiles.put(ImportFile.META.fileName(), mock(File.class));
        importFiles.put(ImportFile.RULES.fileName(), ruleDir);
        importFiles.put(ImportFile.CONSUMER_TYPE.fileName(), mock(File.class));
        importFiles.put(ImportFile.CONSUMER.fileName(), mock(File.class));
        importFiles.put(ImportFile.PRODUCTS.fileName(), mock(File.class));
        importFiles.put(ImportFile.ENTITLEMENTS.fileName(), mock(File.class));

        try {
            i.importObjects(owner, importFiles, co);
        }
        catch (ImporterException e) {
            assertEquals(e.getMessage(), i18n.tr("The archive does not contain the " +
                "required rules file(s)"));
            return;
        }
        assertTrue(false);
    }

    @Test
    public void testImportNoConsumerTypesDir()
        throws IOException, ImporterException {
        Importer i = new Importer(null, null, null, null, null, null, null,
            null, config, null, null, null, i18n);
        Owner owner = mock(Owner.class);
        ConflictOverrides co = mock(ConflictOverrides.class);
        Map<String, File> importFiles = new HashMap<String, File>();
        File ruleDir = mock(File.class);
        File[] rulesFiles = new File[]{mock(File.class)};
        when(ruleDir.listFiles()).thenReturn(rulesFiles);

        importFiles.put(ImportFile.META.fileName(), mock(File.class));
        importFiles.put(ImportFile.RULES.fileName(), ruleDir);
        importFiles.put(ImportFile.CONSUMER_TYPE.fileName(), null);
        importFiles.put(ImportFile.CONSUMER.fileName(), mock(File.class));
        importFiles.put(ImportFile.PRODUCTS.fileName(), mock(File.class));
        importFiles.put(ImportFile.ENTITLEMENTS.fileName(), mock(File.class));

        try {
            i.importObjects(owner, importFiles, co);
        }
        catch (ImporterException e) {
            assertEquals(e.getMessage(), i18n.tr("The archive does not contain the " +
                "required consumer_types directory"));
            return;
        }
        assertTrue(false);
    }

    @Test
    public void testImportNoConsumer()
        throws IOException, ImporterException {
        Importer i = new Importer(null, null, null, null, null, null, null,
            null, config, null, null, null, i18n);
        Owner owner = mock(Owner.class);
        ConflictOverrides co = mock(ConflictOverrides.class);
        Map<String, File> importFiles = new HashMap<String, File>();
        File ruleDir = mock(File.class);
        File[] rulesFiles = new File[]{mock(File.class)};
        when(ruleDir.listFiles()).thenReturn(rulesFiles);

        importFiles.put(ImportFile.META.fileName(), mock(File.class));
        importFiles.put(ImportFile.RULES.fileName(), ruleDir);
        importFiles.put(ImportFile.CONSUMER_TYPE.fileName(), mock(File.class));
        importFiles.put(ImportFile.CONSUMER.fileName(), null);
        importFiles.put(ImportFile.PRODUCTS.fileName(), mock(File.class));
        importFiles.put(ImportFile.ENTITLEMENTS.fileName(), mock(File.class));

        try {
            i.importObjects(owner, importFiles, co);
        }
        catch (ImporterException e) {
            assertEquals(e.getMessage(), i18n.tr("The archive does not contain the " +
                "required consumer.json file"));
            return;
        }
        assertTrue(false);
    }

    @Test
    public void testImportNoProductDir()
        throws IOException, ImporterException {
        RulesImporter ri = mock(RulesImporter.class);
        Importer i = new Importer(null, null, ri, null, null, null, null,
            null, config, null, null, null, i18n);
        Owner owner = mock(Owner.class);
        ConflictOverrides co = mock(ConflictOverrides.class);
        Map<String, File> importFiles = new HashMap<String, File>();
        File ruleDir = mock(File.class);
        File[] rulesFiles = createMockJsFile(MOCK_JS_PATH);
        when(ruleDir.listFiles()).thenReturn(rulesFiles);
        File actualmeta = createFile("/tmp/meta.json", "0.0.3", new Date(),
            "test_user", "prefix");
        // this is the hook to stop testing. we confirm that the archive component tests
        //  are passed and then jump out instead of trying to fake the actual file
        //  processing.
        when(ri.importObject(any(Reader.class), any(String.class))).thenThrow(
            new RuntimeException("Done with the test"));

        importFiles.put(ImportFile.META.fileName(), actualmeta);
        importFiles.put(ImportFile.RULES.fileName(), ruleDir);
        importFiles.put(ImportFile.CONSUMER_TYPE.fileName(), mock(File.class));
        importFiles.put(ImportFile.CONSUMER.fileName(), mock(File.class));
        importFiles.put(ImportFile.PRODUCTS.fileName(), null);
        importFiles.put(ImportFile.ENTITLEMENTS.fileName(), null);

        try {
            i.importObjects(owner, importFiles, co);
        }
        catch (RuntimeException e) {
            assertEquals(e.getMessage(), "Done with the test");
            return;
        }
        assertTrue(false);

    }

    @Test
    public void testImportProductNoEntitlementDir()
        throws IOException, ImporterException {
        Importer i = new Importer(null, null, null, null, null, null, null,
            null, config, null, null, null, i18n);
        Owner owner = mock(Owner.class);
        ConflictOverrides co = mock(ConflictOverrides.class);
        Map<String, File> importFiles = new HashMap<String, File>();
        File ruleDir = mock(File.class);
        File[] rulesFiles = new File[]{mock(File.class)};
        when(ruleDir.listFiles()).thenReturn(rulesFiles);

        importFiles.put(ImportFile.META.fileName(), mock(File.class));
        importFiles.put(ImportFile.RULES.fileName(), ruleDir);
        importFiles.put(ImportFile.CONSUMER_TYPE.fileName(), mock(File.class));
        importFiles.put(ImportFile.CONSUMER.fileName(), mock(File.class));
        importFiles.put(ImportFile.PRODUCTS.fileName(), mock(File.class));
        importFiles.put(ImportFile.ENTITLEMENTS.fileName(), null);

        try {
            i.importObjects(owner, importFiles, co);
        }
        catch (ImporterException e) {
            assertEquals(e.getMessage(), i18n.tr("The archive does not contain the " +
                "required entitlements directory"));
            return;
        }
        assertTrue(false);
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

    private File createFile(String filename, String version, Date date,
                 String username, String prefix)
        throws JsonGenerationException, JsonMappingException, IOException {

        File f = new File(filename);
        Meta meta = new Meta(version, date, username, prefix);
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

    private void addFileToArchive(ZipOutputStream out, File file)
        throws IOException, FileNotFoundException {
        out.putNextEntry(new ZipEntry(file.getName()));
        FileInputStream in = new FileInputStream(file);

        byte [] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        out.closeEntry();
        in.close();
    }

}
