/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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

import org.candlepin.pki.Scheme;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateEncodingException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Generates a signature file
 */
public class SignatureFileExporter {
    private ObjectMapper objectMapper;

    @Inject
    public SignatureFileExporter(@Named("ExportObjectMapper") ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Adds a signature file as a {@link ZipEntry} to the provided zip file.
     *
     * @param outputStream
     *  the zip output stream to write to; cannot be null
     *
     * @param scheme
     *  the scheme used for generating the signature file; cannot be null
     *
     * @param signature
     *  the signature to use for the signature file; cannot be null
     *
     * @throws IllegalArgumentException
     *  if the provided output stream, scheme, or signature is null
     *
     * @throws CertificateEncodingException
     *  if unable to retrieve the encoded X.509 certificate from the scheme
     *
     * @throws IOException
     *  if unable to write the signature file to the zip file
     */
    public void export(ZipOutputStream outputStream, Scheme scheme, byte[] signature)
        throws CertificateEncodingException, IOException {

        if (outputStream == null) {
            throw new IllegalArgumentException("output stream is null");
        }

        if (scheme == null) {
            throw new IllegalArgumentException("scheme is null");
        }

        if (signature == null) {
            throw new IllegalArgumentException("signature is null");
        }

        SignatureFile payload = SignatureFile.from(scheme, signature);

        try {
            String content = this.objectMapper.writeValueAsString(payload);
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            outputStream.putNextEntry(new ZipEntry(SignatureFile.FILENAME));
            outputStream.write(contentBytes, 0, contentBytes.length);
            outputStream.closeEntry();
        }
        catch (JsonProcessingException e) {
            throw new IOException("Unable to serialize or write signature file", e);
        }
    }

}

