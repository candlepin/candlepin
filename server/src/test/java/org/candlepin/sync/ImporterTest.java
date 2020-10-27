/**
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.candlepin.audit.EventSink;
import org.candlepin.common.config.Configuration;
import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.ContentAccessManager;
import org.candlepin.controller.PoolManager;
import org.candlepin.controller.Refresher;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.StandardTranslator;
import org.candlepin.dto.manifest.v1.ConsumerDTO;
import org.candlepin.dto.manifest.v1.ConsumerTypeDTO;
import org.candlepin.dto.manifest.v1.EntitlementDTO;
import org.candlepin.dto.manifest.v1.OwnerDTO;
import org.candlepin.dto.manifest.v1.SubscriptionDTO;
import org.candlepin.model.CdnCurator;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.ContentCurator;
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
import org.candlepin.pki.impl.JSSProviderLoader;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.sync.Importer.ImportFile;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.hamcrest.core.StringContains;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
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
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ImporterTest {

    @TempDir protected File tmpFolder;

    private Configuration config;
    private I18n i18n;
    private ModelTranslator modelTranslator;
    private SyncUtils syncUtils;

    @Mock private CdnCurator mockCdnCurator;
    @Mock private CertificateSerialCurator mockCertSerialCurator;
    @Mock private ConsumerTypeCurator mockConsumerTypeCurator;
    @Mock private ContentCurator mockContentCurator;
    @Mock private EntitlementCurator mockEntitlementCurator;
    @Mock private EnvironmentCurator mockEnvironmentCurator;
    @Mock private ExporterMetadataCurator mockExporterMetadataCurator;
    @Mock private IdentityCertificateCurator mockIdentityCertCurator;
    @Mock private ImportRecordCurator mockImportRecordCurator;
    @Mock private OwnerCurator mockOwnerCurator;
    @Mock private ProductCurator mockProductCurator;

    @Mock private PoolManager mockPoolManager;
    @Mock private ContentAccessManager mockContentAccessManager;

    @Mock private RulesImporter mockRulesImporter;
    @Mock private PKIUtility mockPKIUtility;
    @Mock private EventSink mockEventSink;
    @Mock private DistributorVersionCurator mockDistributorVersionCurator;
    @Mock private SubscriptionReconciler mockSubscriptionReconciler;

    private ObjectMapper mapper;
    private ClassLoader classLoader = getClass().getClassLoader();
    private String mockJsPath;

    static {
        JSSProviderLoader.addProvider();
    }

    @BeforeEach
    public void init() throws Exception {
        this.config = new CandlepinCommonTestConfig();
        this.config.setProperty(ConfigProperties.FAIL_ON_UNKNOWN_IMPORT_PROPERTIES, "false");

        this.i18n = I18nFactory.getI18n(this.getClass(), Locale.US, I18nFactory.FALLBACK);
        this.modelTranslator = new StandardTranslator(this.mockConsumerTypeCurator,
            this.mockEnvironmentCurator, this.mockOwnerCurator);

        this.syncUtils = new SyncUtils(this.config);
        this.mapper = this.syncUtils.getObjectMapper();
        this.mockJsPath = new File(this.tmpFolder, "empty.js").getPath();

        this.updateReleaseVersion("0.0.3", "1");
    }

    @AfterEach
    public void tearDown() throws Exception {
        this.updateReleaseVersion("${version}", "${release}");
    }

    private void updateReleaseVersion(String version, String release) throws URISyntaxException, IOException {
        File target = new File(this.getClass().getClassLoader().getResource("version.properties").toURI());
        PrintStream stream = new PrintStream(target);

        stream.print("version=");
        stream.println(version);
        stream.print("release=");
        stream.println(version);

        stream.close();
    }

    private Importer buildImporter() {
        return new Importer(this.mockConsumerTypeCurator, this.mockProductCurator, this.mockRulesImporter,
            this.mockOwnerCurator, this.mockIdentityCertCurator,
            this.mockPoolManager, this.mockPKIUtility, this.mockExporterMetadataCurator,
            this.mockCertSerialCurator, this.mockEventSink, this.i18n, this.mockDistributorVersionCurator,
            this.mockCdnCurator, this.syncUtils, this.mockImportRecordCurator,
            this.mockSubscriptionReconciler, this.mockEntitlementCurator, this.mockContentAccessManager,
            this.modelTranslator);
    }

    private File createFile(String filename, String version, Date date, String username, String prefix)
        throws JsonGenerationException, JsonMappingException, IOException {

        File file = new File(this.tmpFolder, filename);
        Meta meta = new Meta(version, date, username, prefix, null);

        this.mapper.writeValue(file, meta);

        return file;
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

    private ConsumerType mockConsumerType(ConsumerType mock) {
        if (mock == null) {
            return null;
        }

        if (mock.getLabel() != null) {
            doReturn(mock)
                .when(this.mockConsumerTypeCurator)
                .getByLabel(eq(mock.getLabel()));
        }

        if (mock.getId() != null) {
            doReturn(mock)
                .when(this.mockConsumerTypeCurator)
                .get(eq(mock.getId()));
        }

        return mock;
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

        ExporterMetadata em = new ExporterMetadata();
        Date daybefore = getDateBeforeDays(1);
        em.setExported(daybefore);
        em.setId("42");
        em.setType(ExporterMetadata.TYPE_SYSTEM);

        doReturn(em)
            .when(this.mockExporterMetadataCurator)
            .getByType(eq(ExporterMetadata.TYPE_SYSTEM));

        Importer importer = this.buildImporter();
        importer.validateMetadata(ExporterMetadata.TYPE_SYSTEM, null, actual, new ConflictOverrides());

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
        File file = createFile("meta", "0.0.3", new Date(), "test_user", "prefix");
        File actualmeta = createFile("meta.json", "0.0.3", new Date(), "test_user", "prefix");

        Importer importer = this.buildImporter();
        importer.validateMetadata(ExporterMetadata.TYPE_SYSTEM, null, actualmeta, new ConflictOverrides());

        assertTrue(file.delete());
        assertTrue(actualmeta.delete());
        verify(this.mockExporterMetadataCurator).create(any(ExporterMetadata.class));
    }

    @Test
    public void oldImport() throws Exception {
        // actualmeta is the mock for the import itself
        File actualmeta = createFile("meta.json", "0.0.3", getDateBeforeDays(10), "test_user", "prefix");

        ExporterMetadata em = new ExporterMetadata();
        em.setExported(getDateBeforeDays(3));
        em.setId("42");
        em.setType(ExporterMetadata.TYPE_SYSTEM);

        doReturn(em)
            .when(this.mockExporterMetadataCurator)
            .getByType(eq(ExporterMetadata.TYPE_SYSTEM));

        Importer importer = this.buildImporter();

        try {
            importer.validateMetadata(ExporterMetadata.TYPE_SYSTEM, null, actualmeta,
                new ConflictOverrides());

            fail("Expected an ImportConflictException, but no exception was thrown");
        }
        catch (ImportConflictException e) {
            assertFalse(e.message().getConflicts().isEmpty());
            assertEquals(1, e.message().getConflicts().size());
            assertTrue(e.message().getConflicts().contains(Importer.Conflict.MANIFEST_OLD));
        }
    }

    @Test
    public void sameImport() throws Exception {
        // actualmeta is the mock for the import itself
        Date date = getDateBeforeDays(10);
        File actualmeta = createFile("meta.json", "0.0.3", date, "test_user", "prefix");

        ExporterMetadata em = new ExporterMetadata();
        em.setExported(date); // exact same date = assumed same manifest
        em.setId("42");
        em.setType(ExporterMetadata.TYPE_SYSTEM);

        doReturn(em)
            .when(this.mockExporterMetadataCurator)
            .getByType(eq(ExporterMetadata.TYPE_SYSTEM));

        Importer importer = this.buildImporter();

        try {
            importer.validateMetadata(ExporterMetadata.TYPE_SYSTEM, null, actualmeta,
                new ConflictOverrides());

            fail("Expected an ImportConflictException, but no exception was thrown");
        }
        catch (ImportConflictException e) {
            assertFalse(e.message().getConflicts().isEmpty());
            assertEquals(1, e.message().getConflicts().size());
            assertTrue(e.message().getConflicts().contains(Importer.Conflict.MANIFEST_SAME));
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
        assertTrue(e1.message().getConflicts().contains(Importer.Conflict.DISTRIBUTOR_CONFLICT));
        assertTrue(e1.message().getConflicts().contains(Importer.Conflict.MANIFEST_OLD));
    }

    @Test
    public void newerImport() throws Exception {
        // this tests bz #790751
        Date importDate = getDateBeforeDays(10);

        // actualmeta is the mock for the import itself
        File actualmeta = createFile("meta.json", "0.0.3", importDate, "test_user", "prefix");

        ExporterMetadata em = new ExporterMetadata();
        em.setExported(getDateBeforeDays(30));
        em.setId("42");
        em.setType(ExporterMetadata.TYPE_SYSTEM);

        doReturn(em)
            .when(this.mockExporterMetadataCurator)
            .getByType(eq(ExporterMetadata.TYPE_SYSTEM));

        Importer importer = this.buildImporter();
        importer.validateMetadata(ExporterMetadata.TYPE_SYSTEM, null, actualmeta, new ConflictOverrides());

        assertEquals(em.getExported(), importDate);
    }

    @Test
    public void nullType() throws ImporterException, IOException {
        File actualmeta = createFile("meta.json", "0.0.3", new Date(), "test_user", "prefix");

        Importer importer = this.buildImporter();

        // null Type should cause exception
        assertThrows(ImporterException.class,
            () -> importer.validateMetadata(null, null, actualmeta, new ConflictOverrides()));

        assertTrue(actualmeta.delete());
    }

    @Test
    public void expectOwner() throws ImporterException, IOException {
        ConflictOverrides overrides = new ConflictOverrides();
        File actualmeta = createFile("meta.json", "0.0.3", new Date(), "test_user", "prefix");

        Importer importer = this.buildImporter();

        // null Type should cause exception
        assertThrows(ImporterException.class,
            () -> importer.validateMetadata(ExporterMetadata.TYPE_PER_USER, null, actualmeta, overrides));

        verify(this.mockExporterMetadataCurator, never()).create(any(ExporterMetadata.class));
    }

    @Test
    public void testImportWithNonZipArchive() throws IOException, ImporterException {
        Owner owner = mock(Owner.class);
        ConflictOverrides co = mock(ConflictOverrides.class);
        File archive = new File(this.tmpFolder, "non_zip_file.zip");
        FileWriter fw = new FileWriter(archive);
        fw.write("Just a flat file");
        fw.close();

        Importer importer = this.buildImporter();

        Throwable throwable = assertThrows(ImportExtractionException.class,
            () -> importer.loadExport(owner, archive, co, "original_file.zip"));

        String m = i18n.tr("The archive {0} is not a properly compressed file or is empty",
            "non_zip_file.zip");

        assertThat(throwable.getMessage(), StringContains.containsString(m));
    }

    @Test
    public void testImportZipArchiveNoContent() throws IOException, ImporterException {
        Importer importer = this.buildImporter();

        Owner owner = mock(Owner.class);
        ConflictOverrides co = mock(ConflictOverrides.class);

        File archive = new File(this.tmpFolder, "file.zip");
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(archive));
        out.putNextEntry(new ZipEntry("no_content"));
        out.close();

        Throwable throwable = assertThrows(ImportExtractionException.class,
            () -> importer.loadExport(owner, archive, co, "original_file.zip"));

        String m = i18n.tr("The archive does not contain the required signature file");
        assertThat(throwable.getMessage(), StringContains.containsString(m));
    }

    @Test
    public void testImportBadSignature() throws IOException, ImporterException {
        Owner owner = mock(Owner.class);
        ConflictOverrides co = mock(ConflictOverrides.class);

        File archive = new File(this.tmpFolder, "file.zip");
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(archive));
        out.putNextEntry(new ZipEntry("signature"));
        out.write("This is the placeholder for the signature file".getBytes());

        File ceArchive = new File(this.tmpFolder, "consumer_export.zip");
        FileOutputStream fos = new FileOutputStream(ceArchive);
        fos.write("This is just a flat file".getBytes());
        fos.close();

        addFileToArchive(out, ceArchive);
        out.close();

        Importer importer = this.buildImporter();
        assertThrows(ImportConflictException.class,
            () -> importer.loadExport(owner, archive, co, "original_file.zip"));
    }

    @Test
    public void testImportBadConsumerZip() throws Exception {
        // Mock a passed signature check:
        doReturn(true)
            .when(this.mockPKIUtility)
            .verifySHA256WithRSAHashAgainstCACerts(any(File.class), any(byte [].class));

        Owner owner = mock(Owner.class);
        ConflictOverrides co = mock(ConflictOverrides.class);

        File archive = new File(this.tmpFolder, "file.zip");
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(archive));
        out.putNextEntry(new ZipEntry("signature"));
        out.write("This is the placeholder for the signature file".getBytes());

        File ceArchive = new File(this.tmpFolder, "consumer_export.zip");
        FileOutputStream fos = new FileOutputStream(ceArchive);
        fos.write("This is just a flat file".getBytes());
        fos.close();

        addFileToArchive(out, ceArchive);
        out.close();

        Importer importer = this.buildImporter();
        Throwable throwable = assertThrows(ImportExtractionException.class,
            () -> importer.loadExport(owner, archive, co, "original_file.zip"));

        String errmsg = "not a properly compressed file or is empty";
        assertThat(throwable.getMessage(), StringContains.containsString(errmsg));
    }

    @Test
    public void testImportZipSigAndEmptyConsumerZip() throws Exception {
        // Mock a passed signature check:
        doReturn(true)
            .when(this.mockPKIUtility)
            .verifySHA256WithRSAHashAgainstCACerts(any(File.class), any(byte [].class));

        Owner owner = mock(Owner.class);
        ConflictOverrides co = mock(ConflictOverrides.class);

        File archive = new File(this.tmpFolder, "file.zip");
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(archive));
        out.putNextEntry(new ZipEntry("signature"));
        out.write("This is the placeholder for the signature file".getBytes());

        File ceArchive = new File(this.tmpFolder, "consumer_export.zip");
        ZipOutputStream cezip = new ZipOutputStream(new FileOutputStream(ceArchive));
        cezip.putNextEntry(new ZipEntry("no_content"));
        cezip.close();

        addFileToArchive(out, ceArchive);
        out.close();

        Importer importer = this.buildImporter();
        Throwable throwable = assertThrows(ImportExtractionException.class,
            () -> importer.loadExport(owner, archive, co, "original_file.zip"));

        String errmsg = "consumer_export archive has no contents";
        assertThat(throwable.getMessage(), StringContains.containsString(errmsg));
    }

    @Test
    public void testImportNoMeta() throws IOException, ImporterException {
        Owner owner = mock(Owner.class);
        ConflictOverrides co = mock(ConflictOverrides.class);

        Map<String, File> importFiles = this.getTestImportFiles();
        importFiles.put(ImportFile.META.fileName(), null);

        Importer importer = this.buildImporter();
        Throwable throwable = assertThrows(ImporterException.class,
            () -> importer.importObjects(owner, importFiles, co));

        String errmsg = i18n.tr("The archive does not contain the required meta.json file");
        assertThat(throwable.getMessage(), StringContains.containsString(errmsg));
    }

    @Test
    public void testImportNoConsumerTypesDir() throws IOException, ImporterException {
        Owner owner = mock(Owner.class);
        ConflictOverrides co = mock(ConflictOverrides.class);
        Map<String, File> importFiles = this.getTestImportFiles();

        importFiles.put(ImportFile.CONSUMER_TYPE.fileName(), null);

        Importer importer = this.buildImporter();
        Throwable throwable = assertThrows(ImporterException.class,
            () -> importer.importObjects(owner, importFiles, co));

        String errmsg = i18n.tr("The archive does not contain the required consumer_types directory");
        assertThat(throwable.getMessage(), StringContains.containsString(errmsg));
    }

    @Test
    public void testImportNoConsumer() throws IOException, ImporterException {
        Owner owner = mock(Owner.class);
        ConflictOverrides co = mock(ConflictOverrides.class);
        Map<String, File> importFiles = this.getTestImportFiles();

        importFiles.put(ImportFile.CONSUMER.fileName(), null);

        Importer importer = this.buildImporter();
        Throwable throwable = assertThrows(ImporterException.class,
            () -> importer.importObjects(owner, importFiles, co));

        String errmsg = i18n.tr("The archive does not contain the required consumer.json file");
        assertThat(throwable.getMessage(), StringContains.containsString(errmsg));
    }

    @Test
    public void testImportNoProductDir() throws IOException, ImporterException {
        Owner owner = mock(Owner.class);
        ConflictOverrides co = mock(ConflictOverrides.class);

        Map<String, File> importFiles = this.getTestImportFiles();
        File ruleDir = mock(File.class);
        File[] rulesFiles = createMockJsFile(mockJsPath);
        when(ruleDir.listFiles()).thenReturn(rulesFiles);
        File actualmeta = createFile("meta.json", "0.0.3", new Date(), "test_user", "prefix");

        // this is the hook to stop testing. we confirm that the archive component tests
        //  are passed and then jump out instead of trying to fake the actual file
        //  processing.

        doThrow(new RuntimeException("Done with the test"))
            .when(this.mockRulesImporter)
            .importObject(any(Reader.class));

        importFiles.put(ImportFile.META.fileName(), actualmeta);
        importFiles.put(ImportFile.RULES_FILE.fileName(), rulesFiles[0]);
        importFiles.put(ImportFile.PRODUCTS.fileName(), null);
        importFiles.put(ImportFile.ENTITLEMENTS.fileName(), null);
        importFiles.put(ImportFile.UPSTREAM_CONSUMER.fileName(), mock(File.class));

        Importer importer = this.buildImporter();
        Throwable throwable = assertThrows(RuntimeException.class,
            () -> importer.importObjects(owner, importFiles, co));

        String errmsg = "Done with the test";
        assertThat(throwable.getMessage(), StringContains.containsString(errmsg));
    }

    @Test
    public void testReturnsSubscriptionsFromManifest() throws IOException, ImporterException {
        Owner owner = new Owner("admin", "Admin Owner");

        ConsumerType stype = new ConsumerType(ConsumerTypeEnum.SYSTEM);
        stype.setId("test-ctype");
        this.mockConsumerType(stype);

        Refresher mockRefresher = mock(Refresher.class);

        doReturn(mockRefresher)
            .when(this.mockPoolManager)
            .getRefresher(any(SubscriptionServiceAdapter.class));

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
        this.mockConsumerType(ctype);

        File consumerFile = new File(this.tmpFolder, "consumer.json");
        this.mapper.writeValue(consumerFile, consumerDTO);
        importFiles.put(ImportFile.CONSUMER.fileName(), consumerFile);

        File cTypes = mock(File.class);
        when(cTypes.listFiles()).thenReturn(new File[]{});
        importFiles.put(ImportFile.CONSUMER_TYPE.fileName(), cTypes);

        Product prod = new Product("prodId", "prodTest", null);
        prod.setDependentProductIds(null);
        File prodFile = new File(this.tmpFolder, "product.json");
        this.mapper.writeValue(prodFile, prod);
        File products = mock(File.class);
        when(products.listFiles()).thenReturn(new File[]{prodFile});
        importFiles.put(ImportFile.PRODUCTS.fileName(), products);

        Entitlement ent = new Entitlement();
        Pool pool = new Pool();
        pool.setProduct(prod);
        ent.setPool(pool);
        ent.setQuantity(2);
        File entFile = new File(this.tmpFolder, "entitlement.json");
        this.mapper.writeValue(entFile, this.modelTranslator.translate(ent, EntitlementDTO.class));
        File entitlements = mock(File.class);
        when(entitlements.listFiles()).thenReturn(new File[]{entFile});

        importFiles.put(ImportFile.ENTITLEMENTS.fileName(), entitlements);
        importFiles.put(ImportFile.RULES_FILE.fileName(), rulesFiles[0]);

        ConflictOverrides co = mock(ConflictOverrides.class);

        Importer importer = this.buildImporter();
        List<SubscriptionDTO> subscriptions = importer.importObjects(owner, importFiles, co);

        assertEquals(1, subscriptions.size());
        assertEquals("prodId", subscriptions.get(0).getProduct().getId());
        assertEquals(2, subscriptions.get(0).getQuantity().longValue());
    }

    @Test
    public void testImportProductNoEntitlementDir() throws IOException, ImporterException {
        Owner owner = mock(Owner.class);
        ConflictOverrides co = mock(ConflictOverrides.class);
        Map<String, File> importFiles = this.getTestImportFiles();

        importFiles.put(ImportFile.ENTITLEMENTS.fileName(), null);

        Importer importer = this.buildImporter();
        Throwable throwable = assertThrows(ImporterException.class,
            () -> importer.importObjects(owner, importFiles, co));

        String errmsg = i18n.tr("The archive does not contain the required entitlements directory");
        assertThat(throwable.getMessage(), StringContains.containsString(errmsg));
    }

    @Test
    public void importConsumer() throws Exception {
        ConsumerType type = new ConsumerType(ConsumerTypeEnum.CANDLEPIN);
        type.setId("test-ctype");
        this.mockConsumerType(type);

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

        File consumerfile = new File(this.tmpFolder, "consumer.json");
        this.mapper.writeValue(consumerfile, consumerDTO);
        ConflictOverrides forcedConflicts = mock(ConflictOverrides.class);
        when(forcedConflicts.isForced(any(Importer.Conflict.class))).thenReturn(false);

        Meta meta = new Meta("1.0", new Date(), "admin", "/candlepin/owners", null);

        Importer importer = this.buildImporter();
        importer.importConsumer(owner, consumerfile, upstream, forcedConflicts, meta);

        verify(this.mockOwnerCurator).merge(eq(owner));
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
        File[] distVer = new File[1];
        distVer[0] = new File(this.tmpFolder, "dist-ver.json");
        mapper.writeValue(distVer[0], createTestDistributerVersion());

        Importer importer = this.buildImporter();
        importer.importDistributorVersions(distVer);

        verify(this.mockDistributorVersionCurator).create(any(DistributorVersion.class));
        verify(this.mockDistributorVersionCurator, never()).merge(any(DistributorVersion.class));
    }

    @Test
    public void importDistributorVersionUpdate() throws Exception {
        doReturn(new DistributorVersion("test-dist-ver"))
            .when(this.mockDistributorVersionCurator)
            .findByName(eq("test-dist-ver"));

        File[] distVer = new File[1];
        distVer[0] = new File(this.tmpFolder, "dist-ver.json");
        mapper.writeValue(distVer[0], createTestDistributerVersion());

        Importer importer = this.buildImporter();
        importer.importDistributorVersions(distVer);

        verify(this.mockDistributorVersionCurator, never()).create(any(DistributorVersion.class));
        verify(this.mockDistributorVersionCurator).merge(any(DistributorVersion.class));
    }

    @Test
    public void testImportNoDistributorVersions() throws IOException, ImporterException {
        doNothing()
            .when(this.mockRulesImporter)
            .importObject(any(Reader.class));

        // this is the hook to stop testing. we confirm that the dist version null test
        //  is passed and then jump out instead of trying to fake the actual file
        //  processing.
        doThrow(new RuntimeException("Done with the test"))
            .when(this.mockExporterMetadataCurator)
            .getByTypeAndOwner(any(String.class), any(Owner.class));

        OwnerCurator oc = mock(OwnerCurator.class);
        Owner owner = mock(Owner.class);
        ConflictOverrides co = mock(ConflictOverrides.class);
        Map<String, File> importFiles = createAndSetImportFiles();

        Importer importer = this.buildImporter();
        Throwable throwable = assertThrows(RuntimeException.class,
            () -> importer.importObjects(owner, importFiles, co));

        String errmsg = "Done with the test";
        assertThat(throwable.getMessage(), StringContains.containsString(errmsg));
    }

    private Map<String, File> createAndSetImportFiles()
        throws IOException, JsonGenerationException, JsonMappingException {

        File[] rulesFiles = createMockJsFile(mockJsPath);
        File ruleDir = mock(File.class);
        when(ruleDir.listFiles()).thenReturn(rulesFiles);

        File actualmeta = createFile("meta.json", "0.0.3", new Date(), "test_user", "prefix");

        Map<String, File> importFiles = this.getTestImportFiles();
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
        Meta meta = new Meta("1.0", new Date(), "test-user", "candlepin", "testcdn");

        List<SubscriptionDTO> subscriptions = new ArrayList<>();
        SubscriptionDTO subscription = new SubscriptionDTO();
        subscriptions.add(subscription);

        Map<String, Object> data = new HashMap<>();
        data.put("meta", meta);
        data.put("subscriptions", subscriptions);

        Importer importer = this.buildImporter();
        ImportRecord record = importer.recordImportSuccess(owner, data, new ConflictOverrides(), "test.zip");

        assertEquals(record.getGeneratedBy(), meta.getPrincipalName());
        assertEquals(record.getGeneratedDate(), meta.getCreated());
        assertEquals(record.getStatus(), ImportRecord.Status.SUCCESS);
        assertEquals(record.getStatusMessage(), owner.getKey() + " file imported successfully.");
        assertEquals(record.getFileName(), "test.zip");

        verify(this.mockImportRecordCurator).create(eq(record));
        verify(this.mockEventSink, never()).emitSubscriptionExpired(subscription);
    }

    @Test
    public void testRecordImportSetsUpstreamConsumerFromOwner() {
        String expectedOwnerKey = "TEST_OWNER";
        Owner owner = new Owner(expectedOwnerKey);

        UpstreamConsumer uc = new UpstreamConsumer("uc", owner,
            new ConsumerType(ConsumerType.ConsumerTypeEnum.CANDLEPIN), "uuid");
        uc.setContentAccessMode("mode");
        owner.setUpstreamConsumer(uc);

        Meta meta = new Meta("1.0", new Date(), "test-user", "candlepin", "testcdn");

        Map<String, Object> data = new HashMap<>();
        data.put("meta", meta);
        data.put("subscriptions",  new ArrayList<Subscription>());

        Importer importer = this.buildImporter();
        ImportRecord record = importer.recordImportSuccess(owner, data, new ConflictOverrides(), "test.zip");

        ImportUpstreamConsumer iuc = record.getUpstreamConsumer();
        assertNotNull(iuc);
        assertEquals(iuc.getOwnerId(), uc.getOwnerId());
        assertEquals(iuc.getName(), uc.getName());
        assertEquals(iuc.getUuid(), uc.getUuid());
        assertEquals(iuc.getType(), uc.getType());
        assertEquals(iuc.getWebUrl(), uc.getWebUrl());
        assertEquals(iuc.getApiUrl(), uc.getApiUrl());
        assertEquals(iuc.getContentAccessMode(), uc.getContentAccessMode());

        verify(this.mockImportRecordCurator).create(eq(record));
    }

    @Test
    public void testRecordImportIgnoresUpstreamConsumerIfNotSetOnOwner() {
        String expectedOwnerKey = "TEST_OWNER";
        Owner owner = new Owner(expectedOwnerKey);

        Meta meta = new Meta("1.0", new Date(), "test-user", "candlepin", "testcdn");

        Map<String, Object> data = new HashMap<>();
        data.put("meta", meta);
        data.put("subscriptions",  new ArrayList<Subscription>());

        Importer importer = this.buildImporter();
        ImportRecord record = importer.recordImportSuccess(owner, data, new ConflictOverrides(), "test.zip");

        assertNull(record.getUpstreamConsumer());

        verify(this.mockImportRecordCurator).create(eq(record));
    }

    @Test
    public void testRecordImportExpiredSubsFound() {
        String expectedOwnerKey = "TEST_OWNER";
        Owner owner = new Owner(expectedOwnerKey);

        Map<String, Object> data = new HashMap<>();
        List<SubscriptionDTO> subscriptions = new ArrayList<>();

        //expires tomorrow
        SubscriptionDTO subscription1 = new SubscriptionDTO();
        subscription1.setEndDate(new Date((new Date()).getTime() + (1000 * 60 * 60 * 24)));
        subscriptions.add(subscription1);

        //expires yesterday
        SubscriptionDTO subscription2 = new SubscriptionDTO();
        subscription2.setEndDate(new Date((new Date()).getTime() - (1000 * 60 * 60 * 24)));
        subscriptions.add(subscription2);
        data.put("subscriptions", subscriptions);

        Importer importer = this.buildImporter();
        ImportRecord record = importer.recordImportSuccess(owner, data, new ConflictOverrides(), "test.zip");

        assertEquals(record.getStatus(), ImportRecord.Status.SUCCESS_WITH_WARNING);
        assertEquals(record.getStatusMessage(), owner.getKey() + " file imported successfully." +
            "One or more inactive subscriptions found in the file.");

        verify(this.mockEventSink, never()).emitSubscriptionExpired(subscription1);
        verify(this.mockEventSink).emitSubscriptionExpired(subscription2);
        verify(this.mockImportRecordCurator).create(eq(record));
    }

    @Test
    public void testRecordImportNoActiveSubsFound() {
        String expectedOwnerKey = "TEST_OWNER";
        Owner owner = new Owner(expectedOwnerKey);

        Map<String, Object> data = new HashMap<>();
        data.put("subscriptions", new ArrayList<SubscriptionDTO>());

        Importer importer = this.buildImporter();
        ImportRecord record = importer.recordImportSuccess(owner, data, new ConflictOverrides(), "test.zip");

        assertEquals(ImportRecord.Status.SUCCESS_WITH_WARNING, record.getStatus());
        assertEquals(owner.getKey() + " file imported successfully." +
            "No active subscriptions found in the file.", record.getStatusMessage());

        verify(this.mockEventSink, never()).emitSubscriptionExpired(any(SubscriptionDTO.class));
        verify(this.mockImportRecordCurator).create(eq(record));
    }

}
