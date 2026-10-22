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
import org.candlepin.dto.ObjectTranslator;
import org.candlepin.sync.Meta;

/**
 * Translation of Meta objects to version 2 manifest MetaDTOs
 */
public class MetaTranslator implements ObjectTranslator<Meta, MetaDTO> {

    @Override
    public MetaDTO translate(Meta source) {
        return this.translate(null, source);
    }

    @Override
    public MetaDTO translate(ModelTranslator translator, Meta source) {
        return source != null ? this.populate(translator, source, new MetaDTO()) : null;
    }

    @Override
    public MetaDTO populate(Meta source, MetaDTO destination) {
        return this.populate(null, source, destination);
    }

    @Override
    public MetaDTO populate(ModelTranslator translator, Meta source, MetaDTO destination) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }
        if (destination == null) {
            throw new IllegalArgumentException("destination is null");
        }

        destination.setVersion(source.getVersion());
        destination.setCreated(source.getCreated());
        destination.setPrincipalName(source.getPrincipalName());
        destination.setWebAppPrefix(source.getWebAppPrefix());
        destination.setCdnLabel(source.getCdnLabel());
        destination.setManifestVersion("2");

        return destination;
    }
}
