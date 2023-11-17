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
package org.candlepin.spec.bootstrap.data.builder;

import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.SubscriptionDTO;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.request.Request;
import org.candlepin.spec.bootstrap.client.request.Response;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;



/**
 * The ExportGenerator provides a fluent interface for generating manifests using the manifest
 * generator test extension.
 * <p></p>
 * Repeated calls to .export will generate new manifests that appear to come from the same consumer
 * and organization unless the underlying UUID is modified using the .usingConsumerUuid method.
 */
public class ExportGenerator {

    private static final String MANIFEST_GENERATOR_ENDPOINT = "/testext/manifestgen/export";

    private final ApiClient client;
    private final List<SubscriptionDTO> subscriptions;
    private String consumerUuid;

    public ExportGenerator() {
        this.client = ApiClients.admin();

        this.subscriptions = new ArrayList<>();
        this.consumerUuid = StringUtil.random("manifest-", 16, StringUtil.CHARSET_NUMERIC_HEX);
    }

    /**
     * Generates a random subscription for the given product.
     *
     * @param product
     *  the product for which to generate a subscription
     *
     * @return
     *  a randomly generated subscription using the given product
     */
    private SubscriptionDTO createSubscriptionForProduct(ProductDTO product) {
        return Subscriptions.random()
            .product(product);
    }

    /**
     * Ensures the given subscription has an ID set. While not technically necessary, it provides
     * run-to-run consistency for reconciling subscriptions to pools during import.
     *
     * @param subscription
     *  the subscription to verify
     */
    private void ensureSubscriptionHasId(SubscriptionDTO subscription) {
        if (subscription != null && subscription.getId() == null) {
            subscription.setId(StringUtil.random("test_sub-", 8, StringUtil.CHARSET_NUMERIC_HEX));
        }
    }

    /**
     * Clears all subscription data from this generator.
     *
     * @return
     *  a reference to this generator
     */
    public ExportGenerator clear() {
        this.subscriptions.clear();
        return this;
    }

    /**
     * Sets the UUID to assign to the consumer used to generate the manifest. If null, the UUID will
     * be randomly generated every time the manifest is exported. If non-null, the given UUID must
     * not collide with an existing UUID already assigned to another consumer.
     * <p></p>
     * Generally this isn't necessary, but could be used to make the manifest appear to come from a
     * new consumer or organization without rebuilding the entire export generator.
     *
     * @param uuid
     *  the UUID to assign to consumers used for manifest generation
     *
     * @return
     *  a reference to this generator
     */
    public ExportGenerator usingConsumerUuid(String uuid) {
        this.consumerUuid = uuid;
        return this;
    }

    /**
     * Adds the given subscriptions to this manifest generator. Null collections and elements will
     * be silently ignored.
     *
     * @param subscriptions
     *  a collection of subscriptions to add to this generator
     *
     * @return
     *  a reference to this generator
     */
    public ExportGenerator addSubscriptions(Collection<SubscriptionDTO> subscriptions) {
        if (subscriptions == null) {
            return this;
        }

        subscriptions.stream()
            .filter(Objects::nonNull)
            .peek(this::ensureSubscriptionHasId)
            .forEach(this.subscriptions::add);

        return this;
    }

    /**
     * Adds the given subscriptions to this manifest generator. Null collections and elements will
     * be silently ignored.
     *
     * @param subscriptions
     *  a collection of subscriptions to add to this generator
     *
     * @return
     *  a reference to this generator
     */
    public ExportGenerator addSubscriptions(SubscriptionDTO... subscriptions) {
        if (subscriptions == null) {
            return this;
        }

        return this.addSubscriptions(Arrays.asList(subscriptions));
    }

    /**
     * Adds the given subscription to this manifest generator. If the subscription is null, it will
     * be silently ignored.
     *
     * @param subscription
     *  the subscription to add to this generator
     *
     * @return
     *  a reference to this generator
     */
    public ExportGenerator addSubscription(SubscriptionDTO subscription) {
        if (subscription == null) {
            return this;
        }

        return this.addSubscriptions(List.of(subscription));
    }

    /**
     * Adds the given products to this manifest generator, using a randomly generated subscription
     * for each product. Null collections and elements will be silently ignored.
     *
     * @param products
     *  a collection of products to add to this generator
     *
     * @return
     *  a reference to this generator
     */
    public ExportGenerator addProducts(Collection<ProductDTO> products) {
        if (products == null) {
            return this;
        }

        products.stream()
            .filter(Objects::nonNull)
            .map(this::createSubscriptionForProduct)
            .peek(this::ensureSubscriptionHasId)
            .forEach(this.subscriptions::add);

        return this;
    }

    /**
     * Adds the given products to this manifest generator, using a randomly generated subscription
     * for each product. Null collections and elements will be silently ignored.
     *
     * @param products
     *  a collection of products to add to this generator
     *
     * @return
     *  a reference to this generator
     */
    public ExportGenerator addProducts(ProductDTO... products) {
        if (products == null) {
            return this;
        }

        return this.addProducts(Arrays.asList(products));
    }

    /**
     * Adds the given product to this manifest generator using a randomly generated subscription for
     * the product. If the product is null, it will be silently ignored.
     *
     * @param product
     *  the product to add to this generator
     *
     * @return
     *  a reference to this generator
     */
    public ExportGenerator addProduct(ProductDTO product) {
        if (product == null) {
            return this;
        }

        return this.addProducts(List.of(product));
    }

    /**
     * Generates a manifest by exporting the previously provided subscription data from the target
     * Candlepin instance using the manifest generator test extension. The manifest data is written
     * to a temporary file which will be deleted on exit.
     *
     * @param cdn
     *  cdn information to include with the export request
     *
     * @return
     *  a reference to the temporary file containing the exported manifest
     */
    public File export(ExportCdn cdn) throws IOException {
        String cdnLabel = cdn != null ? cdn.label() : null;
        String webAppPrefix = cdn != null ? cdn.webUrl() : null;
        String apiUrl = cdn != null ? cdn.apiUrl() : null;

        Request request = Request.from(this.client)
            .setPath(MANIFEST_GENERATOR_ENDPOINT)
            .setMethod("POST")
            .setBody(this.subscriptions);

        // Add in optional query bits
        if (this.consumerUuid != null && !consumerUuid.isBlank()) {
            request.addQueryParam("consumer_uuid", this.consumerUuid);
        }

        if (cdnLabel != null && !cdnLabel.isBlank()) {
            request.addQueryParam("cdn_label", cdnLabel);
        }

        if (webAppPrefix != null && !webAppPrefix.isBlank()) {
            request.addQueryParam("webapp_prefix", webAppPrefix);
        }

        if (apiUrl != null && !apiUrl.isBlank()) {
            request.addQueryParam("api_url", apiUrl);
        }

        // Execute request & output
        Response response = request.execute();
        if (response.getCode() != 200) {
            String errmsg = String.format("manifest generation request failed with HTTP response code %d: %s",
                response.getCode(), response.getBodyAsString());
            throw new IllegalStateException(errmsg);
        }

        File manifest = File.createTempFile("manifest", ".zip");
        manifest.deleteOnExit();

        try (OutputStream ostream = new FileOutputStream(manifest)) {
            ostream.write(response.getBody());
            ostream.flush();
        }

        return manifest;
    }

    /**
     * Generates a manifest by exporting the previously provided subscription data from the target
     * Candlepin instance using the manifest generator test extension. The manifest data is written
     * to a temporary file which will be deleted on exit.
     *
     * @return
     *  a reference to the temporary file containing the exported manifest
     */
    public File export() throws IOException {
        return this.export(null);
    }

}
