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

import org.xnap.commons.i18n.I18n;

import tools.jackson.databind.JsonNode;

import java.util.Objects;

import jakarta.inject.Inject;

/**
 * Manifest format selection from raw metadata, independent of the producing Candlepin version
 */
public class ManifestFormatDetector {
    private final I18n i18n;

    /**
     * Initializes the format detector
     *
     * @param i18n
     *     the import error message translator
     */
    @Inject
    public ManifestFormatDetector(I18n i18n) {
        this.i18n = Objects.requireNonNull(i18n);
    }

    /**
     * Recognition of supported v2 metadata
     * <p>
     * Only an absent manifestVersion selects v1. Explicit null, non-string, and unsupported values are errors.
     *
     * @param metadata
     *     the raw meta.json object
     * @throws SyncDataFormatException
     *     if metadata is not an object or declares an unsupported format
     * @return
     *     true for manifestVersion 2.0.0; false for v1 metadata
     */
    public boolean isV2(JsonNode metadata) throws SyncDataFormatException {
        if (metadata == null || !metadata.isObject()) {
            throw new SyncDataFormatException(this.i18n.tr("Manifest metadata must be a JSON object"));
        }

        JsonNode version = metadata.get("manifestVersion");
        if (version == null) {
            return false;
        }
        if (!version.isString() || !"2.0.0".equals(version.asString())) {
            throw new SyncDataFormatException(this.i18n.tr("Unsupported manifest format version"));
        }
        return true;
    }
}
