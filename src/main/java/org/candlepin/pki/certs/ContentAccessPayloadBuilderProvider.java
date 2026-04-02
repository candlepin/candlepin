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

import org.candlepin.model.ContentAccessPayloadCurator;
import org.candlepin.model.ContentCurator;
import org.candlepin.pki.CryptoManager;
import org.candlepin.util.X509V3ExtensionUtil;

import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Provider;



/**
 * Provider for ContentAccessPayloadBuilder instances
 */
public class ContentAccessPayloadBuilderProvider implements Provider<ContentAccessPayloadBuilder> {

    private final CryptoManager cryptoManager;
    private final EntitlementPayloadGenerator entitlementPayloadGenerator;
    private final X509V3ExtensionUtil v3ExtensionUtil;
    private final ContentCurator contentCurator;
    private final ContentAccessPayloadCurator contentAccessPayloadCurator;

    @Inject
    public ContentAccessPayloadBuilderProvider(
        CryptoManager cryptoManager,
        EntitlementPayloadGenerator entitlementPayloadGenerator,
        X509V3ExtensionUtil v3ExtensionUtil,
        ContentCurator contentCurator,
        ContentAccessPayloadCurator contentAccessPayloadCurator) {

        this.cryptoManager = Objects.requireNonNull(cryptoManager);
        this.entitlementPayloadGenerator = Objects.requireNonNull(entitlementPayloadGenerator);
        this.v3ExtensionUtil = Objects.requireNonNull(v3ExtensionUtil);
        this.contentCurator = Objects.requireNonNull(contentCurator);
        this.contentAccessPayloadCurator = Objects.requireNonNull(contentAccessPayloadCurator);
    }

    @Override
    public ContentAccessPayloadBuilder get() {
        return new ContentAccessPayloadBuilder(this.cryptoManager, this.entitlementPayloadGenerator,
            this.v3ExtensionUtil, this.contentCurator, this.contentAccessPayloadCurator);
    }

}
