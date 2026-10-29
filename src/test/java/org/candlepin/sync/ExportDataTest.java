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


package org.candlepin.sync;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.candlepin.dto.manifest.v2.ConsumerDTO;
import org.candlepin.dto.manifest.v2.MetaDTO;
import org.candlepin.dto.manifest.v2.ProductDTO;
import org.candlepin.dto.manifest.v2.SubscriptionDTO;
import org.candlepin.model.SCACertificate;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

class ExportDataTest {

    @Test
    void testStoresPreparedExportData() {
        MetaDTO meta = new MetaDTO();
        ConsumerDTO consumer = new ConsumerDTO();
        Map<String, ProductDTO> products = new HashMap<>();
        Map<String, SubscriptionDTO> subscriptions = new HashMap<>();
        Path workDirectory = Paths.get("export-directory");
        SCACertificate certificate = new SCACertificate();

        ExportData data = new ExportData(meta, consumer, products, subscriptions, workDirectory, certificate);

        assertSame(meta, data.getMetaDTO());
        assertSame(consumer, data.getConsumerDTO());
        assertSame(products, data.getProducts());
        assertSame(subscriptions, data.getSubscriptions());
        assertSame(workDirectory, data.getWorkDirectory());
        assertSame(certificate, data.getScaCertificate());
    }

    @Test
    void testRetainsLiveExportMaps() {
        Map<String, ProductDTO> products = new HashMap<>();
        Map<String, SubscriptionDTO> subscriptions = new HashMap<>();
        ExportData data = new ExportData(new MetaDTO(), new ConsumerDTO(), products, subscriptions,
            Paths.get("export-directory"), new SCACertificate());
        ProductDTO product = new ProductDTO().setId("product");
        SubscriptionDTO subscription = new SubscriptionDTO().setSubscriptionId("subscription");

        products.put("product", product);
        subscriptions.put("pool", subscription);

        assertSame(product, data.getProducts().get("product"));
        assertSame(subscription, data.getSubscriptions().get("pool"));
    }

    @Test
    void testAllowsAbsentCertificate() {
        ExportData data = new ExportData(new MetaDTO(), new ConsumerDTO(), new HashMap<>(), new HashMap<>(),
            Paths.get("export-directory"), null);

        assertNull(data.getScaCertificate());
    }
}
