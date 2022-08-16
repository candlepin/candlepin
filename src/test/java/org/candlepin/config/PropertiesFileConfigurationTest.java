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
package org.candlepin.config;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.hamcrest.core.IsInstanceOf;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;



public class PropertiesFileConfigurationTest {
    private static final String UTF8_FILE = "config/utf8.properties";
    private static final String ASCII_FILE = "config/ascii.properties";

    private ClassLoader getClassLoader() {
        return this.getClass().getClassLoader();
    }

    private String getResourceAsFile(String filename) {
        return this.getClassLoader()
            .getResource(filename)
            .getFile();
    }

    private InputStream getResourceAsStream(String filename) {
        return this.getClassLoader()
            .getResourceAsStream(filename);
    }

    @Test
    public void testGetDefaultCharset() {
        PropertiesFileConfiguration config = new PropertiesFileConfiguration();

        assertEquals(UTF_8, config.getDefaultCharset());
    }

    @Test
    public void testLoadFileByNameWithDefaultCharset() throws Exception {
        PropertiesFileConfiguration config = new PropertiesFileConfiguration();
        String filename = this.getResourceAsFile(UTF8_FILE);

        config.load(filename);

        assertEquals("Mötley Crüe", config.getString("glam.band"));
        assertNotEquals(new String("Mötley Crüe".getBytes(UTF_8), US_ASCII), config.getString("glam.band"));
    }

    @Test
    public void testLoadFileByNameWithSpecificCharset() throws Exception {
        PropertiesFileConfiguration config = new PropertiesFileConfiguration();
        String filename = this.getResourceAsFile(UTF8_FILE);

        config.load(filename, UTF_8);
        assertEquals("Mötley Crüe", config.getString("glam.band"));
        assertNotEquals(new String("Mötley Crüe".getBytes(UTF_8), US_ASCII), config.getString("glam.band"));

        config.load(filename, US_ASCII);
        assertNotEquals("Mötley Crüe", config.getString("glam.band"));
        assertEquals(new String("Mötley Crüe".getBytes(UTF_8), US_ASCII), config.getString("glam.band"));
    }

    @Test
    public void testLoadFileByNameFailsOnNullFile() throws Exception {
        PropertiesFileConfiguration config = new PropertiesFileConfiguration();
        assertThrows(IllegalArgumentException.class, () -> config.load((String) null));
    }

    @Test
    public void testLoadFileByNameFailsOnNullFileWithCharset() throws Exception {
        PropertiesFileConfiguration config = new PropertiesFileConfiguration();
        assertThrows(IllegalArgumentException.class, () -> config.load((String) null, UTF_8));
        assertThrows(IllegalArgumentException.class, () -> config.load((String) null, US_ASCII));
        assertThrows(IllegalArgumentException.class, () -> config.load((String) null, null));
    }

    @Test
    public void testLoadFileByNameFailsOnBadFile() throws Exception {
        PropertiesFileConfiguration config = new PropertiesFileConfiguration();
        String filename = "/does/not/exist";

        Throwable t = assertThrows(ConfigurationException.class, () -> config.load(filename));
        assertThat(t.getCause(), IsInstanceOf.instanceOf(FileNotFoundException.class));
    }

    @Test
    public void testLoadFileWithDefaultCharset() throws Exception {
        PropertiesFileConfiguration config = new PropertiesFileConfiguration();
        String filename = this.getResourceAsFile(UTF8_FILE);
        File file = new File(filename);

        config.load(file);

        assertEquals("Mötley Crüe", config.getString("glam.band"));
        assertNotEquals(new String("Mötley Crüe".getBytes(UTF_8), US_ASCII), config.getString("glam.band"));
    }

    @Test
    public void testLoadFileWithSpecificCharset() throws Exception {
        PropertiesFileConfiguration config = new PropertiesFileConfiguration();
        String filename = this.getResourceAsFile(UTF8_FILE);
        File file = new File(filename);

        config.load(file, UTF_8);
        assertEquals("Mötley Crüe", config.getString("glam.band"));
        assertNotEquals(new String("Mötley Crüe".getBytes(UTF_8), US_ASCII), config.getString("glam.band"));

        config.load(file, US_ASCII);
        assertNotEquals("Mötley Crüe", config.getString("glam.band"));
        assertEquals(new String("Mötley Crüe".getBytes(UTF_8), US_ASCII), config.getString("glam.band"));
    }

    @Test
    public void testLoadFileFailsOnNullFile() throws Exception {
        PropertiesFileConfiguration config = new PropertiesFileConfiguration();
        assertThrows(IllegalArgumentException.class, () -> config.load((File) null));
    }

