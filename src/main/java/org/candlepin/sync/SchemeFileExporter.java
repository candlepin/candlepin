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

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.Writer;
import java.security.cert.CertificateEncodingException;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Generates a scheme file
 */
public class SchemeFileExporter {
    private ObjectMapper objectMapper;

    @Inject
    public SchemeFileExporter(@Named("ExportObjectMapper") ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Exports a {@link SchemeFile} using the provided writer.
     *
     * @param writer
     *  the writer used to export the scheme file
     *
     * @param scheme
     *  the scheme used for generating the scheme file; cannot be null
     *
     * @throws IllegalArgumentException
     *  if the provided writer or scheme is null
     *
     * @throws CertificateEncodingException
     *  if unable to retrieve the encoded X.509 certificate from the scheme
     *
     * @throws IOException
     *  if unable to write the scheme file
     */
    public void export(Writer writer, Scheme scheme)
        throws CertificateEncodingException, IOException {

        if (writer == null) {
            throw new IllegalArgumentException("writer is null");
        }

        if (scheme == null) {
            throw new IllegalArgumentException("scheme is null");
        }

        SchemeFile payload = SchemeFile.from(scheme);

        try {
            this.objectMapper.writeValue(writer, payload);
        }
        catch (JacksonException e) {
            throw new IOException("Unable to serialize or write signature file", e);
        }
    }

}

