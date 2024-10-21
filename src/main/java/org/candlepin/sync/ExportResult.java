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

import java.io.Serializable;

/**
 * Represents the result from an async export job. This class simply defines
 * the appropriate meta data to create a link to download the manifest.
 */
public class ExportResult implements Serializable {

    private static final long serialVersionUID = -352738548332088053L;

    private String exportedConsumer;
    private String exportId;
    private String href;

    public ExportResult(String exportedConsumer, String exportId) {
        this.exportedConsumer = exportedConsumer;
        this.exportId = exportId;

        this.href = buildAsyncDownloadManifestHref(this.exportedConsumer, this.exportId);
    }

    /**
     * Builds an HREF to a stored manifest file.
     *
     * @param consumerUuid the target consumer UUID.
     * @param manifestId the target manifest ID.
     * @return the HREF string for the specified manifest
     */
    private static String buildAsyncDownloadManifestHref(String consumerUuid, String manifestId) {
        return String.format("/consumers/%s/export/%s", consumerUuid, manifestId);
    }

    public String getExportedConsumer() {
        return exportedConsumer;
    }

    public String getExportId() {
        return exportId;
    }

    public String getHref() {
        return this.href;
    }

    @Override
    public String toString() {
        return "Export for consumer " + exportedConsumer;
    }
}
