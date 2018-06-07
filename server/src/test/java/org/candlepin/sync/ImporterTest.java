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

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

import org.candlepin.audit.EventSink;
import org.candlepin.common.config.MapConfiguration;
import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.PoolManager;
import org.candlepin.controller.Refresher;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.StandardTranslator;
import org.candlepin.dto.manifest.v1.ConsumerDTO;
import org.candlepin.dto.manifest.v1.ConsumerTypeDTO;
import org.candlepin.dto.manifest.v1.OwnerDTO;
import org.candlepin.jackson.ProductCachedSerializationModule;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.DistributorVersion;
import org.candlepin.model.DistributorVersionCapability;
import org.candlepin.model.DistributorVersionCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.ExporterMetadata;
import org.candlepin.model.ExporterMetadataCurator;
import org.candlepin.model.IdentityCertificateCurator;
import org.candlepin.model.ImportRecord;
import org.candlepin.model.ImportRecordCurator;
import org.candlepin.model.ImportUpstreamConsumer;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.UpstreamConsumer;
import org.candlepin.model.dto.Subscription;
import org.candlepin.pki.PKIUtility;
import org.candlepin.pki.ProviderBasedPKIUtility;
import org.candlepin.pki.impl.JSSProviderLoader;
import org.candlepin.service.OwnerServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.sync.Importer.ImportFile;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * ImporterTest
 */
