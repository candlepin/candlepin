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

import org.candlepin.dto.DTOFactory;
import org.candlepin.model.CertificateSerial;


/**
 * The CertificateSerialTranslator provides translation from CertificateSerial model objects to
 * CertificateSerialDTOs
 */
public class CertificateSerialTranslator extends
    TimestampedEntityTranslator<CertificateSerial, CertificateSerialDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public CertificateSerialDTO translate(CertificateSerial source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CertificateSerialDTO translate(DTOFactory factory, CertificateSerial source) {
        return this.populate(factory, source, new CertificateSerialDTO());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CertificateSerialDTO populate(CertificateSerial source,
        CertificateSerialDTO destination) {

        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CertificateSerialDTO populate(DTOFactory factory, CertificateSerial source,
        CertificateSerialDTO destination) {

        destination = super.populate(factory, source, destination);

        destination.setId(source.getId());
        destination.setSerial(source.getSerial());
        destination.setExpiration(source.getExpiration());
        destination.setCollected(source.isCollected());
        destination.setRevoked(source.isRevoked());

        return destination;
    }
}
