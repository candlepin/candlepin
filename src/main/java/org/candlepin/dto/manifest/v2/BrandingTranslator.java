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

package org.candlepin.dto.manifest.v2;

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.TimestampedEntityTranslator;
import org.candlepin.model.Branding;

/**
 * Translation of Branding objects to version 2 manifest BrandingDTOs
 */
public class BrandingTranslator extends TimestampedEntityTranslator<Branding, BrandingDTO> {

    @Override
    public BrandingDTO translate(Branding source) {
        return this.translate(null, source);
    }

    @Override
    public BrandingDTO translate(ModelTranslator translator, Branding source) {
        return source != null ? this.populate(translator, source, new BrandingDTO()) : null;
    }

    @Override
    public BrandingDTO populate(Branding source, BrandingDTO destination) {
        return this.populate(null, source, destination);
    }

    @Override
    public BrandingDTO populate(ModelTranslator translator, Branding source, BrandingDTO destination) {
        destination = super.populate(translator, source, destination);

        destination.setId(source.getId());
        destination.setProductId(source.getProductId());
        destination.setName(source.getName());
        destination.setType(source.getType());

        return destination;
    }
}
