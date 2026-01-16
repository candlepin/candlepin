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
package org.candlepin.sync;

import org.candlepin.dto.manifest.v1.ContentDTO;
import org.candlepin.dto.manifest.v1.ProductDTO;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * ProductImporter
 */
public class ProductImporter {
    private static Logger log = LoggerFactory.getLogger(ProductImporter.class);

    public static final String PRODUCT_FILE_SUFFIX = ".json";

    private Map<String, ProductDTO> cache;

    private File productDir;
    private ObjectMapper mapper;
    private I18n i18n;

    // TODO: We really should be providing something more generic like a DataSource or some such,
    // but this will work for a quick retrofit.
    public ProductImporter(File productDir, ObjectMapper mapper, I18n i18n) {
        if (productDir == null) {
            throw new IllegalArgumentException("productDir is null");
        }

        if (mapper == null) {
            throw new IllegalArgumentException("mapper is null");
        }

        if (i18n == null) {
            throw new IllegalArgumentException("i18n is null");
        }

        this.cache = new HashMap<>();

        this.productDir = productDir;
        this.mapper = mapper;
        this.i18n = i18n;
    }

    /**
     * Normalizes the given product to ensure it is safe for import. If the given product is null,
     * this method returns null.
     *
     * @param product
     *  the product to normalize
     *
     * @return
     *  the input, normalized product
     */
    private ProductDTO normalizeProduct(ProductDTO product) {
        if (product == null) {
            return null;
        }

        // Make sure the UUID is null, otherwise Hibernate thinks these are detached entities.
        product.setUuid(null);

        // Multiplication has already happened on the upstream candlepin. set this to 1
        // so we can use multipliers on local products if necessary.
        product.setMultiplier(1L);

        // Update attached content and ensure it isn't malformed
        if (product.getProductContent() != null) {
            for (ProductDTO.ProductContentDTO pc : product.getProductContent()) {
                ContentDTO content = pc.getContent();

                // Clear the UUID
                content.setUuid(null);

                // Fix the vendor string if it is/was cleared (BZ 990113)
                if (StringUtils.isBlank(content.getVendor())) {
                    content.setVendor("unknown");
                }

                // On standalone servers we will set metadata expire to 1 second so
                // clients an immediately get changes to content when published on the
                // server. We would use 0, but the client plugin interprets this as unset
                // and ignores it completely resulting in the default yum values being
                // used.
                //
                // We know this is a standalone server due to the fact that import is
                // being used, so there is no need to guard this behavior.
                content.setMetadataExpiration(1L);
            }
        }

        // Update children products
        this.normalizeProduct(product.getDerivedProduct());

        if (product.getProvidedProducts() != null) {
            product.getProvidedProducts().forEach(this::normalizeProduct);
        }

        return product;
    }

    /**
     * Fetches the product from the backing manifest, if it exists. If the product does not exist
     * in the manifest, this method returns null. If the product exists and is loaded successfully,
     * it will be added to the cache for future reads/imports.
     *
     * @param productId
     *  the ID of the product to fetch from the manifest
     *
     * @throws IOException
     *  if an unexpected exception occurs while reading product data from the manifest
     *
     * @return
     *  the loaded product if present in the manifest; null otherwise
     */
    private ProductDTO readFromManifest(String productId) throws IOException {
        log.debug("loading product from manifest: {}", productId);

        File pfile = new File(this.productDir, productId + PRODUCT_FILE_SUFFIX);
        if (!pfile.exists()) {
            return null;
        }

        try (Reader reader = new FileReader(pfile)) {
            ProductDTO product = this.mapper.readValue(reader, ProductDTO.class);
            this.normalizeProduct(product);
            this.resolveChildren(product);

            this.cache.put(product.getId(), product);
            return product;
        }
    }

    /**
     * Resolves the product ID to a product from cache or the manifest. If a matching product could
     * not be found, this method returns null.
     *
     * @param productId
     *  the product ID to resolve to a product instance
     *
     * @throws IOException
     *  if an unexpected exception occurs while loading product data from the manifest
     *
     * @return
     *  the resolved product, or null if the product ID could not be resolved
     */
    private ProductDTO resolveProductId(String productId) throws IOException {
        // Check if the product already exists in the cache
        ProductDTO product = this.cache.get(productId);
        if (product != null) {
            return product;
        }

        // Check if we have a product definition in the manifest
        product = this.readFromManifest(productId);
        if (product != null) {
            return product;
        }

        return null;
    }

