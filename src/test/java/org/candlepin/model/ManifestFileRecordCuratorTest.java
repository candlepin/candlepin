/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.sync.file.ManifestFileType;
import org.candlepin.test.DatabaseTestFixture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Calendar;



public class ManifestFileRecordCuratorTest extends DatabaseTestFixture {

    private File tempFile;
    private ManifestFileRecord record;

    @BeforeEach
    public void setupTest() throws Exception {
        Path temp = Files.createTempFile("test-manifest", ".zip");
        tempFile = temp.toFile();
        record = manifestFileRecordCurator.createFile(ManifestFileType.EXPORT, tempFile,
            "principalId", "ownerId");
    }

    @AfterEach
    public void tearDownTest() {
        tempFile.delete();
    }

    @Test
    public void testDeleteById() throws Exception {
        assertNotNull(record);
        assertTrue(manifestFileRecordCurator.deleteById(record.getId()));
    }

    @Test
    public void testFindFile() throws Exception {
        assertNotNull(manifestFileRecordCurator.findFile(record.getId()));
    }

    @Test
    public void testDeleteExpired() throws Exception {
        Calendar cal = Calendar.getInstance();
        manifestFileRecordCurator.createFile(ManifestFileType.EXPORT, tempFile, "principalId", "ownerId");

        cal.add(Calendar.HOUR_OF_DAY, -4);
        assertEquals(0, manifestFileRecordCurator.deleteExpired(cal.getTime()));
        cal.add(Calendar.HOUR_OF_DAY, 8);
        assertEquals(2, manifestFileRecordCurator.deleteExpired(cal.getTime()));
    }
}
