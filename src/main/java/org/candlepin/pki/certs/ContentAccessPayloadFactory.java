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
package org.candlepin.pki.certs;

import java.util.Objects;

import org.candlepin.util.X509V3ExtensionUtil;

import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Factory for creating {@link ContentAccessPayloadBuilder} instances used to build
 * content access payloads.
 * <p>
 * This factory provides a single entry point for obtaining a configured builder
 */
@Singleton
public class ContentAccessPayloadFactory {
    private final EntitlementPayloadGenerator payloadGenerator;
    private final X509V3ExtensionUtil v3ExtensionUtil;

    @Inject
    public ContentAccessPayloadFactory(EntitlementPayloadGenerator payloadGenerator,
        X509V3ExtensionUtil v3ExtensionUtil) {

        this.payloadGenerator = Objects.requireNonNull(payloadGenerator);
        this.v3ExtensionUtil = Objects.requireNonNull(v3ExtensionUtil);
    }

    /**
     * @return a new builder for constructing content access payloads
     */
    public ContentAccessPayloadBuilder builder() {
        return new ContentAccessPayloadBuilder(this.payloadGenerator, this.v3ExtensionUtil);
    }

}
