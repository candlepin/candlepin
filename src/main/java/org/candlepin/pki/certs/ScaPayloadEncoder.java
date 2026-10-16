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

import org.candlepin.model.dto.Content;
import org.candlepin.model.dto.EntitlementBody;
import org.candlepin.model.dto.Order;
import org.candlepin.model.dto.Pool;
import org.candlepin.model.dto.Product;
import org.candlepin.model.dto.TinySubscription;
import org.candlepin.pki.CryptoManager;
import org.candlepin.pki.Scheme;
import org.candlepin.util.Util;

import java.util.Date;
import java.util.List;
import java.util.Objects;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Serialization, compression, and signing of prepared SCA payload data
 */
@Singleton
public class ScaPayloadEncoder {
    private final CryptoManager cryptoManager;
    private final EntitlementPayloadGenerator payloadGenerator;

    @Inject
    public ScaPayloadEncoder(CryptoManager cryptoManager, EntitlementPayloadGenerator payloadGenerator) {
        this.cryptoManager = Objects.requireNonNull(cryptoManager);
        this.payloadGenerator = Objects.requireNonNull(payloadGenerator);
    }

    /**
     * Encodes and signs repository data without gathering or filtering content
     *
     * @return Base64 entitlement-data and signature blocks
     */
    /* pkg-private */ String encode(ScaPayloadData data, Scheme scheme) {
        Objects.requireNonNull(data);
        Objects.requireNonNull(scheme);

        byte[] payload = this.payloadGenerator.generate(this.createBody(data));
        byte[] signature = this.cryptoManager.getSigner(scheme).sign(payload);

        return new StringBuilder("-----BEGIN ENTITLEMENT DATA-----\n")
            .append(Util.toBase64(payload))
            .append("-----END ENTITLEMENT DATA-----\n")
            .append("-----BEGIN SIGNATURE-----\n")
            .append(Util.toBase64(signature))
            .append("-----END SIGNATURE-----\n")
            .toString();
    }

    private EntitlementBody createBody(ScaPayloadData data) {
        Product product = new Product();
        product.setId("content_access");
        product.setName(" Content Access");
        product.setVersion("");
        product.setBrandType("");
        product.setBrandName("");
        product.setArchitectures(List.of());
        product.setContent(data.content().stream()
            .map(this::createContent)
            .toList());

        TinySubscription subscription = new TinySubscription();
        subscription.setSku("content_access");
        subscription.setName("Content Access");

        String timestamp = Util.getUTCDateFormat().format(Date.from(data.timestamp()));
        Order order = new Order();
        order.setStart(timestamp);
        order.setEnd(timestamp);

        EntitlementBody body = new EntitlementBody();
        body.setConsumer(data.consumerUuid());
        body.setSubscription(subscription);
        body.setOrder(order);
        body.setProducts(List.of(product));
        body.setPool(new Pool());
        return body;
    }

    private Content createContent(ScaContentData data) {
        return new Content()
            .setId(data.id())
            .setType(data.type())
            .setName(data.name())
            .setLabel(data.label())
            .setVendor(data.vendor())
            .setPath(data.path())
            .setGpgUrl(data.gpgUrl())
            .setEnabled(data.enabled())
            .setMetadataExpiration(data.metadataExpiration())
            .setRequiredTags(data.requiredTags())
            .setArches(data.arches());
    }
}
