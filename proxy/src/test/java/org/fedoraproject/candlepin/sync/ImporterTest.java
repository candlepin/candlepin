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

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.fedoraproject.candlepin.model.ExporterMetadata;
import org.fedoraproject.candlepin.model.ExporterMetadataCurator;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Date;


/**
 * ImporterTest
 */
public class ImporterTest {

    private ObjectMapper mapper;

    @Before
    public void init() {
        mapper = SyncUtils.getObjectMapper();
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
        em.setId(42L);
        em.setType(ExporterMetadata.TYPE_METADATA);
        when(emc.lookupByType(ExporterMetadata.TYPE_METADATA)).thenReturn(em);
        Importer i = new Importer(null, null, null, null, null, null, null,
            null, null, emc);
        i.validateMetaJson(actualmeta);

        assertTrue(f.delete());
        assertTrue(actualmeta.delete());
        assertTrue(daybefore.compareTo(em.getExported()) < 0);
    }

    @Test
    public void firstRun() throws Exception {
        File f = createFile("/tmp/meta");
        File actualmeta = createFile("/tmp/meta.json");
        ExporterMetadataCurator emc = mock(ExporterMetadataCurator.class);
        when(emc.lookupByType(ExporterMetadata.TYPE_METADATA)).thenReturn(null);
        Importer i = new Importer(null, null, null, null, null, null, null,
            null, null, emc);
        i.validateMetaJson(actualmeta);
        assertTrue(f.delete());
        assertTrue(actualmeta.delete());
        verify(emc).create(any(ExporterMetadata.class));
    }

    @Test(expected = ImporterException.class)
    public void expectException() throws Exception {
        // create actual first
        File actualmeta = createFile("/tmp/meta.json");
        File f = createFile("/tmp/meta");
        ExporterMetadataCurator emc = mock(ExporterMetadataCurator.class);
        ExporterMetadata em = new ExporterMetadata();
        em.setCreated(getDateAfterDays(1));
        em.setId(42L);
        em.setType(ExporterMetadata.TYPE_METADATA);
        when(emc.lookupByType(ExporterMetadata.TYPE_METADATA)).thenReturn(em);
        Importer i = new Importer(null, null, null, null, null, null, null,
            null, null, emc);
        i.validateMetaJson(actualmeta);

        assertTrue(f.delete());
        assertTrue(actualmeta.delete());
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
