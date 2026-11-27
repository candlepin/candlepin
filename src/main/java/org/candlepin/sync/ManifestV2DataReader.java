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

import org.candlepin.controller.ContentAccessMode;
import org.candlepin.dto.manifest.v1.BrandingDTO;
import org.candlepin.dto.manifest.v1.ConsumerDTO;
import org.candlepin.dto.manifest.v1.ConsumerTypeDTO;
import org.candlepin.dto.manifest.v1.ContentDTO;
import org.candlepin.dto.manifest.v1.OwnerDTO;
import org.candlepin.dto.manifest.v1.ProductDTO;
import org.candlepin.dto.manifest.v1.SubscriptionDTO;
import org.candlepin.dto.manifest.v2.MetaDTO;
import org.candlepin.model.Owner;
import org.candlepin.util.Util;

import org.apache.commons.lang3.StringUtils;
import org.xnap.commons.i18n.I18n;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * Reading and normalization of verified v2 payloads without persistence
 * <p>
 * Resolves the complete product graph before constructing subscriptions. Missing references, cycles, and
 * inconsistent file identities are rejected before a refresher can write partial data. The v1 DTOs here are
 * internal service-adapter models, not an attempt to reinterpret the v2 JSON as v1 wire data.
 */
public class ManifestV2DataReader {
    private final ObjectMapper mapper;
    private final I18n i18n;
    private final ManifestFormatDetector formatDetector;

    /**
     * Initializes the detached payload reader
     *
     * @param mapper
     *     the manifest object mapper
     * @param i18n
     *     the import error message translator
     * @param formatDetector
     *     the raw metadata format validator
     */
    @Inject
    public ManifestV2DataReader(@Named("ImportObjectMapper") ObjectMapper mapper, I18n i18n,
        ManifestFormatDetector formatDetector) {
        this.mapper = Objects.requireNonNull(mapper);
        this.i18n = Objects.requireNonNull(i18n);
        this.formatDetector = Objects.requireNonNull(formatDetector);
    }

    /**
     * Reading of metadata, upstream consumer, products, and pool-based subscriptions
     * <p>
     * The caller must verify the signature first. Missing product and subscription directories represent an
     * empty export; missing required metadata or consumer data is an error. Quantities are always unlimited.
     *
     * @param directory
     *     the verified payload directory
     * @param owner
     *     the local owner receiving the subscriptions
     * @throws IOException
     *     if payload files cannot be read
     * @throws SyncDataFormatException
     *     if required data is missing, inconsistent, or invalid
     * @return
     *     detached data ready for certificate validation, conflict checks, and reconciliation
     */
    public ManifestV2Data read(Path directory, Owner owner) throws IOException, SyncDataFormatException {
        Objects.requireNonNull(directory);
        Objects.requireNonNull(owner);
        try {
            return this.readPayload(directory, owner);
        }
        catch (JacksonException | IllegalArgumentException e) {
            throw new SyncDataFormatException(this.i18n.tr("Malformed v2 manifest payload"));
        }
    }

    /**
     * Checks whether the given metadata file declares a version 2 manifest
     *
     * @param metadata
     *     the path to {@code meta.json}
     * @throws IOException
     *     if the metadata file cannot be read
     * @throws SyncDataFormatException
     *     if {@code manifestVersion} is explicitly present but invalid or unsupported
     * @return
     *     true if the metadata declares version 2, false if {@code manifestVersion} is absent (v1)
     */
    public boolean isManifestV2(Path metadata) throws IOException, SyncDataFormatException {
        Objects.requireNonNull(metadata);
        try (Reader reader = Files.newBufferedReader(metadata)) {
            return this.formatDetector.isV2(this.mapper.readTree(reader));
        }
    }

    private ManifestV2Data readPayload(Path directory, Owner owner) throws IOException, SyncDataFormatException {
        Path metadata = directory.resolve(ManifestConstants.META_JSON);
        this.require(Files.isRegularFile(metadata), "The archive does not contain the required meta.json file");
        this.require(this.isManifestV2(metadata), "Expected a version 2 manifest");
        MetaDTO sourceMeta = this.readValue(metadata, MetaDTO.class);
        this.require(sourceMeta.getCreated() != null && StringUtils.isNotBlank(sourceMeta.getVersion()),
            "Manifest metadata requires a creation date and Candlepin version");
        Meta meta = new Meta(sourceMeta.getVersion(), sourceMeta.getCreated(), sourceMeta.getPrincipalName(),
            sourceMeta.getWebAppPrefix(), sourceMeta.getCdnLabel());
        Path consumerFile = directory.resolve(ManifestConstants.UPSTREAM_CONSUMER).resolve(ManifestConstants.CONSUMER_JSON);
        ConsumerDTO consumer = this.readConsumer(consumerFile, meta);
        Map<String, org.candlepin.dto.manifest.v2.ProductDTO> sources = this.readProducts(directory);
        Map<String, ProductDTO> products = new HashMap<>();
        for (String id : sources.keySet()) {
            this.resolveProduct(id, sources, products, new HashSet<>());
        }
        List<SubscriptionDTO> subscriptions = this.readSubscriptions(directory, owner, consumer, products);
        return new ManifestV2Data(meta, consumer, subscriptions);
    }

