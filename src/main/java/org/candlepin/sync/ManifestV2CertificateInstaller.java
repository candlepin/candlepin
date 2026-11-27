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

import org.candlepin.dto.manifest.v1.ConsumerDTO;
import org.candlepin.model.Owner;
import org.candlepin.service.model.CertificateInfo;

/**
 * Certificate ownership validation and transactional storage boundary for v2 import
 * <p>
 * BLOCKED by the pending identity and SCA certificate integrations: the certificate-to-consumer validation
 * rules and upstream SCA storage mapping are not yet available. No implementation or binding is provided.
 * Implementations must not log private keys or place them in exception messages.
 */
public interface ManifestV2CertificateInstaller {

    /**
     * Validation of both certificates against the exported upstream consumer
     *
     * @param consumer
     *     the detached upstream consumer metadata
     * @param identity
     *     the required identity certificate material
     * @param sca
     *     the required SCA certificate material
     * @throws ImporterException
     *     if either certificate is incomplete, invalid, or does not belong to the exported consumer
     */
    void validate(ConsumerDTO consumer, CertificateInfo identity, CertificateInfo sca) throws ImporterException;

    /**
     * Installation of validated credentials on the imported upstream consumer
     * <p>
     * Runs inside the import transaction after consumer metadata is stored. Implementations own serial
     * remapping and persistence of the upstream identity and SCA credentials. They must not associate the
     * upstream SCA certificate with an unrelated local consumer or commit independently of the import.
     *
     * @param owner
     *     the local owner with its newly stored upstream consumer
     * @param identity
     *     validated identity certificate material
     * @param sca
     *     validated SCA certificate material
     * @throws ImporterException
     *     if credentials cannot be stored, causing the complete import to roll back
     */
    void install(Owner owner, CertificateInfo identity, CertificateInfo sca) throws ImporterException;
}
