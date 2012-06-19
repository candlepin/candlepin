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
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;


import org.candlepin.config.Config;
import org.candlepin.exceptions.ConflictException;
import org.candlepin.model.ExporterMetadata;
import org.candlepin.model.ExporterMetadataCurator;
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
        i.validateMetadata(ExporterMetadata.TYPE_SYSTEM, null, actual, false);

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
        i.validateMetadata(ExporterMetadata.TYPE_SYSTEM, null, actualmeta, false);
        assertTrue(f.delete());
        assertTrue(actualmeta.delete());
        verify(emc).create(any(ExporterMetadata.class));
    }

    @Test(expected = ConflictException.class)
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
        i.validateMetadata(ExporterMetadata.TYPE_SYSTEM, null, actualmeta, false);
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
        i.validateMetadata(ExporterMetadata.TYPE_SYSTEM, null, actualmeta, false);
        assertEquals(importDate, em.getExported());
    }

    @Test
    public void newerVersionImport() throws Exception {
        // if we do are importing candlepin 0.0.10 data into candlepin 0.0.3,
        // import the rules.

        File actualmeta = createFile("/tmp/meta.json", "0.0.10", new Date(),
            "test_user", "prefix");
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
        File actualmeta = createFile("/tmp/meta.json", "0.0.1", new Date(),
            "test_user", "prefix");
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
        File actualmeta = createFile("/tmp/meta.json", "0.0.3", new Date(),
            "test_user", "prefix");
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
        File actualmeta = createFile("/tmp/meta.json", "0.0.3", new Date(),
            "test_user", "prefix");
        ExporterMetadataCurator emc = mock(ExporterMetadataCurator.class);
        when(emc.lookupByTypeAndOwner(ExporterMetadata.TYPE_PER_USER, null))
            .thenReturn(null);

        Importer i = new Importer(null, null, null, null, null, null, null,
            null, null, emc, null, null, i18n);

        // null Type should cause exception
        i.validateMetadata(ExporterMetadata.TYPE_PER_USER, null, actualmeta, false);
        verify(emc, never()).create(any(ExporterMetadata.class));
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

}
