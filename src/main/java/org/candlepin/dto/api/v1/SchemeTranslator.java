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
package org.candlepin.dto.api.v1;

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.ObjectTranslator;
import org.candlepin.dto.api.server.v1.SchemeDTO;
import org.candlepin.pki.Scheme;

/**
 * Provides translation from Scheme to SchemeDTO
 */
public class SchemeTranslator
    implements ObjectTranslator<Scheme, SchemeDTO> {

    @Override
    public SchemeDTO translate(Scheme source) {
        return this.translate(null, source);
    }

    @Override
    public SchemeDTO translate(ModelTranslator translator, Scheme source) {
        return source != null ? this.populate(translator, source, new SchemeDTO()) : null;
    }

    @Override
    public SchemeDTO populate(Scheme source, SchemeDTO destination) {
        return this.populate(null, source, destination);
    }

    @Override
    public SchemeDTO populate(ModelTranslator translator, Scheme source,
        SchemeDTO dest) {

        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (dest == null) {
            throw new IllegalArgumentException("destination is null");
        }

        dest.name(source.name())
            .signatureAlgorithm(source.signatureAlgorithm())
            .keyAlgorithm(source.keyAlgorithm())
            .keySize(source.keySize().orElse(null));

        return dest;
    }
}
