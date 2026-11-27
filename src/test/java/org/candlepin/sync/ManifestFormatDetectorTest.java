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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import tools.jackson.databind.ObjectMapper;

import java.util.Locale;

class ManifestFormatDetectorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final I18n i18n = I18nFactory.getI18n(this.getClass(), Locale.US, I18nFactory.FALLBACK);
    private final ManifestFormatDetector detector = new ManifestFormatDetector(this.i18n);

    @Test
    void recognizesExplicitV2() throws Exception {
        assertTrue(this.detector.isV2(this.mapper.readTree("{\"manifestVersion\":\"2.0.0\",\"version\":\"6.0.0\"}")));
    }

    @Test
    void absentFormatIsV1RegardlessOfProducerVersion() throws Exception {
        assertFalse(this.detector.isV2(this.mapper.readTree("{\"version\":\"2.0.0\"}")));
        assertFalse(this.detector.isV2(this.mapper.readTree("{}")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "true", "{\"manifestVersion\":null}",
        "{\"manifestVersion\":2}", "{\"manifestVersion\":{}}", "{\"manifestVersion\":\"\"}",
        "{\"manifestVersion\":\"1.0.0\"}", "{\"manifestVersion\":\"3.0.0\"}"})
    void rejectsMalformedOrUnsupportedMetadata(String json) {
        assertThrows(SyncDataFormatException.class, () -> this.detector.isV2(this.mapper.readTree(json)));
    }

    @Test
    void rejectsMissingMetadata() {
        assertThrows(SyncDataFormatException.class, () -> this.detector.isV2(null));
    }
}
