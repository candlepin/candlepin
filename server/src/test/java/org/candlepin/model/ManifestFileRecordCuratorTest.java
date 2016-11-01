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
package org.candlepin.model;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Calendar;

import org.candlepin.sync.file.ManifestFileType;
import org.candlepin.test.DatabaseTestFixture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.Inject;

public class ManifestFileRecordCuratorTest extends DatabaseTestFixture {

    @Inject private ManifestFileRecordCurator curator;
    private File tempFile;
    private ManifestFileRecord record;

    @Before
    public void setupTest() throws Exception {
        Path temp = Files.createTempFile("test-manifest", ".zip");
        tempFile = temp.toFile();
        record = curator.createFile(ManifestFileType.EXPORT, tempFile,
            "principalId", "ownerId");
    }

    @After
    public void tearDownTest() {
        tempFile.delete();
    }

    @Test
    public void testDeleteById() throws Exception {
        assertNotNull(record);
        assertTrue(curator.deleteById(record.getId()));
    }

    @Test
    public void testFindFile() throws Exception {
        assertNotNull(curator.findFile(record.getId()));
    }

    @Test
    public void testDeleteExpired() throws Exception {
        Calendar cal = Calendar.getInstance();
        curator.createFile(ManifestFileType.EXPORT, tempFile, "principalId", "ownerId");

        cal.add(Calendar.HOUR_OF_DAY, -4);
        assertEquals(0, curator.deleteExpired(cal.getTime()));
        cal.add(Calendar.HOUR_OF_DAY, 8);
        assertEquals(2, curator.deleteExpired(cal.getTime()));
    }
}
