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
package org.candlepin.dto.api.v1;

import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.Owner;
import org.candlepin.model.UeberCertificate;



/**
 * The UeberCertificateTranslator provides translation from UeberCertificate model objects to
 * UeberCertificateDTOs
 */
public class UeberCertificateTranslator
    extends AbstractCertificateTranslator<UeberCertificate, UeberCertificateDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public UeberCertificateDTO translate(UeberCertificate source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UeberCertificateDTO translate(ModelTranslator translator, UeberCertificate source) {
        return source != null ? this.populate(translator, source, new UeberCertificateDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UeberCertificateDTO populate(UeberCertificate source, UeberCertificateDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UeberCertificateDTO populate(ModelTranslator translator, UeberCertificate source,
        UeberCertificateDTO dest) {

        dest = super.populate(translator, source, dest);

        if (translator != null) {
            Owner owner = source.getOwner();
            dest.setOwner(owner != null ? translator.translate(owner, NestedOwnerDTO.class) : null);

        }
        else {
            dest.setOwner(null);
        }

        return dest;
    }

}
