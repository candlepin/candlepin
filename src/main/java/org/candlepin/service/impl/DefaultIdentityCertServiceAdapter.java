/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.service.impl;

import org.candlepin.model.Consumer;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.IdentityCertificateCurator;
import org.candlepin.pki.certs.IdentityCertificateGenerator;
import org.candlepin.service.IdentityCertServiceAdapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

import javax.inject.Inject;


public class DefaultIdentityCertServiceAdapter implements IdentityCertServiceAdapter {
    private static final Logger log = LoggerFactory.getLogger(DefaultIdentityCertServiceAdapter.class);
    private final IdentityCertificateCurator idCertCurator;
    private final IdentityCertificateGenerator identityCertificateGenerator;

    @Inject
    public DefaultIdentityCertServiceAdapter(
        IdentityCertificateCurator identityCertCurator,
        IdentityCertificateGenerator identityCertificateGenerator) {
        this.idCertCurator = Objects.requireNonNull(identityCertCurator);
        this.identityCertificateGenerator = Objects.requireNonNull(identityCertificateGenerator);
    }

    @Override
    public void deleteIdentityCert(Consumer consumer) {
        if (consumer.getIdCert() == null) {
            log.warn("Unable to delete null identity cert for consumer: {}", consumer.getUuid());
            return;
        }

        IdentityCertificate certificate = this.idCertCurator.get(consumer.getIdCert().getId());
        if (certificate != null) {
            this.idCertCurator.delete(certificate);
        }
    }

    @Override
    public IdentityCertificate generateIdentityCert(Consumer consumer) {
        return getIdentityCertificate(consumer, false);
    }

    @Override
    public IdentityCertificate regenerateIdentityCert(Consumer consumer) {
        return getIdentityCertificate(consumer, true);
    }

    private IdentityCertificate getIdentityCertificate(Consumer consumer, boolean regenerate) {
        IdentityCertificate certificate = null;

        if (consumer.getIdCert() != null) {
            certificate = this.idCertCurator.get(consumer.getIdCert().getId());
        }

        if (certificate != null) {
            if (regenerate) {
                consumer.setIdCert(null);
                this.idCertCurator.delete(certificate);
            }
            else {
                return certificate;
            }
        }

        IdentityCertificate newCertificate = this.identityCertificateGenerator.generate(consumer);
        consumer.setIdCert(newCertificate);

        return newCertificate;
    }

}
