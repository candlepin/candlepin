/**
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
import org.candlepin.dto.ObjectTranslator;
import org.candlepin.model.Branding;
import org.candlepin.util.Util;

/**
 * The BrandingTranslator provides translation from Branding model objects to BrandingDTOs
 */
public class BrandingTranslator implements ObjectTranslator<Branding, BrandingDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public BrandingDTO translate(Branding source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BrandingDTO translate(ModelTranslator translator, Branding source) {
        return source != null ? this.populate(translator, source, new BrandingDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BrandingDTO populate(Branding source, BrandingDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BrandingDTO populate(ModelTranslator modelTranslator, Branding source, BrandingDTO dest) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (dest == null) {
            throw new IllegalArgumentException("destination is null");
        }

        dest.setProductId(source.getProductId());
        dest.setName(source.getName());
        dest.setType(source.getType());
        dest.created(Util.toDateTime(source.getCreated()));
        dest.updated(Util.toDateTime(source.getUpdated()));

        return dest;
    }
}