    private ConsumerDTO readConsumer(Path file, Meta meta) throws IOException, SyncDataFormatException {
        this.require(Files.isRegularFile(file), "The archive does not contain upstream_consumer/consumer.json");
        org.candlepin.dto.manifest.v2.ConsumerDTO source = this.readValue(file,
            org.candlepin.dto.manifest.v2.ConsumerDTO.class);
        this.require(source != null && StringUtils.isNotBlank(source.getUuid()) && source.getType() != null &&
            StringUtils.isNotBlank(source.getType().getLabel()), "Invalid upstream consumer metadata");
        ConsumerDTO consumer = new ConsumerDTO();
        consumer.setUuid(source.getUuid());
        consumer.setName(source.getName());
        consumer.setOwner(new OwnerDTO().setId(source.getOwnerId()));
        consumer.setType(new ConsumerTypeDTO().setLabel(source.getType().getLabel()).setManifest(true));
        consumer.setUrlApi(source.getUrlApi());
        consumer.setUrlWeb(StringUtils.isBlank(source.getUrlWeb()) ? meta.getWebAppPrefix() : source.getUrlWeb());
        consumer.setContentAccessMode(ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue());
        return consumer;
    }

    private Map<String, org.candlepin.dto.manifest.v2.ProductDTO> readProducts(Path directory)
        throws IOException, SyncDataFormatException {
        Map<String, org.candlepin.dto.manifest.v2.ProductDTO> products = new LinkedHashMap<>();
        for (Path file : this.jsonFiles(directory.resolve(ManifestConstants.PRODUCTS))) {
            org.candlepin.dto.manifest.v2.ProductDTO product = this.readValue(file,
                org.candlepin.dto.manifest.v2.ProductDTO.class);
            this.require(product != null && this.fileId(file).equals(product.getId()),
                "Product file name does not match its product ID");
            this.require(StringUtils.isNotBlank(product.getName()), "Product name is required");
            products.put(product.getId(), product);
        }
        return products;
    }

    /**
     * Resolves child references before caching each product so cycles cannot escape into recursive DTO hashing
     */
    private ProductDTO resolveProduct(String id, Map<String, org.candlepin.dto.manifest.v2.ProductDTO> sources,
        Map<String, ProductDTO> products, Set<String> visiting) throws SyncDataFormatException {
        ProductDTO cached = products.get(id);
        if (cached != null) {
            return cached;
        }
        org.candlepin.dto.manifest.v2.ProductDTO source = sources.get(id);
        this.require(source != null, "Manifest references a product that is not defined");
        this.require(visiting.add(id), "Manifest contains a cyclic product graph");
        ProductDTO product = new ProductDTO();
        product.setId(id);
        product.setName(source.getName());
        product.setMultiplier(1L);
        product.setAttributes(source.getAttributes());
        product.setCreated(source.getCreated());
        product.setUpdated(source.getUpdated());
        if (source.getDerivedProduct() != null) {
            product.setDerivedProduct(this.resolveProduct(source.getDerivedProduct(), sources, products, visiting));
        }
        List<ProductDTO> provided = new ArrayList<>();
        if (source.getProvidedProducts() != null) {
            for (String child : source.getProvidedProducts()) {
                provided.add(this.resolveProduct(child, sources, products, visiting));
            }
        }
        product.setProvidedProducts(provided);
        product.setProductContent(this.readContent(source));
        List<BrandingDTO> branding = new ArrayList<>();
        if (source.getBranding() != null) {
            for (org.candlepin.dto.manifest.v2.BrandingDTO entry : source.getBranding()) {
                this.require(entry != null, "Manifest contains null branding");
                branding.add(new BrandingDTO()
                    .setProductId(entry.getProductId())
                    .setName(entry.getName())
                    .setType(entry.getType()));
            }
        }
        product.setBranding(branding);
        products.put(id, product);
        visiting.remove(id);
        return product;
    }

