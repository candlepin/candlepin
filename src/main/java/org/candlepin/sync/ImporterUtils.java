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
import org.candlepin.model.Certificate;

import java.util.Date;



/**
 * Utility class used when importing entities.
 */
class ImporterUtils {

    private ImporterUtils() {
        // default constructor
    }

    /**
     * Populates the specified entity with data from the provided DTO. This method can be used to
     * populate all certificate entities.
     *
     * @param entity
     *  The entity instance to populate
     *
     * @param dto
     *  The DTO containing the data with which to populate the entity
     *
     * @throws IllegalArgumentException
     *  if either entity or dto are null
     *
     * @return
     *  the populated entity
     */
    static Certificate populateEntity(Certificate entity, CertificateDTO dto) {
        if (entity == null) {
            throw new IllegalArgumentException("the certificate model entity is null");
        }

        if (dto == null) {
            throw new IllegalArgumentException("the certificate dto is null");
        }

        entity.setCreated(dto.getCreated())
            .setUpdated(dto.getUpdated())
            .setCertificate(dto.getCertificate()) // TODO: This really should look for the payload vs cert
            .setPrivateKey(dto.getPrivateKey())
            .setRevoked(dto.isRevoked())
            .setExpiration(dto.getExpiration());

        return entity;
    }
}