    @Test
    public void testLoadFileFailsOnNullFileWithCharset() throws Exception {
        PropertiesFileConfiguration config = new PropertiesFileConfiguration();
        assertThrows(IllegalArgumentException.class, () -> config.load((File) null, UTF_8));
        assertThrows(IllegalArgumentException.class, () -> config.load((File) null, US_ASCII));
        assertThrows(IllegalArgumentException.class, () -> config.load((File) null, null));
    }

    @Test
    public void testLoadFileFailsOnBadFile() throws Exception {
        PropertiesFileConfiguration config = new PropertiesFileConfiguration();
        File file = new File("/does/not/exist");

        Throwable t = assertThrows(ConfigurationException.class, () -> config.load(file));
        assertThat(t.getCause(), IsInstanceOf.instanceOf(FileNotFoundException.class));
    }

    @Test
    public void testLoadInputStreamWithDefaultCharset() throws Exception {
        PropertiesFileConfiguration config = new PropertiesFileConfiguration();
        InputStream stream = this.getResourceAsStream(UTF8_FILE);

        config.load(stream);

        assertEquals("Motörhead", config.getString("great.band"));
        assertNotEquals(new String("Motörhead".getBytes(UTF_8), US_ASCII), config.getString("great.band"));
    }

    @Test
    public void testLoadInputStreamWithSpecificCharset() throws Exception {
        PropertiesFileConfiguration config = new PropertiesFileConfiguration();
        InputStream utf8stream = this.getResourceAsStream(UTF8_FILE);

        config.load(utf8stream, UTF_8);
        assertEquals("Motörhead", config.getString("great.band"));
        assertNotEquals(new String("Motörhead".getBytes(UTF_8), US_ASCII), config.getString("great.band"));

        InputStream asciiStream = this.getResourceAsStream(UTF8_FILE);

        config.load(asciiStream, US_ASCII);
        assertNotEquals("Motörhead", config.getString("great.band"));
        assertEquals(new String("Motörhead".getBytes(UTF_8), US_ASCII), config.getString("great.band"));
    }

    @Test
    public void testLoadReaderFailsOnNullInputStream() throws Exception {
        PropertiesFileConfiguration config = new PropertiesFileConfiguration();
        assertThrows(IllegalArgumentException.class, () -> config.load((InputStream) null));
    }

    @Test
    public void testLoadReaderFailsOnNullInputStreamWithCharset() throws Exception {
        PropertiesFileConfiguration config = new PropertiesFileConfiguration();
        assertThrows(IllegalArgumentException.class, () -> config.load((InputStream) null, UTF_8));
        assertThrows(IllegalArgumentException.class, () -> config.load((InputStream) null, US_ASCII));
        assertThrows(IllegalArgumentException.class, () -> config.load((InputStream) null, null));
    }

    @Test
    public void testLoadReader() throws Exception {
        PropertiesFileConfiguration config = new PropertiesFileConfiguration();
        InputStream stream = this.getResourceAsStream(UTF8_FILE);

        Reader reader = new InputStreamReader(stream, UTF_8);
        config.load(reader);

        assertEquals("Blue Öyster Cult", config.getString("cowbell.band"));
    }

    @Test
    public void testLoadReaderFailsOnNullReader() throws Exception {
        PropertiesFileConfiguration config = new PropertiesFileConfiguration();
        assertThrows(IllegalArgumentException.class, () -> config.load((Reader) null));
    }

    @Test
    public void testLoadReaderDoesNotApplyDefaultCharset() throws Exception {
        PropertiesFileConfiguration config = new PropertiesFileConfiguration();
        InputStream utf8Stream = this.getResourceAsStream(UTF8_FILE);

        Reader reader = new InputStreamReader(utf8Stream, US_ASCII);
        config.load(reader);

        // We're forcing ASCII encoding, so the encoding of the unicode characters should be busted
        assertNotEquals("Motörhead", config.getString("great.band"));
        assertEquals(new String("Motörhead".getBytes(UTF_8), US_ASCII), config.getString("great.band"));
    }

    @Test
    public void testLoadMultiline() throws Exception {
        PropertiesFileConfiguration config = new PropertiesFileConfiguration();
        config.load(this.getResourceAsFile(ASCII_FILE));

        String poem = "The Assyrian came down like the wolf on the fold," +
            "And his cohorts were gleaming in purple and gold;" +
            "And the sheen of their spears was like stars on the sea," +
            "When the blue wave rolls nightly on deep Galilee.";

        assertEquals(poem, config.getString("poem"));
    }
}
