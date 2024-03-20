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

import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.EntitlementCertificateCurator;
import org.candlepin.model.PoolQuantity;
import org.candlepin.model.Product;
import org.candlepin.pki.certs.EntitlementCertificateGenerator;
import org.candlepin.service.EntitlementCertServiceAdapter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.inject.Inject;

/**
 * DefaultEntitlementCertServiceAdapter
 */
public class DefaultEntitlementCertServiceAdapter implements EntitlementCertServiceAdapter {
    private final EntitlementCertificateCurator entCertCurator;
    private final CertificateSerialCurator serialCurator;
    private final EntitlementCertificateGenerator entitlementCertificateGenerator;

    @Inject
    public DefaultEntitlementCertServiceAdapter(
        EntitlementCertificateCurator entCertCurator,
        CertificateSerialCurator serialCurator,
        EntitlementCertificateGenerator entitlementCertificateGenerator) {

        this.entCertCurator = Objects.requireNonNull(entCertCurator);
        this.serialCurator = Objects.requireNonNull(serialCurator);
        this.entitlementCertificateGenerator = Objects.requireNonNull(entitlementCertificateGenerator);
    }

    @Override
    public List<EntitlementCertificate> listForConsumer(Consumer consumer) {
        return entCertCurator.listForConsumer(consumer);
    }

    // NOTE: we use entitlement here, but it version does not...
    // NOTE: we can get consumer from entitlement.getConsumer()
    @Override
    public EntitlementCertificate generateEntitlementCert(Entitlement entitlement, Product product) {

        Map<String, Entitlement> entitlements = new HashMap<>();
        entitlements.put(entitlement.getPool().getId(), entitlement);
        Map<String, PoolQuantity> poolQuantities = new HashMap<>();
        poolQuantities.put(entitlement.getPool().getId(),
            new PoolQuantity(entitlement.getPool(), entitlement.getQuantity()));
        Map<String, Product> products = new HashMap<>();
        products.put(entitlement.getPool().getId(), product);

        Map<String, EntitlementCertificate> result = this.entitlementCertificateGenerator
            .generate(entitlement.getConsumer(), poolQuantities, entitlements, products, true);

        return result.get(entitlement.getPool().getId());
    }

    @Override
    public Map<String, EntitlementCertificate> generateEntitlementCerts(Consumer consumer,
        Map<String, PoolQuantity> poolQuantities,
        Map<String, Entitlement> entitlements,
        Map<String, Product> products, boolean save) {

        return this.entitlementCertificateGenerator
            .generate(consumer, poolQuantities, entitlements, products, save);
    }

    public List<Long> listEntitlementSerialIds(Consumer consumer) {
        return serialCurator.listEntitlementSerialIds(consumer);
    }
}
