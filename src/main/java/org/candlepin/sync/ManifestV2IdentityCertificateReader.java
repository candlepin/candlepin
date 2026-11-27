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

import org.candlepin.service.model.CertificateInfo;

import java.nio.file.Path;

/**
 * Reader boundary for the upstream consumer identity certificate in manifest v2
 * <p>
 * The export layout is {@code upstream_consumer/<serial-id>.json}, alongside {@code consumer.json}. Reading must
 * occur only after {@link ManifestV2SignatureVerifier} has accepted the manifest. Implementations must reject
 * missing, malformed, or incomplete required certificate material with an {@link ImporterException}.
 * <p>
 * The returned material represents the upstream consumer identity, not a locally generated identity.
 * Readers perform no persistence and must not attach certificates to local consumers. The importer owns
 * validation against the upstream consumer and the eventual storage mapping.
 */
public interface ManifestV2IdentityCertificateReader {

    /**
     * Reading of required upstream consumer identity certificate material
     * <p>
     * The returned value must contain the certificate, private key, and serial metadata required by the
     * certificate integration. Using {@link CertificateInfo} avoids requiring a particular persistence entity.
     * Implementations must not log private keys or include them in exception messages.
     *
     * @param manifestDirectory
     *     the verified, extracted manifest payload directory containing meta.json
     * @throws ImporterException
     *     if required certificate material is missing, invalid, or unreadable
     * @return
     *     non-null decoded certificate material, without database writes
     */
    CertificateInfo read(Path manifestDirectory) throws ImporterException;
}
