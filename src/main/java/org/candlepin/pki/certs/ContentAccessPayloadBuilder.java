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

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.candlepin.controller.util.ContentPathBuilder;
import org.candlepin.controller.util.PromotedContent;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Environment;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.pki.Signer;
import org.candlepin.util.Util;
import org.candlepin.util.X509V3ExtensionUtil;

public class ContentAccessPayloadBuilder {
    private static final String PRODUCT_ID = "content_access";
    private static final String PRODUCT_NAME = "Content Access";

    private final EntitlementPayloadGenerator payloadGenerator;
    private final X509V3ExtensionUtil v3ExtensionUtil;

    private Owner owner;
    private Consumer consumer;
    private Signer signer;
    private List<Environment> environments = new ArrayList<>();
    private Collection<ProductContent> content = new ArrayList<>();

    public ContentAccessPayloadBuilder(EntitlementPayloadGenerator payloadGenerator,
        X509V3ExtensionUtil v3ExtensionUtil) {

        this.payloadGenerator = Objects.requireNonNull(payloadGenerator);
        this.v3ExtensionUtil = Objects.requireNonNull(v3ExtensionUtil);
    }

    public ContentAccessPayloadBuilder setOwner(Owner owner) {
        this.owner = owner;
        return this;
    }

    public ContentAccessPayloadBuilder setConsumer(Consumer consumer) {
        this.consumer = consumer;
        return this;
    }

    public ContentAccessPayloadBuilder setSigner(Signer signer) {
        this.signer = signer;
        return this;
    }

    public ContentAccessPayloadBuilder setEnvironments(List<Environment> environments) {
        if (environments == null) {
            throw new IllegalArgumentException("environments is null");
        }

        this.environments = environments;
        return this;
    }

    public ContentAccessPayloadBuilder setContent(Collection<ProductContent> content) {
        if (content == null) {
            throw new IllegalArgumentException("content is null");
        }

        this.content = content;
        return this;
    }

    public String build() {

        // TODO: Either need to validate that required fields are populated when we are ready to build, or
        // we need to populate the fields with default values.

        ContentPathBuilder contentPathBuilder = ContentPathBuilder.from(this.owner, this.environments);
        PromotedContent promotedContent = new PromotedContent(contentPathBuilder)
            .withAll(this.environments);

        Function<ProductContent, String> cidFetcher = pcinfo -> pcinfo.getContent()
            .getId();
        Map<String, ProductContent> ownerContent = this.content
            .stream()
            .collect(Collectors.toMap(cidFetcher, Function.identity(),
                (v1, v2) -> new ProductContent(v2.getContent(), v1.isEnabled() || v2.isEnabled())));

        byte[] payloadBytes = this.createPayload(this.consumer, ownerContent, promotedContent);

        return this.createPayloadAndSignature(payloadBytes);
    }

    private byte[] createPayload(Consumer consumer, Map<String, ProductContent> activateContent,
        PromotedContent promotedContent) {

        String consumerUuid = consumer != null ? consumer.getUuid() : null;

        Product engProduct = new Product()
            .setId(PRODUCT_ID)
            .setName(PRODUCT_NAME)
            .setProductContent(activateContent.values());

        Product skuProduct = new Product()
            .setId(PRODUCT_ID)
            .setName(PRODUCT_NAME);

        Pool emptyPool = new Pool()
            .setProduct(skuProduct)
            .setStartDate(new Date())
            .setEndDate(new Date());

        Entitlement emptyEnt = new Entitlement();
        emptyEnt.setPool(emptyPool);
        emptyEnt.setConsumer(consumer);

        Set<String> entitledProductIds = new HashSet<>();
        entitledProductIds.add("content-access");

        org.candlepin.model.dto.Product productModel = this.v3ExtensionUtil.mapProduct(engProduct, skuProduct,
            promotedContent, consumer, emptyPool, entitledProductIds);

        List<org.candlepin.model.dto.Product> productModels = new ArrayList<>();
        productModels.add(productModel);

        return this.payloadGenerator.generate(productModels, consumerUuid, emptyPool, null);
    }

    private String createPayloadAndSignature(byte[] payloadBytes) {
        String payload = "-----BEGIN ENTITLEMENT DATA-----\n";
        payload += Util.toBase64(payloadBytes);
        payload += "-----END ENTITLEMENT DATA-----\n";

        byte[] bytes = this.signer
            .sign(new ByteArrayInputStream(payloadBytes));

        String signature = "-----BEGIN SIGNATURE-----\n";
        signature += Util.toBase64(bytes);
        signature += "-----END SIGNATURE-----\n";
        return payload + signature;
    }
}
