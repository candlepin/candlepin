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
package org.candlepin.sync;

import org.candlepin.model.Certificate;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Certificate exporter
 */
public class CertificateExporter {

    void exportCertificate(Certificate cert, File file) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(cert.getCert());
            writer.write(cert.getKey());
        }
        catch (IOException ioExp) {
            throw new IOException("Error occurred while exporting certificates", ioExp);
        }

    }
}
