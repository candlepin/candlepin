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
import org.candlepin.dto.TimestampedEntityTranslator;
import org.candlepin.model.Certificate;



/**
 * The AbstractCertificateTranslator provides translation from Certificate model objects to
 * AbstractCertificateDTOs for the API endpoints
 *
 * @param <I>
 *  The input entity type supported by this translator
 *
 * @param <O>
 *  The output DTO type generated/managed by this translator
 */
public abstract class AbstractCertificateTranslator<I extends Certificate, O extends AbstractCertificateDTO>
    extends TimestampedEntityTranslator<I, O> {

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract O translate(I source);

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract O translate(ModelTranslator translator, I source);

    /**
     * {@inheritDoc}
     */
    @Override
    public O populate(I source, O destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public O populate(ModelTranslator translator, I source, O dest) {
        dest = super.populate(translator, source, dest);

        dest.setId(source.getId());
        dest.setKey(source.getKey());
        dest.setCert(source.getCert());

        if (translator != null) {
            dest.setSerial(translator.translate(source.getSerial(), CertificateSerialDTO.class));
        }
        else {
            dest.setSerial(null);
        }

        return dest;
    }

}
