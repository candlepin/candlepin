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
package org.candlepin.resource.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.candlepin.test.TestUtil;

import org.jboss.resteasy.core.providerfactory.ResteasyProviderFactoryImpl;
import org.jboss.resteasy.plugins.providers.FileProvider;
import org.jboss.resteasy.plugins.providers.InputStreamProvider;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInputImpl;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.Providers;



public class AttachedFileTest {

    private Providers buildProviderFactory() {
        ResteasyProviderFactory factory = new ResteasyProviderFactoryImpl();

        // TODO: Add more providers here as necessary for testing. At the time of writing, we only
        // need the FileProvider and InputStreamProvider
        factory.registerProvider(FileProvider.class);
        factory.registerProvider(InputStreamProvider.class);

        return factory;
    }

    private MultipartInput assembleMultipartInput(String input, String boundary) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(input.getBytes());

        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("boundary", boundary);

        MediaType contentType = new MediaType("multipart", "form-data", parameters);
        MultipartInputImpl multipart = new MultipartInputImpl(contentType, this.buildProviderFactory());
        multipart.parse(bais);

        return multipart;
    }

    private MultipartInput buildMultipartInput(String sourcePath, String... chunks) throws IOException {
        String boundary = TestUtil.randomString(30);
        String glue = "--" + boundary + "\r\n";

        String input = "URLSTR: " + sourcePath + "\r\n" +
            glue +
            String.join(glue, chunks) +
            glue;

        return this.assembleMultipartInput(input, boundary);
    }

    private String buildInputChunk(String name, String filename, String data) {
        StringBuilder builder = new StringBuilder()
            .append("Content-Disposition: form-data");

        if (name != null) {
            builder.append("; name=\"")
                .append(name)
                .append("\"");
        }

        if (filename != null) {
            builder.append("; filename=\"")
                .append(filename)
                .append("\"");
        }

        builder.append("\r\n")
            .append("Content-Type: text/plain; charset=US-ASCII\r\n")
            .append("Content-Transfer-Encoding: 8bit\r\n")
            .append("\r\n");

        if (data != null) {
            builder.append(data)
                .append("\r\n");
        }

        return builder.toString();
    }

    private String readInputStream(InputStream istream) throws IOException {
        // 32 character buffer; could be larger, but (probably) won't matter for our scope
        char[] buffer = new char[32];

        // The string builder that will assemble our output
        StringBuilder builder = new StringBuilder();

        try (InputStreamReader reader = new InputStreamReader(istream)) {
            while (true) {
                int read = reader.read(buffer);

                if (read == -1) {
                    break;
                }

                builder.append(buffer, 0, read);
            }
        }

        return builder.toString();
    }

    private String readFileData(File file) throws IOException {
        try (FileInputStream istream = new FileInputStream(file)) {
            return this.readInputStream(istream);
        }
    }

    private void verifyAttachedFile(AttachedFile attached, String filename, String expectedData)
        throws IOException {

        assertNotNull(attached);

        String defaultFilename = "default filename";
        if (filename != null) {
            assertEquals(filename, attached.getFilename());
            assertEquals(filename, attached.getFilename(defaultFilename));
        }
        else {
            assertEquals(null, attached.getFilename());
            assertEquals(defaultFilename, attached.getFilename(defaultFilename));
        }

        File file = attached.getFile();
        assertNotNull(file);

        String filedata = this.readFileData(file);
        assertEquals(expectedData, filedata);

        InputStream istream = attached.getInputStream();
        assertNotNull(istream);

        String streamdata = this.readInputStream(istream);
        assertEquals(expectedData, streamdata);
        istream.close();
    }

    @Test
    public void testGetAttachedFileWithNoIndexGetsFirstPartOfSinglePartInput() throws Exception {
        String chunk = this.buildInputChunk("part1", "part1.txt", "this is part 1");
        MultipartInput input = this.buildMultipartInput("/path/to/my/upload.txt", chunk);

        AttachedFile attached = AttachedFile.getAttachedFile(input);

        this.verifyAttachedFile(attached, "part1.txt", "this is part 1");
    }

    @Test
    public void testGetAttachedFileWithNoIndexGetsFirstPartOfMultiPartInput() throws Exception {
        String chunk1 = this.buildInputChunk("part1", "part1.txt", "this is part 1");
        String chunk2 = this.buildInputChunk("part2", "part2.txt", "this is part 2");
        MultipartInput input = this.buildMultipartInput("/path/to/my/upload.txt", chunk1, chunk2);

        AttachedFile attached = AttachedFile.getAttachedFile(input);

        this.verifyAttachedFile(attached, "part1.txt", "this is part 1");
    }

    @Test
    public void testGetAttachedFileWithIndexGetsRequestedPartOfMultiPartInput() throws Exception {
        String chunk1 = this.buildInputChunk("part1", "part1.txt", "this is part 1");
        String chunk2 = this.buildInputChunk("part2", "part2.txt", "this is part 2");
        String chunk3 = this.buildInputChunk("part3", "part3.txt", "this is part 3");
        MultipartInput input = this.buildMultipartInput("/path/to/my/upload.txt", chunk1, chunk2, chunk3);

        AttachedFile attached = AttachedFile.getAttachedFile(input, 1);

        this.verifyAttachedFile(attached, "part2.txt", "this is part 2");
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(ints = { -30, -1, 30 })
    public void testGetAttachedFileWithInvalidIndexThrowsException(int index) throws Exception {
        String chunk1 = this.buildInputChunk("part1", "part1.txt", "this is part 1");
        String chunk2 = this.buildInputChunk("part2", "part2.txt", "this is part 2");
        String chunk3 = this.buildInputChunk("part3", "part3.txt", "this is part 3");
        MultipartInput input = this.buildMultipartInput("/path/to/my/upload.txt", chunk1, chunk2, chunk3);

        assertThrows(IndexOutOfBoundsException.class, () -> AttachedFile.getAttachedFile(input, index));
    }

    @Test
    public void testGetAttachedFilesWithSinglePartInput() throws Exception {
        String chunk = this.buildInputChunk("part1", "part1.txt", "this is part 1");
        MultipartInput input = this.buildMultipartInput("/path/to/my/upload.txt", chunk);

        List<AttachedFile> attachedList = AttachedFile.getAttachedFiles(input);
        assertNotNull(attachedList);
        assertEquals(1, attachedList.size());

        this.verifyAttachedFile(attachedList.get(0), "part1.txt", "this is part 1");
    }

    @Test
    public void testGetAttachedFilesWithMultiPartInput() throws Exception {
        String chunk1 = this.buildInputChunk("part1", "part1.txt", "this is part 1");
        String chunk2 = this.buildInputChunk("part2", "part2.txt", "this is part 2");
        String chunk3 = this.buildInputChunk("part3", "part3.txt", "this is part 3");
        MultipartInput input = this.buildMultipartInput("/path/to/my/upload.txt", chunk1, chunk2, chunk3);

        List<AttachedFile> attachedList = AttachedFile.getAttachedFiles(input);
        assertNotNull(attachedList);
        assertEquals(3, attachedList.size());

        // These should be received in the order they're defined in the request
        this.verifyAttachedFile(attachedList.get(0), "part1.txt", "this is part 1");
        this.verifyAttachedFile(attachedList.get(1), "part2.txt", "this is part 2");
        this.verifyAttachedFile(attachedList.get(2), "part3.txt", "this is part 3");
    }

}