public class ImporterTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Rule
    public ExpectedException ee = ExpectedException.none();

    private ObjectMapper mapper;
    private I18n i18n;
    private String mockJsPath;
    private CandlepinCommonTestConfig config;
    private ClassLoader classLoader = getClass().getClassLoader();
    private SyncUtils su;
    private ProductCurator pc;
    private EntitlementCurator ec;
    private EnvironmentCurator environmentCurator;
    private OwnerCurator oc;
    private SubscriptionReconciler mockSubReconciler;
    private ConsumerTypeCurator consumerTypeCurator;
    private ModelTranslator translator;

    static {
        JSSProviderLoader.addProvider();
    }

    @Before
    public void init() throws URISyntaxException, IOException {
        mapper = TestSyncUtils.getTestSyncUtils(new MapConfiguration(
            new HashMap<String, String>() {
                {
                    put(ConfigProperties.FAIL_ON_UNKNOWN_IMPORT_PROPERTIES, "false");
                }
            }
        ));

        i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        config = new CandlepinCommonTestConfig();
        pc = Mockito.mock(ProductCurator.class);
        ec = Mockito.mock(EntitlementCurator.class);
        this.environmentCurator = Mockito.mock(EnvironmentCurator.class);
        ProductCachedSerializationModule productCachedModule = new ProductCachedSerializationModule(pc);
        su = new SyncUtils(config, productCachedModule);
        PrintStream ps = new PrintStream(
            new File(this.getClass().getClassLoader().getResource("version.properties").toURI()));
        ps.println("version=0.0.3");
        ps.println("release=1");
        ps.close();
        mockJsPath = new File(folder.getRoot(), "empty.js").getPath();

        this.mockSubReconciler = Mockito.mock(SubscriptionReconciler.class);
        this.consumerTypeCurator = Mockito.mock(ConsumerTypeCurator.class);
        oc = mock(OwnerCurator.class);

        this.translator = new StandardTranslator(this.consumerTypeCurator, this.environmentCurator, this.oc);
    }

    @After
    public void tearDown() throws Exception {
        PrintStream ps = new PrintStream(new File(this.getClass()
            .getClassLoader().getResource("version.properties").toURI()));
        ps.println("version=${version}");
        ps.println("release=${release}");
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
        File file = createFile("meta", "0.0.3", now, "test_user", "prefix");
        File actual = createFile("meta.json", "0.0.3", now, "test_user", "prefix");
        ExporterMetadataCurator emc = mock(ExporterMetadataCurator.class);
        ExporterMetadata em = new ExporterMetadata();
        Date daybefore = getDateBeforeDays(1);
        em.setExported(daybefore);
        em.setId("42");
        em.setType(ExporterMetadata.TYPE_SYSTEM);
        when(emc.getByType(ExporterMetadata.TYPE_SYSTEM)).thenReturn(em);
        Importer i = new Importer(null, null, null, null, null, null,
            null, null, null, emc, null, null, i18n, null,
            null, su, null, this.mockSubReconciler, this.ec, this.translator);
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
        File f = createFile("meta", "0.0.3", new Date(),
            "test_user", "prefix");
        File actualmeta = createFile("meta.json", "0.0.3", new Date(),
            "test_user", "prefix");
        ExporterMetadataCurator emc = mock(ExporterMetadataCurator.class);
        when(emc.getByType(ExporterMetadata.TYPE_SYSTEM)).thenReturn(null);
        Importer i = new Importer(null, null, null, null, null, null,
            null, null, null, emc, null, null, i18n,
            null, null, su, null, this.mockSubReconciler, this.ec, this.translator);
        i.validateMetadata(ExporterMetadata.TYPE_SYSTEM, null, actualmeta,
            new ConflictOverrides());
        assertTrue(f.delete());
        assertTrue(actualmeta.delete());
        verify(emc).create(any(ExporterMetadata.class));
    }

    @Test
    public void oldImport() throws Exception {
        // actualmeta is the mock for the import itself
        File actualmeta = createFile("meta.json", "0.0.3", getDateBeforeDays(10),
            "test_user", "prefix");
        ExporterMetadataCurator emc = mock(ExporterMetadataCurator.class);
        // emc is the mock for lastrun (i.e., the most recent import in CP)
        ExporterMetadata em = new ExporterMetadata();
        em.setExported(getDateBeforeDays(3));
        em.setId("42");
        em.setType(ExporterMetadata.TYPE_SYSTEM);
        when(emc.getByType(ExporterMetadata.TYPE_SYSTEM)).thenReturn(em);
        Importer i = new Importer(null, null, null, null, null, null,
            null, null, null, emc, null, null, i18n,
            null, null, su, null, this.mockSubReconciler, this.ec, this.translator);
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
        File actualmeta = createFile("meta.json", "0.0.3", date,
            "test_user", "prefix");
        ExporterMetadataCurator emc = mock(ExporterMetadataCurator.class);
        // emc is the mock for lastrun (i.e., the most recent import in CP)
        ExporterMetadata em = new ExporterMetadata();
        em.setExported(date); // exact same date = assumed same manifest
        em.setId("42");
        em.setType(ExporterMetadata.TYPE_SYSTEM);
        when(emc.getByType(ExporterMetadata.TYPE_SYSTEM)).thenReturn(em);
        Importer i = new Importer(null, null, null, null, null, null,
            null, null, null, emc, null, null, i18n,
            null, null, su, null, this.mockSubReconciler, this.ec, this.translator);
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
        List<ImportConflictException> exceptions = new LinkedList<>();
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
        File actualmeta = createFile("meta.json", "0.0.3", importDate,
            "test_user", "prefix");
        ExporterMetadataCurator emc = mock(ExporterMetadataCurator.class);
        // em is the mock for lastrun (i.e., the most recent import in CP)
        ExporterMetadata em = new ExporterMetadata();
        em.setExported(getDateBeforeDays(30));
        em.setId("42");
        em.setType(ExporterMetadata.TYPE_SYSTEM);
        when(emc.getByType(ExporterMetadata.TYPE_SYSTEM)).thenReturn(em);
        Importer i = new Importer(null, null, null, null, null, null,
            null, null, null, emc, null, null, i18n,
            null, null, su, null, this.mockSubReconciler, this.ec, this.translator);
        i.validateMetadata(ExporterMetadata.TYPE_SYSTEM, null, actualmeta,
            new ConflictOverrides());
        assertEquals(importDate, em.getExported());
    }

    @Test(expected = ImporterException.class)
    public void nullType() throws ImporterException, IOException {
        File actualmeta = createFile("meta.json", "0.0.3", new Date(),
            "test_user", "prefix");
        try {
            Importer i = new Importer(null, null, null, null, null, null,
                null, null, null, null, null, null, i18n,
                null, null, su, null, this.mockSubReconciler, this.ec, this.translator);

            // null Type should cause exception
            i.validateMetadata(null, null, actualmeta, new ConflictOverrides());
        }
        finally {
            assertTrue(actualmeta.delete());
        }
    }

    @Test(expected = ImporterException.class)
    public void expectOwner() throws ImporterException, IOException {
        File actualmeta = createFile("meta.json", "0.0.3", new Date(),
            "test_user", "prefix");
        ExporterMetadataCurator emc = mock(ExporterMetadataCurator.class);
        when(emc.getByTypeAndOwner(ExporterMetadata.TYPE_PER_USER, null))
            .thenReturn(null);
        Importer i = new Importer(null, null, null, null, null, null,
            null, null, null, emc, null, null, i18n,
            null, null, su, null, this.mockSubReconciler, this.ec, this.translator);

        // null Type should cause exception
        i.validateMetadata(ExporterMetadata.TYPE_PER_USER, null, actualmeta,
            new ConflictOverrides());
        verify(emc, never()).create(any(ExporterMetadata.class));
    }

    @Test
    public void testImportWithNonZipArchive()
        throws IOException, ImporterException {
        Importer i = new Importer(null, null, null, null, null, null,
            null, null, config, null, null, null, i18n,
            null, null, su, null, this.mockSubReconciler, this.ec, this.translator);
        Owner owner = mock(Owner.class);
        ConflictOverrides co = mock(ConflictOverrides.class);
        File archive = new File(folder.getRoot(), "non_zip_file.zip");
        FileWriter fw = new FileWriter(archive);
        fw.write("Just a flat file");
        fw.close();

        String m = i18n.tr("The archive {0} is not a properly compressed file or is empty",
            "non_zip_file.zip");
        ee.expect(ImportExtractionException.class);
        ee.expectMessage(m);
        i.loadExport(owner, archive, co, "original_file.zip");
    }

    @Test
    public void testImportZipArchiveNoContent()
        throws IOException, ImporterException {
        Importer i = new Importer(null, null, null, null, null, null,
            null, null, config, null, null, null, i18n,
            null, null, su, null, this.mockSubReconciler, this.ec, this.translator);
        Owner owner = mock(Owner.class);
        ConflictOverrides co = mock(ConflictOverrides.class);

        File archive = new File(folder.getRoot(), "file.zip");
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(archive));
        out.putNextEntry(new ZipEntry("no_content"));
        out.close();

        String m = i18n.tr("The archive does not contain the required signature file");
        ee.expect(ImportExtractionException.class);
        ee.expectMessage(m);
        i.loadExport(owner, archive, co, "original_file.zip");
    }

    @Test(expected = ImportConflictException.class)
    public void testImportBadSignature()
        throws IOException, ImporterException {
        PKIUtility pki = mock(PKIUtility.class);
        Importer i = new Importer(null, null, null, null, null, null, null,
            pki, config, null, null, null, i18n,
            null, null, su, null, this.mockSubReconciler, this.ec, this.translator);
        Owner owner = mock(Owner.class);
        ConflictOverrides co = mock(ConflictOverrides.class);

        File archive = new File(folder.getRoot(), "file.zip");
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(archive));
        out.putNextEntry(new ZipEntry("signature"));
        out.write("This is the placeholder for the signature file".getBytes());
        File ceArchive = new File(folder.getRoot(), "consumer_export.zip");
        FileOutputStream fos = new FileOutputStream(ceArchive);
        fos.write("This is just a flat file".getBytes());
        fos.close();
        addFileToArchive(out, ceArchive);
        out.close();

        i.loadExport(owner, archive, co, "original_file.zip");
    }

    @Test
    public void testImportBadConsumerZip() throws Exception {
        PKIUtility pki = mock(PKIUtility.class);
        Importer i = new Importer(null, null, null, null, null, null, null,
            pki, config, null, null, null, i18n,
            null, null, su, null, this.mockSubReconciler, this.ec, this.translator);
        Owner owner = mock(Owner.class);
        ConflictOverrides co = mock(ConflictOverrides.class);

        // Mock a passed signature check:
        when(pki.verifySHA256WithRSAHashAgainstCACerts(any(File.class),
            any(byte [].class))).thenReturn(true);

        File archive = new File(folder.getRoot(), "file.zip");
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(archive));
        out.putNextEntry(new ZipEntry("signature"));
        out.write("This is the placeholder for the signature file".getBytes());
        File ceArchive = new File(folder.getRoot(), "consumer_export.zip");
        FileOutputStream fos = new FileOutputStream(ceArchive);
        fos.write("This is just a flat file".getBytes());
        fos.close();
        addFileToArchive(out, ceArchive);
        out.close();

        ee.expect(ImportExtractionException.class);
        ee.expectMessage("not a properly compressed file or is empty");
        i.loadExport(owner, archive, co, "original_file.zip");
    }

    @Test
    public void testImportZipSigAndEmptyConsumerZip()
        throws Exception {
        PKIUtility pki = mock(ProviderBasedPKIUtility.class);
        Importer i = new Importer(null, null, null, null, null, null, null,
            pki, config, null, null, null, i18n,
            null, null, su, null, this.mockSubReconciler, this.ec, this.translator);
        Owner owner = mock(Owner.class);
        ConflictOverrides co = mock(ConflictOverrides.class);

        // Mock a passed signature check:
        when(pki.verifySHA256WithRSAHashAgainstCACerts(any(File.class),
            any(byte [].class))).thenReturn(true);

        File archive = new File(folder.getRoot(), "file.zip");
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(archive));
        out.putNextEntry(new ZipEntry("signature"));
        out.write("This is the placeholder for the signature file".getBytes());
        File ceArchive = new File(folder.getRoot(), "consumer_export.zip");
        ZipOutputStream cezip = new ZipOutputStream(new FileOutputStream(ceArchive));
        cezip.putNextEntry(new ZipEntry("no_content"));
        cezip.close();
        addFileToArchive(out, ceArchive);
        out.close();

        ee.expect(ImportExtractionException.class);
        ee.expectMessage("consumer_export archive has no contents");
        i.loadExport(owner, archive, co, "original_file.zip");
    }

    private Map<String, File> getTestImportFiles() {
        Map<String, File> importFiles = new HashMap<>();
        importFiles.put(ImportFile.META.fileName(), mock(File.class));
        importFiles.put(ImportFile.RULES_FILE.fileName(), mock(File.class));
        importFiles.put(ImportFile.CONSUMER_TYPE.fileName(), mock(File.class));
        importFiles.put(ImportFile.CONSUMER.fileName(), mock(File.class));
        importFiles.put(ImportFile.PRODUCTS.fileName(), mock(File.class));
        importFiles.put(ImportFile.ENTITLEMENTS.fileName(), mock(File.class));
        importFiles.put(ImportFile.DISTRIBUTOR_VERSIONS.fileName(), mock(File.class));
        return importFiles;
    }

    @Test
    public void testImportNoMeta() throws IOException, ImporterException {
        OwnerCurator oc = mock(OwnerCurator.class);
        Importer i = new Importer(null, null, null, oc, null, null, null,
            null, config, null, null, null, i18n,
            null, null, su, null, this.mockSubReconciler, this.ec, this.translator);
        Owner owner = mock(Owner.class);
        ConflictOverrides co = mock(ConflictOverrides.class);

        Map<String, File> importFiles = getTestImportFiles();
        importFiles.put(ImportFile.META.fileName(), null);

        String m = i18n.tr("The archive does not contain the " +
            "required meta.json file");
        ee.expect(ImporterException.class);
        ee.expectMessage(m);
        i.importObjects(owner, importFiles, co);
    }

    @Test
    public void testImportNoConsumerTypesDir() throws IOException, ImporterException {
        OwnerCurator oc = mock(OwnerCurator.class);
        Importer i = new Importer(null, null, null, oc, null, null, null,
            null, config, null, null, null, i18n,
            null, null, su, null, this.mockSubReconciler, this.ec, this.translator);
        Owner owner = mock(Owner.class);
        ConflictOverrides co = mock(ConflictOverrides.class);
        Map<String, File> importFiles = getTestImportFiles();

        importFiles.put(ImportFile.CONSUMER_TYPE.fileName(), null);

        String m = i18n.tr("The archive does not contain the " +
            "required consumer_types directory");
        ee.expect(ImporterException.class);
        ee.expectMessage(m);
        i.importObjects(owner, importFiles, co);
    }

    @Test
    public void testImportNoConsumer() throws IOException, ImporterException {
        OwnerCurator oc = mock(OwnerCurator.class);
        Importer i = new Importer(null, null, null, oc, null, null, null,
            null, config, null, null, null, i18n,
            null, null, su, null, this.mockSubReconciler, this.ec, this.translator);
        Owner owner = mock(Owner.class);
        ConflictOverrides co = mock(ConflictOverrides.class);
        Map<String, File> importFiles = getTestImportFiles();

        importFiles.put(ImportFile.CONSUMER.fileName(), null);

        String m = i18n.tr("The archive does not contain the required consumer.json file");
        ee.expect(ImporterException.class);
        ee.expectMessage(m);
        i.importObjects(owner, importFiles, co);
    }

    @Test
    public void testImportNoProductDir()
        throws IOException, ImporterException {
        RulesImporter ri = mock(RulesImporter.class);
        OwnerCurator oc = mock(OwnerCurator.class);
        Importer i = new Importer(null, null, ri, oc, null, null, null,
            null, config, null, null, null, i18n,
            null, null, su, null, this.mockSubReconciler, this.ec, this.translator);
        Owner owner = mock(Owner.class);
        ConflictOverrides co = mock(ConflictOverrides.class);
        Map<String, File> importFiles = getTestImportFiles();
        File ruleDir = mock(File.class);
        File[] rulesFiles = createMockJsFile(mockJsPath);
        when(ruleDir.listFiles()).thenReturn(rulesFiles);
        File actualmeta = createFile("meta.json", "0.0.3", new Date(), "test_user", "prefix");
        // this is the hook to stop testing. we confirm that the archive component tests
        //  are passed and then jump out instead of trying to fake the actual file
        //  processing.
        doThrow(new RuntimeException("Done with the test")).when(ri).importObject(
            any(Reader.class));

        importFiles.put(ImportFile.META.fileName(), actualmeta);
        importFiles.put(ImportFile.RULES_FILE.fileName(), rulesFiles[0]);
        importFiles.put(ImportFile.PRODUCTS.fileName(), null);
        importFiles.put(ImportFile.ENTITLEMENTS.fileName(), null);
        importFiles.put(ImportFile.UPSTREAM_CONSUMER.fileName(), mock(File.class));

        ee.expect(RuntimeException.class);
        ee.expectMessage("Done with the test");
        i.importObjects(owner, importFiles, co);
    }

    @Test
    public void testReturnsSubscriptionsFromManifest() throws IOException, ImporterException {

        Owner owner = new Owner("admin", "Admin Owner");

        ExporterMetadataCurator emc = mock(ExporterMetadataCurator.class);
        when(emc.getByTypeAndOwner("per_user", owner)).thenReturn(null);

        ConsumerType stype = new ConsumerType(ConsumerTypeEnum.SYSTEM);
        stype.setId("test-ctype");
        when(consumerTypeCurator.getByLabel(eq("system"))).thenReturn(stype);
        when(consumerTypeCurator.get(eq(stype.getId()))).thenReturn(stype);

        OwnerCurator oc = mock(OwnerCurator.class);
        when(oc.getByUpstreamUuid(any(String.class))).thenReturn(null);

        PoolManager pm = mock(PoolManager.class);
        Refresher refresher = mock(Refresher.class);
        when(pm.getRefresher(any(SubscriptionServiceAdapter.class), any(OwnerServiceAdapter.class)))
            .thenReturn(refresher);

        Map<String, File> importFiles = new HashMap<>();
        File ruleDir = mock(File.class);
        File[] rulesFiles = createMockJsFile(mockJsPath);
        when(ruleDir.listFiles()).thenReturn(rulesFiles);

        File actualmeta = createFile("meta.json", "0.0.3", new Date(), "test_user", "prefix");
        importFiles.put(ImportFile.META.fileName(), actualmeta);

        ConsumerDTO consumerDTO = new ConsumerDTO();
        consumerDTO.setUuid("eb5e04bf-be27-44cf-abe3-0c0b1edd523e");
        consumerDTO.setName("mymachine");
        ConsumerTypeDTO typeDTO = new ConsumerTypeDTO();
        typeDTO.setLabel("candlepin");
        typeDTO.setManifest(true);
        consumerDTO.setType(typeDTO);
        consumerDTO.setUrlWeb("foo.example.com/subscription");
        consumerDTO.setUrlApi("/candlepin");
        consumerDTO.setContentAccessMode("");
        OwnerDTO ownerDTO = new OwnerDTO();
        ownerDTO.setKey("admin");
        ownerDTO.setDisplayName("Admin Owner");
        consumerDTO.setOwner(ownerDTO);

        ConsumerType ctype = new ConsumerType(ConsumerTypeEnum.CANDLEPIN);
        ctype.setId("test-ctype");
        when(consumerTypeCurator.getByLabel(eq("candlepin"))).thenReturn(ctype);
        when(consumerTypeCurator.get(eq(ctype.getId()))).thenReturn(ctype);

        File consumerFile = new File(folder.getRoot(), "consumer.json");
        mapper.writeValue(consumerFile, consumerDTO);
        importFiles.put(ImportFile.CONSUMER.fileName(), consumerFile);

        File cTypes = mock(File.class);
        when(cTypes.listFiles()).thenReturn(new File[]{});
        importFiles.put(ImportFile.CONSUMER_TYPE.fileName(), cTypes);

        Product prod = new Product("prodId", "prodTest", null);
        prod.setDependentProductIds(null);
        File prodFile = new File(folder.getRoot(), "product.json");
        mapper.writeValue(prodFile, prod);
        File products = mock(File.class);
        when(products.listFiles()).thenReturn(new File[]{prodFile});
        importFiles.put(ImportFile.PRODUCTS.fileName(), products);

        Entitlement ent = new Entitlement();
        Pool pool = new Pool();
        pool.setProduct(prod);
        ent.setPool(pool);
        ent.setQuantity(2);
        File entFile = new File(folder.getRoot(), "entitlement.json");
        mapper.writeValue(entFile, ent);
        File entitlements = mock(File.class);
        when(entitlements.listFiles()).thenReturn(new File[]{entFile});
        importFiles.put(ImportFile.ENTITLEMENTS.fileName(), entitlements);

        RulesImporter ri = mock(RulesImporter.class);
        importFiles.put(ImportFile.RULES_FILE.fileName(), rulesFiles[0]);

        ConflictOverrides co = mock(ConflictOverrides.class);

        Importer i = new Importer(consumerTypeCurator, pc, ri, oc, null, null, pm,
            null, config, emc, null, null, i18n,
            null, null, su, null, this.mockSubReconciler, this.ec, this.translator);
        List<Subscription> subscriptions = i.importObjects(owner, importFiles, co);

        assertEquals(1, subscriptions.size());
        assertEquals("prodId", subscriptions.get(0).getProduct().getId());
        assertEquals(2, subscriptions.get(0).getQuantity().longValue());
    }

    @Test
    public void testImportProductNoEntitlementDir() throws IOException, ImporterException {
        OwnerCurator oc = mock(OwnerCurator.class);
        Importer i = new Importer(null, null, null, oc, null, null, null,
            null, config, null, null, null, i18n,
            null, null, su, null, this.mockSubReconciler, this.ec, this.translator);
        Owner owner = mock(Owner.class);
        ConflictOverrides co = mock(ConflictOverrides.class);
        Map<String, File> importFiles = getTestImportFiles();

        importFiles.put(ImportFile.ENTITLEMENTS.fileName(), null);

        String m = i18n.tr("The archive does not contain the " +
            "required entitlements directory");
        ee.expect(ImporterException.class);
        ee.expectMessage(m);
        i.importObjects(owner, importFiles, co);
    }

    private File createFile(String filename, String version, Date date,
        String username, String prefix)
        throws JsonGenerationException, JsonMappingException, IOException {

        File f = new File(folder.getRoot(), filename);
        Meta meta = new Meta(version, date, username, prefix, null);
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

    @Test
    public void importConsumer() throws Exception {
        OwnerCurator oc = mock(OwnerCurator.class);
        ConsumerType type = new ConsumerType(ConsumerTypeEnum.CANDLEPIN);
        type.setId("test-ctype");
        when(consumerTypeCurator.getByLabel(eq("candlepin"))).thenReturn(type);
        when(consumerTypeCurator.get(eq(type.getId()))).thenReturn(type);

        Importer i = new Importer(consumerTypeCurator, null, null, oc,
            mock(IdentityCertificateCurator.class), null, null,
            null, null, null, mock(CertificateSerialCurator.class), null, i18n,
            null, null, su, null, this.mockSubReconciler, this.ec, this.translator);
        File[] upstream = createUpstreamFiles();
        Owner owner = new Owner("admin", "Admin Owner");

        ConsumerDTO consumerDTO = new ConsumerDTO();
        consumerDTO.setUuid("eb5e04bf-be27-44cf-abe3-0c0b1edd523e");
        consumerDTO.setName("mymachine");
        ConsumerTypeDTO typeDTO = new ConsumerTypeDTO();
        typeDTO.setLabel("candlepin");
        typeDTO.setManifest(true);
        consumerDTO.setType(typeDTO);
        consumerDTO.setUrlWeb("foo.example.com/subscription");
        consumerDTO.setUrlApi("/candlepin");
        consumerDTO.setContentAccessMode("access_mode");
        OwnerDTO ownerDTO = new OwnerDTO();
        ownerDTO.setKey("admin");
        ownerDTO.setDisplayName("Admin Owner");
        consumerDTO.setOwner(ownerDTO);

        File consumerfile = new File(folder.getRoot(), "consumer.json");
        mapper.writeValue(consumerfile, consumerDTO);
        ConflictOverrides forcedConflicts = mock(ConflictOverrides.class);
        when(forcedConflicts.isForced(any(Importer.Conflict.class))).thenReturn(false);

        Meta meta = new Meta("1.0", new Date(), "admin", "/candlepin/owners", null);

        i.importConsumer(owner, consumerfile, upstream, forcedConflicts, meta);

        verify(oc).merge(eq(owner));
    }

    private File[] createUpstreamFiles() throws URISyntaxException {
        File[] upstream = new File[2];
        File idcertfile = new File(classLoader.getResource("upstream/testidcert.json").toURI());
        File kpfile = new File(classLoader.getResource("upstream/keypair.pem").toURI());
        upstream[0] = idcertfile;
        upstream[1] = kpfile;
        return upstream;
    }

    private DistributorVersion createTestDistributerVersion() {
        DistributorVersion dVersion = new DistributorVersion("test-dist-ver");
        Set<DistributorVersionCapability> capabilities = new HashSet<>();
        capabilities.add(new DistributorVersionCapability(null, "capability-1"));
        capabilities.add(new DistributorVersionCapability(null, "capability-2"));
        capabilities.add(new DistributorVersionCapability(null, "capability-3"));
        dVersion.setCapabilities(capabilities);
        return dVersion;
    }

    @Test
    public void importDistributorVersionCreate() throws Exception {
        DistributorVersionCurator dvc = mock(DistributorVersionCurator.class);
        Importer i = new Importer(null, null, null, null, null, null,
            null, null, null, null, null, null, i18n,
            dvc, null, su, null, this.mockSubReconciler, this.ec, this.translator);
        File[] distVer = new File[1];
        distVer[0] = new File(folder.getRoot(), "dist-ver.json");
        mapper.writeValue(distVer[0], createTestDistributerVersion());

        i.importDistributorVersions(distVer);

        verify(dvc).create(any(DistributorVersion.class));
        verify(dvc, never()).merge(any(DistributorVersion.class));
    }

    @Test
    public void importDistributorVersionUpdate() throws Exception {
        DistributorVersionCurator dvc = mock(DistributorVersionCurator.class);
        Importer i = new Importer(null, null, null, null, null, null,
            null, null, null, null, null, null, i18n,
            dvc, null, su, null, this.mockSubReconciler, this.ec, this.translator);
        when(dvc.findByName("test-dist-ver")).thenReturn(
            new DistributorVersion("test-dist-ver"));
        File[] distVer = new File[1];
        distVer[0] = new File(folder.getRoot(), "dist-ver.json");
        mapper.writeValue(distVer[0], createTestDistributerVersion());

        i.importDistributorVersions(distVer);

        verify(dvc, never()).create(any(DistributorVersion.class));
        verify(dvc).merge(any(DistributorVersion.class));
    }

    @Test
    public void testImportNoDistributorVersions()
        throws IOException, ImporterException {
        RulesImporter ri = mock(RulesImporter.class);
        doNothing().when(ri).importObject(any(Reader.class));
        ExporterMetadataCurator emc = mock(ExporterMetadataCurator.class);

        // this is the hook to stop testing. we confirm that the dist version null test
        //  is passed and then jump out instead of trying to fake the actual file
        //  processing.
        doThrow(new RuntimeException("Done with the test")).when(emc)
            .getByTypeAndOwner(any(String.class), any(Owner.class));
        OwnerCurator oc = mock(OwnerCurator.class);
        Owner owner = mock(Owner.class);
        ConflictOverrides co = mock(ConflictOverrides.class);
        Map<String, File> importFiles = createAndSetImportFiles();
        Importer i = new Importer(null, null, ri, oc, null, null,
            null, null, config, emc, null, null, i18n,
            null, null, su, null, this.mockSubReconciler, this.ec, this.translator);

        ee.expect(RuntimeException.class);
        ee.expectMessage("Done with the test");
        i.importObjects(owner, importFiles, co);
    }

    private Map<String, File> createAndSetImportFiles()
        throws IOException, JsonGenerationException, JsonMappingException {
        File[] rulesFiles = createMockJsFile(mockJsPath);
        File ruleDir = mock(File.class);
        when(ruleDir.listFiles()).thenReturn(rulesFiles);
        File actualmeta = createFile("meta.json", "0.0.3", new Date(), "test_user", "prefix");
        Map<String, File> importFiles = getTestImportFiles();
        importFiles.put(ImportFile.META.fileName(), actualmeta);
        importFiles.put(ImportFile.RULES_FILE.fileName(), rulesFiles[0]);
        importFiles.put(ImportFile.PRODUCTS.fileName(), null);
        importFiles.put(ImportFile.ENTITLEMENTS.fileName(), null);
        importFiles.put(ImportFile.DISTRIBUTOR_VERSIONS.fileName(), null);
        File cTypes = mock(File.class);
        when(cTypes.listFiles()).thenReturn(new File[]{});
        importFiles.put(ImportFile.CONSUMER_TYPE.fileName(), cTypes);

        return importFiles;
    }

    @Test
    public void testRecordImportSuccess() {
        String expectedOwnerKey = "TEST_OWNER";
        Owner owner = new Owner(expectedOwnerKey);

        EventSink eventSinkMock = mock(EventSink.class);
        ImportRecordCurator importRecordCurator = mock(ImportRecordCurator.class);
        Importer importer = new Importer(null, null, null, null, null, null, null, null, config, null,
            null, eventSinkMock, i18n,
            null, null, su, importRecordCurator, this.mockSubReconciler, this.ec, this.translator);

        Meta meta = new Meta("1.0", new Date(), "test-user", "candlepin", "testcdn");

        List<Subscription> subscriptions = new ArrayList<>();
        Subscription subscription = new Subscription();
        subscriptions.add(subscription);

        Map<String, Object> data = new HashMap<>();
        data.put("meta", meta);
        data.put("subscriptions", subscriptions);

        ImportRecord record = importer.recordImportSuccess(owner, data, new ConflictOverrides(), "test.zip");
        assertEquals(meta.getPrincipalName(), record.getGeneratedBy());
        assertEquals(meta.getCreated(), record.getGeneratedDate());
        assertEquals(ImportRecord.Status.SUCCESS, record.getStatus());
        assertEquals(owner.getKey() + " file imported successfully.", record.getStatusMessage());
        assertEquals("test.zip", record.getFileName());
        verify(importRecordCurator).create(eq(record));
        verify(eventSinkMock, never()).emitSubscriptionExpired(subscription);
    }

    @Test
    public void testRecordImportSetsUpstreamConsumerFromOwner() {
        String expectedOwnerKey = "TEST_OWNER";
        Owner owner = new Owner(expectedOwnerKey);

        UpstreamConsumer uc = new UpstreamConsumer("uc", owner,
            new ConsumerType(ConsumerType.ConsumerTypeEnum.CANDLEPIN), "uuid");
        uc.setContentAccessMode("mode");
        owner.setUpstreamConsumer(uc);

        EventSink eventSinkMock = mock(EventSink.class);
        ImportRecordCurator importRecordCurator = mock(ImportRecordCurator.class);
        Importer importer = new Importer(null, null, null, null, null, null, null, null, config, null,
            null, eventSinkMock, i18n,
            null, null, su, importRecordCurator, this.mockSubReconciler, this.ec, this.translator);

        Meta meta = new Meta("1.0", new Date(), "test-user", "candlepin", "testcdn");

        Map<String, Object> data = new HashMap<>();
        data.put("meta", meta);
        data.put("subscriptions",  new ArrayList<Subscription>());

        ImportRecord record = importer.recordImportSuccess(owner, data, new ConflictOverrides(), "test.zip");

        ImportUpstreamConsumer iuc = record.getUpstreamConsumer();
        assertNotNull(iuc);
        assertEquals(uc.getOwnerId(), iuc.getOwnerId());
        assertEquals(uc.getName(), iuc.getName());
        assertEquals(uc.getUuid(), iuc.getUuid());
        assertEquals(uc.getType(), iuc.getType());
        assertEquals(uc.getWebUrl(), iuc.getWebUrl());
        assertEquals(uc.getApiUrl(), iuc.getApiUrl());
        assertEquals(uc.getContentAccessMode(), iuc.getContentAccessMode());

        verify(importRecordCurator).create(eq(record));
    }

    @Test
    public void testRecordImportIgnoresUpstreamConsumerIfNotSetOnOwner() {
        String expectedOwnerKey = "TEST_OWNER";
        Owner owner = new Owner(expectedOwnerKey);

        EventSink eventSinkMock = mock(EventSink.class);
        ImportRecordCurator importRecordCurator = mock(ImportRecordCurator.class);
        Importer importer = new Importer(null, null, null, null, null, null, null, null, config, null,
            null, eventSinkMock, i18n,
            null, null, su, importRecordCurator, this.mockSubReconciler, this.ec, this.translator);

        Meta meta = new Meta("1.0", new Date(), "test-user", "candlepin", "testcdn");

        Map<String, Object> data = new HashMap<>();
        data.put("meta", meta);
        data.put("subscriptions",  new ArrayList<Subscription>());

        ImportRecord record = importer.recordImportSuccess(owner, data, new ConflictOverrides(), "test.zip");
        assertNull(record.getUpstreamConsumer());
        verify(importRecordCurator).create(eq(record));
    }

    @Test
    public void testRecordImportExpiredSubsFound() {
        String expectedOwnerKey = "TEST_OWNER";
        Owner owner = new Owner(expectedOwnerKey);

        EventSink eventSinkMock = mock(EventSink.class);
        ImportRecordCurator importRecordCurator = mock(ImportRecordCurator.class);
        Importer importer = new Importer(null, null, null, null, null, null, null, null, config, null,
            null, eventSinkMock, i18n,
            null, null, su, importRecordCurator, this.mockSubReconciler, this.ec, this.translator);

        Map<String, Object> data = new HashMap<>();
        List<Subscription> subscriptions = new ArrayList<>();
        Subscription subscription1 = new Subscription();
        //expires tomorrow
        subscription1.setEndDate(new Date((new Date()).getTime() + (1000 * 60 * 60 * 24)));
        subscriptions.add(subscription1);

        Subscription subscription2 = new Subscription();
        //expires yesterday
        subscription2.setEndDate(new Date((new Date()).getTime() - (1000 * 60 * 60 * 24)));
        subscriptions.add(subscription2);
        data.put("subscriptions", subscriptions);

        ImportRecord record = importer.recordImportSuccess(owner, data, new ConflictOverrides(), "test.zip");
        assertEquals(ImportRecord.Status.SUCCESS_WITH_WARNING, record.getStatus());
        assertEquals(owner.getKey() + " file imported successfully." +
            "One or more inactive subscriptions found in the file.", record.getStatusMessage());
        verify(eventSinkMock, never()).emitSubscriptionExpired(subscription1);
        verify(eventSinkMock).emitSubscriptionExpired(subscription2);
        verify(importRecordCurator).create(eq(record));
    }

    @Test
    public void testRecordImportNoActiveSubsFound() {
        String expectedOwnerKey = "TEST_OWNER";
        Owner owner = new Owner(expectedOwnerKey);

        EventSink eventSinkMock = mock(EventSink.class);
        ImportRecordCurator importRecordCurator = mock(ImportRecordCurator.class);
        Importer importer = new Importer(null, null, null, null, null, null, null, null, config, null,
            null, eventSinkMock, i18n,
            null, null, su, importRecordCurator, this.mockSubReconciler, this.ec, this.translator);

        Map<String, Object> data = new HashMap<>();
        data.put("subscriptions", new ArrayList<Subscription>());

        ImportRecord record = importer.recordImportSuccess(owner, data, new ConflictOverrides(), "test.zip");
        assertEquals(ImportRecord.Status.SUCCESS_WITH_WARNING, record.getStatus());
        assertEquals(owner.getKey() + " file imported successfully." +
            "No active subscriptions found in the file.", record.getStatusMessage());
        verify(eventSinkMock, never()).emitSubscriptionExpired(any(Subscription.class));
        verify(importRecordCurator).create(eq(record));
    }

}
