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
package org.candlepin.common.config;

import static org.hamcrest.MatcherAssert.*;
import static org.junit.jupiter.api.Assertions.*;

import org.hamcrest.core.IsInstanceOf;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Properties;


public class PropertiesFileConfigurationTest {

    private static final String UTF8_FILE = "config/utf8.properties";
    private static final String ASCII_FILE = "config/ascii.properties";

    // Use StandardCharsets.UTF_8/US_ASCII when we move to Java 7
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final Charset ASCII = Charset.forName("US-ASCII");

    private ClassLoader cl;
    private PropertiesFileConfiguration config;

    @BeforeEach
    public void init() {
        cl = this.getClass().getClassLoader();
        config = new PropertiesFileConfiguration();
    }

    @Test
    public void testLoadInputStreamWithSpecificEncoding() throws Exception {
        InputStream utf8Stream = cl.getResourceAsStream(UTF8_FILE);

        config.setEncoding(ASCII);
        config.load(utf8Stream);

        assertNotEquals("Motörhead", config.getString("great.band"));
        assertEquals(new String("Motörhead".getBytes(), ASCII), config.getString("great.band"));
    }

    @Test
    public void testLoadReader() throws Exception {
        String file = cl.getResource(UTF8_FILE).getFile();
        Reader r = new FileReader(file);

        config.setEncoding(UTF8);
        config.load(r);

        assertEquals("Blue Öyster Cult", config.getString("cowbell.band"));
    }

    @Test
    public void testLoadProperties() throws Exception {
        Properties p = new Properties();
        p.load(cl.getResourceAsStream(ASCII_FILE));

        config.setEncoding(UTF8);
        config.load(p);
        assertEquals(Arrays.asList("chocolate chip", "oatmeal", "peanut butter"),
            config.getList("cookies"));
        assertEquals("chocolate chip, oatmeal, peanut butter", config.getString("cookies"));
    }

    @Test
    public void testLoadMultiline() throws Exception {
        Properties p = new Properties();
        p.load(cl.getResourceAsStream(ASCII_FILE));

        config.setEncoding(UTF8);
        config.load(p);

        String poem = "The Assyrian came down like the wolf on the fold," +
            "And his cohorts were gleaming in purple and gold;" +
            "And the sheen of their spears was like stars on the sea," +
            "When the blue wave rolls nightly on deep Galilee.";
        assertEquals(poem, config.getString("poem"));
    }

    @Test
    public void testLoadFile() throws Exception {
        String file = cl.getResource(UTF8_FILE).getFile();
        config = new PropertiesFileConfiguration(file, ASCII);
        assertNotEquals("Mötley Crüe", config.getString("glam.band"));
        assertEquals(new String("Mötley Crüe".getBytes(), ASCII), config.getString("glam.band"));
    }

    @Test
    public void testEmptyReader() throws Exception {
        Throwable t = assertThrows(ConfigurationException.class,
            () -> config.load(new File("/does/not/exist")));

        assertThat(t.getCause(), IsInstanceOf.instanceOf(FileNotFoundException.class));
    }
}
