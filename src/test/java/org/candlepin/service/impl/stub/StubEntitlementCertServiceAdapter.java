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
package org.candlepin.service.impl.stub;

import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.EntitlementCertificateCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolQuantity;
import org.candlepin.model.Product;
import org.candlepin.service.EntitlementCertServiceAdapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import javax.inject.Inject;

/**
 * Generating an entitlement cert is expensive, this class stubs the process out.
 */
public class StubEntitlementCertServiceAdapter implements EntitlementCertServiceAdapter {

    private static final Logger log = LoggerFactory.getLogger(StubEntitlementCertServiceAdapter.class);
    protected EntitlementCertificateCurator entCertCurator;
    private final CertificateSerialCurator serialCurator;

    @Inject
    public StubEntitlementCertServiceAdapter(
        EntitlementCertificateCurator entCertCurator,
        CertificateSerialCurator serialCurator) {

        this.entCertCurator = Objects.requireNonNull(entCertCurator);
        this.serialCurator = Objects.requireNonNull(serialCurator);
    }

    @Override
    public List<EntitlementCertificate> listForConsumer(Consumer consumer) {
        return entCertCurator.listForConsumer(consumer);
    }

    @Override
    public EntitlementCertificate generateEntitlementCert(Entitlement entitlement, Product product) {
        Map<String, Entitlement> ents = new HashMap<>();
        Map<String, PoolQuantity> poolQuantityMap = new HashMap<>();
        Map<String, Product> productMap = new HashMap<>();

        Pool pool = entitlement.getPool();
        ents.put(pool.getId(), entitlement);
        poolQuantityMap.put(pool.getId(), new PoolQuantity(pool, entitlement.getQuantity()));
        productMap.put(pool.getId(), product);

        return generateEntitlementCerts(entitlement.getConsumer(),
            poolQuantityMap, ents, productMap, true).get(pool.getId());
    }

    @Override
    public Map<String, EntitlementCertificate> generateEntitlementCerts(Consumer consumer,
        Map<String, PoolQuantity> poolQuantityMap, Map<String, Entitlement> entitlements,
        Map<String, Product> products, boolean save) {
        Map<String, EntitlementCertificate> result = new HashMap<>();

        for (Entry<String, Entitlement> entry: entitlements.entrySet()) {
            Entitlement entitlement = entry.getValue();
            Product product = products.get(entry.getKey());
            log.debug("Generating entitlement cert for:");
            log.debug("   consumer: " + consumer.getUuid());
            log.debug("   product: " + product.getUuid());
            log.debug("   end date: " + entitlement.getEndDate());

            EntitlementCertificate cert = new EntitlementCertificate();
            CertificateSerial serial = new CertificateSerial(entitlement.getEndDate());
            serialCurator.create(serial);

            cert.setSerial(serial);
            cert.setKeyAsBytes(("---- STUB KEY -----" + Math.random())
                .getBytes());
            cert.setCertAsBytes(("---- STUB CERT -----" + Math.random())
                .getBytes());
            cert.setEntitlement(entitlement);
            entitlement.getCertificates().add(cert);

            log.debug("Generated cert: " + serial.getId());
            log.debug("Key: " + cert.getKey());
            log.debug("Cert: " + cert.getCert());
            if (save) {
                cert = entCertCurator.create(cert);
            }
            result.put(entry.getKey(), cert);
        }

        return result;
    }

    @Override
    public List<Long> listEntitlementSerialIds(Consumer consumer) {
        return serialCurator.listEntitlementSerialIds(consumer);
    }
}
