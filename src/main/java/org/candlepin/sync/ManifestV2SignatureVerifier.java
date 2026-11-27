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

import java.nio.file.Path;

/**
 * Signature verification boundary for manifest v2 import
 * <p>
 * The importer must invoke this verifier before interpreting or persisting manifest data, including
 * certificates. Implementations supplied by the signature-validation integration own the signature layout,
 * trust checks, and mapping of verification failures to import conflicts. No implementation may accept an
 * unsigned manifest merely because signature support is unavailable.
 */
public interface ManifestV2SignatureVerifier {

    /**
     * Verification of a manifest archive and its extracted payload
     * <p>
     * Extraction must already have rejected unsafe archive paths. The archive and extracted directory must
     * belong to the same import and remain unchanged throughout verification and subsequent import.
     * Successful return means verification passed or a supported conflict was explicitly overridden.
     *
     * @param archive
     *     the original manifest archive, including any signature envelope
     * @param manifestDirectory
     *     the extracted payload directory containing meta.json
     * @param overrides
     *     explicitly requested import conflict overrides; never null
     * @throws ImporterException
     *     if verification fails without an applicable override, or signature material cannot be read
     */
    void verify(Path archive, Path manifestDirectory, ConflictOverrides overrides) throws ImporterException;
}
