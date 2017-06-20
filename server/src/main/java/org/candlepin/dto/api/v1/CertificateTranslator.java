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
import org.candlepin.model.Certificate;
import org.candlepin.model.CertificateSerial;



/**
 * The CertificateTranslator provides translation from Certificate model objects to
 * CertificateDTOs
 */
public class CertificateTranslator extends TimestampedEntityTranslator<Certificate, CertificateDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public CertificateDTO translate(Certificate source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CertificateDTO translate(DTOFactory factory, Certificate source) {
        return this.populate(factory, source, new CertificateDTO());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CertificateDTO populate(Certificate source, CertificateDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CertificateDTO populate(DTOFactory factory, Certificate source, CertificateDTO dest) {
        dest = super.populate(factory, source, dest);

        dest.setId(source.getId());
        dest.setKey(source.getKey());
        dest.setCert(source.getCert());

        if (factory != null) {
            dest.setSerial(factory.<CertificateSerial, CertificateSerialDTO>buildDTO(source.getSerial()));
        }
        else {
            dest.setSerial(null);
        }

        return dest;
    }
}