    private List<ProductDTO.ProductContentDTO> readContent(org.candlepin.dto.manifest.v2.ProductDTO product)
        throws SyncDataFormatException {
        List<ProductDTO.ProductContentDTO> content = new ArrayList<>();
        if (product.getProductContent() != null) {
            for (Map.Entry<String, org.candlepin.dto.manifest.v2.ContentDTO> entry :
                product.getProductContent().entrySet()) {
                org.candlepin.dto.manifest.v2.ContentDTO source = entry.getValue();
                this.require(source != null && StringUtils.isNotBlank(source.getId()) &&
                    source.getId().equals(entry.getKey()), "Content map key does not match its content ID");
                ContentDTO target = new ContentDTO();
                target.setId(source.getId());
                target.setName(source.getName());
                target.setLabel(source.getLabel());
                target.setType(source.getType());
                target.setVendor(StringUtils.isBlank(source.getVendor()) ? "unknown" : source.getVendor());
                target.setContentUrl(source.getContentUrl());
                target.setGpgUrl(source.getGpgUrl());
                target.setArches(source.getArches());
                target.setRequiredTags(source.getRequiredTags());
                target.setReleaseVersion(source.getReleaseVer());
                target.setMetadataExpiration(1L);
                content.add(new ProductDTO.ProductContentDTO(target, source.isEnabled()));
            }
        }
        return content;
    }

    private List<SubscriptionDTO> readSubscriptions(Path directory, Owner owner, ConsumerDTO consumer,
        Map<String, ProductDTO> products) throws IOException, SyncDataFormatException {
        List<SubscriptionDTO> subscriptions = new ArrayList<>();
        for (Path file : this.jsonFiles(directory.resolve(ManifestConstants.SUBSCRIPTIONS))) {
            org.candlepin.dto.manifest.v2.SubscriptionDTO source = this.readValue(file,
                org.candlepin.dto.manifest.v2.SubscriptionDTO.class);
            this.require(source != null && source.getStartDate() != null && source.getEndDate() != null &&
                !source.getEndDate().before(source.getStartDate()), "Invalid subscription dates");
            ProductDTO product = products.get(source.getProductId());
            this.require(product != null, "Subscription references a product that is not defined");
            SubscriptionDTO subscription = new SubscriptionDTO();
            // The source subscription ID is not a downstream subscription ID. Upstream pool ID is the
            // reconciliation identity; local IDs are allocated here and reused by the v2 reconciler.
            subscription.setId(Util.generateDbUUID());
            subscription.setOwner(new OwnerDTO().setId(owner.getId()).setKey(owner.getKey()));
            subscription.setUpstreamPoolId(this.fileId(file));
            subscription.setUpstreamConsumerId(consumer.getUuid());
            subscription.setQuantity(-1L);
            subscription.setProduct(product);
            subscription.setStartDate(source.getStartDate());
            subscription.setEndDate(source.getEndDate());
            subscription.setLastModified(source.getUpdated());
            subscription.setContractNumber(source.getContractNumber());
            subscription.setAccountNumber(source.getAccountNumber());
            subscriptions.add(subscription);
        }
        return subscriptions;
    }

    private <T> T readValue(Path file, Class<T> type) throws IOException {
        try (Reader reader = Files.newBufferedReader(file)) {
            return this.mapper.readValue(reader, type);
        }
    }

    private List<Path> jsonFiles(Path directory) throws IOException, SyncDataFormatException {
        if (!Files.exists(directory)) {
            return List.of();
        }
        this.require(Files.isDirectory(directory), "Manifest collection path is not a directory");
        try (Stream<Path> files = Files.list(directory)) {
            List<Path> paths = files.sorted().toList();
            for (Path file : paths) {
                this.require(Files.isRegularFile(file) && file.getFileName().toString().endsWith(ManifestConstants.JSON_SUFFIX),
                    "Manifest collection contains an unexpected entry");
                this.fileId(file);
            }
            return paths;
        }
    }

    private String fileId(Path file) throws SyncDataFormatException {
        String name = file.getFileName().toString();
        String id = name.substring(0, name.length() - ManifestConstants.JSON_SUFFIX.length());
        this.require(StringUtils.isNotBlank(id), "Manifest file has an empty ID");
        return id;
    }

    private void require(boolean valid, String message) throws SyncDataFormatException {
        if (!valid) {
            throw new SyncDataFormatException(this.i18n.tr(message));
        }
    }
}
