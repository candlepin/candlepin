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

        Importer i = new Importer(null, null, null, null, null, null, null, null);
        i.validateMetaJson(actualmeta);

        assertTrue(f.delete());
        assertTrue(actualmeta.delete());
    }

    @Test(expected = ImporterException.class)
    public void expectException() throws Exception {
        // create actual first
        File actualmeta = createFile("/tmp/meta.json");
        File f = createFile("/tmp/meta");

        Importer i = new Importer(null, null, null, null, null, null, null, null);
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
}
