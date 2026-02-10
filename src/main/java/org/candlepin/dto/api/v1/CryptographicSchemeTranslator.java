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
import org.candlepin.dto.api.server.v1.CryptographicSchemeDTO;
import org.candlepin.pki.CryptographyStatusProvider.SchemeMetadata;

/**
 * Provides translation from SchemeMetadata to CryptographicSchemeDTO
 */
public class CryptographicSchemeTranslator
    implements ObjectTranslator<SchemeMetadata, CryptographicSchemeDTO> {

    @Override
    public CryptographicSchemeDTO translate(SchemeMetadata source) {
        return this.translate(null, source);
    }

    @Override
    public CryptographicSchemeDTO translate(ModelTranslator translator, SchemeMetadata source) {
        return source != null ? this.populate(translator, source, new CryptographicSchemeDTO()) : null;
    }

    @Override
    public CryptographicSchemeDTO populate(SchemeMetadata source, CryptographicSchemeDTO destination) {
        return this.populate(null, source, destination);
    }

    @Override
    public CryptographicSchemeDTO populate(ModelTranslator translator, SchemeMetadata source,
        CryptographicSchemeDTO dest) {

        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (dest == null) {
            throw new IllegalArgumentException("destination is null");
        }

        dest.name(source.name()).signatureAlgorithm(source.signatureAlgorithm())
            .keyAlgorithm(source.keyAlgorithm());

        if (source.keySize() != null) {
            dest.keySize(source.keySize());
        }

        return dest;
    }
}