    /**
     * Resolves the children of the given product, ensuring they reference a product defined in the
     * manifest, and logging warnings or errors if they do not.
     *
     * @param product
     *  the product for which to resolve children product references
     *
     * @return
     *  the input product
     */
    private ProductDTO resolveChildren(ProductDTO product) throws IOException {
        if (product == null) {
            return null;
        }

        // TODO: This whole mess becomes a lot simpler if we can error when a product references
        // another product that isn't present in the manifest definitions.

        // Resolve derived products
        ProductDTO derived = product.getDerivedProduct();
        if (derived != null) {
            ProductDTO resolved = this.resolveProductId(derived.getId());

            if (resolved != null) {
                if (!resolved.equals(derived)) {
                    // If the two products aren't equal, we could pass this down to later ops in
                    // the process, but we'll just end up churning on it and using whatever happens
                    // to come in last. Instead, set the derived product to what we resolved it to
                    // from the manifest (which has the nice side effect of normalizing it as well).

                    log.warn("Imported product references a derived product that differs from the " +
                        "manifest definition. Using manifest definition instead.\n" +
                        "Product: {}\nDerived: {}\nManifest: {}\n", product, derived, resolved);
                }

                product.setDerivedProduct(resolved);
            }
            else {
                // If we weren't able to load the product from the manifest, we have a dangling
                // product definition. Depending on how the code later in the import op handles this
                // state, this may end up being a severe failure. At the time of writing, the
                // refresher will resolve this state, but this is still not a good place to be. Yell
                // very loudly about it.

                log.warn("Imported product references a derived product not defined in the manifest\n" +
                    "Product: {}\nDerived: {}\n", product, derived);

                this.resolveChildren(derived);
            }
        }

        // Resolve provided products
        Collection<ProductDTO> providedProducts = product.getProvidedProducts();
        if (providedProducts != null) {
            List<ProductDTO> resolvedProvidedProducts = new ArrayList<>();

            for (ProductDTO provided : providedProducts) {
                ProductDTO resolved = this.resolveProductId(provided.getId());

                if (resolved != null) {
                    if (!resolved.equals(provided)) {
                        // If the two products aren't equal, we could pass this down to later ops in
                        // the process, but we'll just end up churning on it and using whatever happens
                        // to come in last. Instead, set the provided product to what we resolved it to
                        // from the manifest (which has the nice side effect of normalizing it as well).

                        log.warn("Imported product references a provided product that differs from the " +
                            "manifest definition. Using manifest definition instead.\n" +
                            "Product: {}\nDerived: {}\nManifest: {}\n", product, provided, resolved);
                    }

                    resolvedProvidedProducts.add(resolved);
                }
                else {
                    // If we weren't able to load the product from the manifest, we have a dangling
                    // product definition. Depending on how the code later in the import op handles this
                    // state, this may end up being a severe failure. At the time of writing, the
                    // refresher will resolve this state, but this is still not a good place to be. Yell
                    // very loudly about it.

                    log.warn("Imported product references a provided product not defined in the manifest\n" +
                        "Product: {}\nDerived: {}\n", product, provided);

                    this.resolveChildren(provided);
                    resolvedProvidedProducts.add(provided);
                }
            }

            product.setProvidedProducts(resolvedProvidedProducts);
        }

        return product;
    }

    /**
     * Imports the specified product and any of its necessary children from the backing manifest. If
     * the does not exist in the manifest or cannot be imported, this method throws an exception.
     *
     * @param productId
     *  the ID of the product to import
     *
     * @throws IOException
     *  if the product exists in the manifest, but cannot be loaded for any reason
     *
     * @throws SyncDataFormatException
     *  if the specified product does not exist in the manifest
     *
     * @return
     *  the imported product
     */
    public ProductDTO importProduct(String productId) throws IOException, SyncDataFormatException {
        ProductDTO product = this.resolveProductId(productId);
        if (product == null) {
            throw new SyncDataFormatException(this.i18n.tr("Unable to find product with ID: {0}", productId));
        }

        return product;
    }

    /**
     * Imports all known products from the manifest, returning them as a map of product ID to
     * product DTO. If the manifest contains no products, this method returns an empty map.
     *
     * @throws IOException
     *  if an unexpected error occurs while loading product data from the manifest
     *
     * @return
     *  a mapping of products defined in the manifest
     */
    public Map<String, ProductDTO> importProductMap() throws IOException {
        File[] files = this.productDir.listFiles();

        Map<String, ProductDTO> output = new HashMap<>();
        Pattern fnPattern = Pattern.compile("^(.*)" + Pattern.quote(PRODUCT_FILE_SUFFIX));

        try {
            for (File candidate : files) {
                Matcher matcher = fnPattern.matcher(candidate.getName());
                if (matcher.matches()) {
                    ProductDTO product = this.importProduct(matcher.group(1));
                    output.put(product.getId(), product);
                }
            }
        }
        catch (SyncDataFormatException e) {
            // Given that we're feeding it the product IDs from filenames, this shouldn't ever happen
            throw new RuntimeException(e);
        }

        return output;
    }

}
