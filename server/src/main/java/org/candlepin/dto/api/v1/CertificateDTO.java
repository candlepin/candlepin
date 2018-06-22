/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
package org.candlepin.dto.api.v1;

import io.swagger.annotations.ApiModel;

import org.candlepin.dto.TimestampedCandlepinDTO;



/**
 * The CertificateDTO is a DTO representing most Candlepin certificates presented to the API.
 * (exceptions include ProductCertificate which has its own DTO).
 */
@ApiModel(parent = TimestampedCandlepinDTO.class, description = "DTO representing a certificate")
public class CertificateDTO extends AbstractCertificateDTO<CertificateDTO> {
    public static final long serialVersionUID = 1L;

    /**
     * Initializes a new CertificateDTO instance with null values.
     */
    public CertificateDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a new CertificateDTO instance which is a shallow copy of the provided
     * source entity.
     *
     * @param source
     *  The source entity to copy
     */
    public CertificateDTO(CertificateDTO source) {
        super(source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        CertificateSerialDTO serial = this.getSerial();

        return String.format("CertificateDTO [id: %s, key: %s, serial id: %s]",
            this.getId(), this.getKey(), serial != null ? serial.getId() : null);
    }
}
