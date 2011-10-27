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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.model.ExporterMetadata;
import org.candlepin.model.ExporterMetadataCurator;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

import org.candlepin.config.Config;
import org.candlepin.exceptions.ConflictException;


/**
 * ImporterTest
 */
public class ImporterTest {

    private ObjectMapper mapper;
    private I18n i18n;

    @Before
    public void init() {
        mapper = SyncUtils.getObjectMapper(new Config(new HashMap<String, String>()));
        i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
    }

    @Test
    public void validateMetaJson() throws Exception {
        /* read file
         *  read in version
         *  read in created date
         * make sure created date is XYZ
         * make sure version is > ABC
         */

        File f = createFile("/tmp/meta");
        File actualmeta = createFile("/tmp/meta.json");
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
        File f = createFile("/tmp/meta");
        File actualmeta = createFile("/tmp/meta.json");
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
        File actualmeta = createFile("/tmp/meta.json");
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

    @Test(expected = ImporterException.class)
    public void nullType() throws ImporterException, IOException {
        File actualmeta = createFile("/tmp/meta.json");
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
        File actualmeta = createFile("/tmp/meta.json");
        ExporterMetadataCurator emc = mock(ExporterMetadataCurator.class);
        when(emc.lookupByTypeAndOwner(ExporterMetadata.TYPE_PER_USER, null))
            .thenReturn(null);

        Importer i = new Importer(null, null, null, null, null, null, null,
            null, null, emc, null, null, i18n);

        // null Type should cause exception
        i.validateMetadata(ExporterMetadata.TYPE_PER_USER, null, actualmeta, false);
        verify(emc, never()).create(any(ExporterMetadata.class));
    }

    private File createFile(String filename)
        throws JsonGenerationException, JsonMappingException, IOException {

        File f = new File(filename);
        Meta meta = new Meta("0.0.0", new Date());
        mapper.writeValue(f, meta);
        return f;
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
