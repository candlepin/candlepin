/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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

import org.candlepin.dto.manifest.v1.CertificateDTO;
import org.candlepin.dto.manifest.v1.CertificateSerialDTO;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.RevocableCertificate;

/**
 * Utility class used when importing entities.
 */
class ImporterUtils {

    private ImporterUtils() {
        // default constructor
    }

    /**
     * Populates the specified entity with data from the provided DTO.
     * This method can be used to populate all certificate entities that extend RevocableCertificate.
     *
     * <p>Note: any entity fields that are not present on RevocableCertificate are not populated
     * and should be populated manually by the caller. </p>
     *
     * @param entity
     *  The entity instance to populate
     *
     * @param dto
     *  The DTO containing the data with which to populate the entity
     *
     * @throws IllegalArgumentException
     *  if either entity or dto are null
     */
    static void populateEntity(RevocableCertificate entity, CertificateDTO dto) {
        if (entity == null) {
            throw new IllegalArgumentException("the certificate model entity is null");
        }

        if (dto == null) {
            throw new IllegalArgumentException("the certificate dto is null");
        }

        entity.setKey(dto.getKey());
        entity.setCert(dto.getCertificate());
        entity.setUpdated(dto.getUpdated());
        entity.setCreated(dto.getCreated());

        if (dto.getSerial() != null) {
            CertificateSerialDTO dtoSerial = dto.getSerial();
            CertificateSerial entitySerial = new CertificateSerial();
            // No need to populate CertificateSerial's id (autogenerated)
            // or serial (id is used as the serial).
            entitySerial.setExpiration(dtoSerial.getExpiration());
            entitySerial.setRevoked(dtoSerial.isRevoked());
            entitySerial.setCreated(dtoSerial.getCreated());
            entitySerial.setUpdated(dtoSerial.getUpdated());

            entity.setSerial(entitySerial);
        }
    }
}