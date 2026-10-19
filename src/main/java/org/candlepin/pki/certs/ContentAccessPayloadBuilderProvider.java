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

import java.util.Objects;

import jakarta.inject.Inject;
import jakarta.inject.Provider;



/**
 * Provider for ContentAccessPayloadBuilder instances
 */
public class ContentAccessPayloadBuilderProvider implements Provider<ContentAccessPayloadBuilder> {

    private final ScaContentDataProvider dataProvider;
    private final ScaPayloadEncoder encoder;
    private final ContentAccessPayloadCurator contentAccessPayloadCurator;

    @Inject
    public ContentAccessPayloadBuilderProvider(ScaContentDataProvider dataProvider, ScaPayloadEncoder encoder,
        ContentAccessPayloadCurator contentAccessPayloadCurator) {

        this.dataProvider = Objects.requireNonNull(dataProvider);
        this.encoder = Objects.requireNonNull(encoder);
        this.contentAccessPayloadCurator = Objects.requireNonNull(contentAccessPayloadCurator);
    }

    @Override
    public ContentAccessPayloadBuilder get() {
        return new ContentAccessPayloadBuilder(this.dataProvider, this.encoder, this.contentAccessPayloadCurator);
    }

}
