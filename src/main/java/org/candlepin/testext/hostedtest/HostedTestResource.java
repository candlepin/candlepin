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
package org.candlepin.testext.hostedtest;

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.server.v1.ContentDTO;
import org.candlepin.dto.api.server.v1.OwnerDTO;
import org.candlepin.dto.api.server.v1.ProductDTO;
import org.candlepin.dto.api.server.v1.SubscriptionDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.ConflictException;
import org.candlepin.exceptions.IseException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.ProductContent;
import org.candlepin.resource.util.AttachedFile;
import org.candlepin.resource.util.InfoAdapter;
import org.candlepin.service.UniqueIdGenerator;
import org.candlepin.service.model.ContentInfo;
import org.candlepin.service.model.OwnerInfo;
import org.candlepin.service.model.ProductContentInfo;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.service.model.SubscriptionInfo;

import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;


/**
 * The HostedTestResource class provides an endpoint for managing the upstream data stored by the
 * backing HostedTestDataStore class
 */
@Path("/hostedtest")
public class HostedTestResource {
    private static final Logger log = LoggerFactory.getLogger(HostedTestResource.class);

    private static final Pattern ARCHIVE_FILENAME_REGEX = Pattern.compile("\\.(?i:gz)\\z");

    private final HostedTestDataStore datastore;
    private final UniqueIdGenerator idGenerator;
    private final ModelTranslator translator;
    private final ObjectMapper mapper;

    @Inject
    public HostedTestResource(HostedTestDataStore datastore, UniqueIdGenerator idGenerator,
        ModelTranslator translator, ObjectMapper mapper) {

        this.datastore = Objects.requireNonNull(datastore);
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.translator = Objects.requireNonNull(translator);
        this.mapper = mapper;
    }

    /**
     * API to check if resource is alive
     *
     * @return always returns true
     */
    @GET
    @Path("/alive")
    @Produces(MediaType.TEXT_PLAIN)
    public Boolean isAlive() {
        return true;
    }

    /**
     * Creates or updates all of the children entities referenced by the given subscription.
     *
     * @param subscription
     *     The subscription for which to create or update children entities
     */
    private void createSubscriptionChildren(SubscriptionInfo subscription) {
        if (subscription == null) {
            throw new IllegalArgumentException("subscription is null");
        }

        // Impl note: this *must* be a LinkedHashMap to ensure that products going into it are
        // iterated in the same order to ensure we create children before their parents.
        Map<String, ProductInfo> pmap = new LinkedHashMap<>();
        Map<String, ContentInfo> cmap = new HashMap<>();

        ProductInfo product = subscription.getProduct();

        this.mapProduct(product, pmap, cmap);

        log.debug("Creating {} children entities for subscription {}", (pmap.size() + cmap.size()),
            subscription.getId());

        this.persistMappedContent(cmap);
        this.persistMappedProducts(pmap);
    }

    /**
     * Creates or updates all of the children entities referenced by the given product.
     *
     * @param product
     *     The product for which to create or update children entities
     */
    private void createProductChildren(ProductInfo product) {
        if (product == null) {
            throw new IllegalArgumentException("product is null");
        }

        Map<String, ProductInfo> pmap = new LinkedHashMap<>();
        Map<String, ContentInfo> cmap = new HashMap<>();

        this.mapProduct(product, pmap, cmap);

        // Remove the our root product from the map so the caller can control how it's persisted
        pmap.remove(product.getId());

        log.debug("Creating {} children entities for product {}", (pmap.size() + cmap.size()),
            product.getId());

        this.persistMappedProducts(pmap);
        this.persistMappedContent(cmap);
    }

    private void mapProduct(ProductInfo pinfo, Map<String, ProductInfo> pmap,
        Map<String, ContentInfo> cmap) {

        if (pinfo == null) {
            return;
        }

        if (pinfo.getId() == null || pinfo.getId().matches("\\A\\s*\\z")) {
            throw new BadRequestException("product has a null or empty product ID: " + pinfo);
        }

        this.mapProduct(pinfo.getDerivedProduct(), pmap, cmap);

        Collection<? extends ProductInfo> providedProducts = pinfo.getProvidedProducts();
        if (providedProducts != null) {
            for (ProductInfo provided : providedProducts) {
                if (provided == null) {
                    throw new BadRequestException("provided products collection contains a null product");
                }
            }

            providedProducts.forEach(provided -> this.mapProduct(provided, pmap, cmap));
        }

        Collection<? extends ProductContentInfo> productContent = pinfo.getProductContent();
        if (productContent != null) {
            for (ProductContentInfo pcinfo : productContent) {
                if (pcinfo == null) {
                    continue;
                }

                ContentInfo cinfo = pcinfo.getContent();

                if (cinfo == null) {
                    throw new BadRequestException("product contains a null content: " + pcinfo);
                }

                if (cinfo.getId() == null || cinfo.getId().matches("\\A\\s*\\z")) {
                    throw new BadRequestException("content has a null or empty content ID: " + cinfo);
                }

                cmap.put(cinfo.getId(), cinfo);
            }
        }

        pmap.put(pinfo.getId(), pinfo);
    }

    private void persistMappedProducts(Map<String, ProductInfo> pmap) {
        for (ProductInfo pinfo : pmap.values()) {
            if (this.datastore.getProduct(pinfo.getId()) != null) {
                log.debug("Updating child product: {}", pinfo.getId());
                this.datastore.updateProduct(pinfo.getId(), pinfo);
            }
            else {
                log.debug("Creating child product: {}", pinfo.getId());
                this.datastore.createProduct(pinfo);
            }
        }
    }

    private void persistMappedContent(Map<String, ContentInfo> cmap) {
        for (ContentInfo cinfo : cmap.values()) {
            if (this.datastore.getContent(cinfo.getId()) != null) {
                log.debug("Updating child content: {}", cinfo.getId());
                this.datastore.updateContent(cinfo.getId(), cinfo);
            }
            else {
                log.debug("Creating child content: {}", cinfo.getId());
                this.datastore.createContent(cinfo);
            }
        }
    }

    /**
     * Deletes all data currently maintained by the backing adapter.
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    public void clearData() {
        this.datastore.clearData();
    }

    /**
     * Creates a new owner from the subscription JSON provided. Any UUID provided in the JSON will be
     * ignored when creating the new subscription.
     *
     * @param owner
     *     An OwnerDTO object built from the JSON provided in the request
     *
     * @return The newly created Subscription object
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/owners")
    public OwnerDTO createOwner(OwnerDTO owner) {
        if (this.datastore.getOwner(owner.getKey()) != null) {
            throw new ConflictException("owner already exists: " + owner.getKey());
        }

        // Create owner object...
        OwnerInfo ownerInfo = this.datastore.createOwner(InfoAdapter.ownerInfoAdapter(owner));

        return this.translator.translate(ownerInfo, OwnerDTO.class);
    }

    // TODO: Add remaining owner CRUD operations as needed

    /**
     * Creates a new subscription from the subscription JSON provided. Any UUID provided in the JSON
     * will be ignored when creating the new subscription.
     *
     * @param createChildren
     *     whether or not children entities in the provided subscription data should be automatically
     *     created or updated before creating the new subscription
     *
     * @param subscription
     *     the subscription data to use to create the new subscription
     *
     * @return The newly created Subscription object
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/subscriptions")
    public SubscriptionDTO createSubscription(
        @QueryParam("create_children") @DefaultValue("false") boolean createChildren,
        SubscriptionDTO subscription) {

        if (subscription == null) {
            throw new BadRequestException("no subscription data provided");
        }

        if (subscription.getProduct() == null) {
            throw new BadRequestException("subscription lacks a product: " + subscription);
        }

        // Generate an ID if necessary
        if (subscription.getId() == null || subscription.getId().matches("\\A\\s*\\z")) {
            subscription.setId(this.idGenerator.generateId());
        }

        if (this.datastore.getSubscription(subscription.getId()) != null) {
            throw new ConflictException("subscription already exists: " + subscription.getId());
        }

        SubscriptionInfo subscriptionToCreate = InfoAdapter.subscriptionInfoAdapter(subscription);
        // Create the subobjects first
        if (createChildren) {
            log.debug("Persisting children received on subscription {}", subscriptionToCreate.getId());
            this.createSubscriptionChildren(subscriptionToCreate);
        }

        // Create subscription object...
        SubscriptionInfo createdSubscription = this.datastore.createSubscription(subscriptionToCreate);

        return this.translator.translate(createdSubscription, SubscriptionDTO.class);
    }

    /**
     * Lists all known subscriptions currently maintained by the subscription service.
     *
     * @return A collection of subscriptions maintained by the subscription service
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/subscriptions")
    public Stream<SubscriptionDTO> listSubscriptions() {
        return this.datastore.listSubscriptions().stream()
            .map(this.translator.getStreamMapper(SubscriptionInfo.class, SubscriptionDTO.class));
    }

    /**
     * Retrieves the subscription for the specified subscription id. If the subscription id cannot be
     * found, this method returns null.
     *
     * @param subscriptionId
     *     The id of the subscription to retrieve
     * @return The requested Subscription object, or null if the subscription could not be found
     */
    @GET
    @Path("/subscriptions/{subscription_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public SubscriptionDTO getSubscription(@PathParam("subscription_id") String subscriptionId) {
        SubscriptionInfo foundSubscription = this.datastore.getSubscription(subscriptionId);

        if (foundSubscription == null) {
            throw new NotFoundException("subscription does not exist: " + subscriptionId);
        }

        return this.translator.translate(foundSubscription, SubscriptionDTO.class);
    }

    /**
     * Updates the specified subscription with the provided subscription data.
     *
     * @param subscriptionId
     *     the ID of the subscription to update
     *
     * @param createChildren
     *     whether or not children entities in the provided subscription data should be automatically
     *     created or updated before applying the subscription changes
     *
     * @param subscription
     *     the subscription data to use to update the specified subscription
     *
     * @return the updated subscription
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/subscriptions/{subscription_id}")
    public SubscriptionDTO updateSubscription(
        @PathParam("subscription_id") String subscriptionId,
        @QueryParam("create_children") @DefaultValue("false") boolean createChildren,
        SubscriptionDTO subscription) {

        if (subscription == null) {
            throw new BadRequestException("no subscription data provided");
        }

        if (this.datastore.getSubscription(subscriptionId) == null) {
            throw new NotFoundException("subscription does not yet exist: " + subscriptionId);
        }

        SubscriptionInfo subscriptionToCreate = InfoAdapter.subscriptionInfoAdapter(subscription);
        // Create/Update sub objects, if necessary
        if (createChildren) {
            log.debug("Persisting children received on subscription {}", subscription.getId());
            this.createSubscriptionChildren(subscriptionToCreate);
        }

        // Update subscription
        SubscriptionInfo updatedSubscription = this.datastore
            .updateSubscription(subscriptionId, subscriptionToCreate);
        return this.translator.translate(updatedSubscription, SubscriptionDTO.class);
    }

    @DELETE
    @Path("/subscriptions/{subscription_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public boolean deleteSubscription(@PathParam("subscription_id") String subscriptionId) {
        return this.datastore.deleteSubscription(subscriptionId) != null;
    }

    @GET
    @Path("/products")
    @Produces(MediaType.APPLICATION_JSON)
    public Stream<ProductDTO> listProducts() {
        return this.datastore.listProducts().stream()
            .map(this.translator.getStreamMapper(ProductInfo.class, ProductDTO.class));
    }

    @GET
    @Path("/products/{product_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public ProductDTO getProduct(@PathParam("product_id") String productId) {
        ProductInfo foundProduct = this.datastore.getProduct(productId);

        if (foundProduct == null) {
            throw new NotFoundException("product does not exist: " + productId);
        }

        return this.translator.translate(foundProduct, ProductDTO.class);
    }

    @POST
    @Path("/products")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public ProductDTO createProduct(
        @QueryParam("create_children") @DefaultValue("false") boolean createChildren,
        ProductDTO product) {

        if (product == null) {
            throw new BadRequestException("product is null");
        }

        if (product.getId() == null || product.getId().isEmpty()) {
            throw new BadRequestException("product lacks a product ID: " + product);
        }

        if (this.datastore.getProduct(product.getId()) != null) {
            throw new ConflictException("product already exists: " + product.getId());
        }

        if (createChildren) {
            log.debug("Persisting children received on product {}", product.getId());
            this.createProductChildren(InfoAdapter.productInfoAdapter(product));
        }

        ProductInfo createdProduct = this.datastore.createProduct(InfoAdapter.productInfoAdapter(product));
        return this.translator.translate(createdProduct, ProductDTO.class);
    }

    @PUT
    @Path("/products/{product_id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public ProductDTO updateProduct(
        @PathParam("product_id") String productId,
        @QueryParam("create_children") @DefaultValue("false") boolean createChildren,
        ProductDTO product) {

        if (product == null) {
            throw new BadRequestException("product update is null");
        }

        if (this.datastore.getProduct(productId) == null) {
            throw new NotFoundException("product does not yet exist: " + productId);
        }

        if (createChildren) {
            log.debug("Persisting children received on product {}", product.getId());
            this.createProductChildren(InfoAdapter.productInfoAdapter(product));
        }

        ProductInfo updatedProduct = this.datastore
            .updateProduct(productId, InfoAdapter.productInfoAdapter(product));
        return this.translator.translate(updatedProduct, ProductDTO.class);
    }

    @DELETE
    @Path("/products/{product_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public boolean deleteProduct(@PathParam("product_id") String productId) {
        return this.datastore.deleteProduct(productId) != null;
    }

    @GET
    @Path("/content")
    @Produces(MediaType.APPLICATION_JSON)
    public Stream<ContentDTO> listContent() {
        return this.datastore.listContent().stream()
            .map(this.translator.getStreamMapper(ContentInfo.class, ContentDTO.class));
    }

    @GET
    @Path("/content/{content_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public ContentDTO getContent(@PathParam("content_id") String contentId) {
        ContentInfo foundContent = this.datastore.getContent(contentId);

        if (foundContent == null) {
            throw new NotFoundException("content does not exist: " + contentId);
        }

        return this.translator.translate(foundContent, ContentDTO.class);
    }

    @POST
    @Path("/content")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public ContentDTO createContent(ContentDTO content) {
        if (content == null) {
            throw new BadRequestException("content is null");
        }

        if (content.getId() == null || content.getId().isEmpty()) {
            throw new BadRequestException("content lacks a content ID: " + content);
        }

        if (this.datastore.getContent(content.getId()) != null) {
            throw new ConflictException("content already exists: " + content.getId());
        }

        ContentInfo createdContent = this.datastore.createContent(InfoAdapter.contentInfoAdapter(content));
        return this.translator.translate(createdContent, ContentDTO.class);
    }

    @PUT
    @Path("/content/{content_id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public ContentDTO updateContent(
        @PathParam("content_id") String contentId,
        ContentDTO content) {

        if (content == null) {
            throw new BadRequestException("content update is null");
        }

        if (this.datastore.getContent(contentId) == null) {
            throw new NotFoundException("content does not yet exist: " + contentId);
        }

        ContentInfo updatedContent = this.datastore
            .updateContent(contentId, InfoAdapter.contentInfoAdapter(content));
        return this.translator.translate(updatedContent, ContentDTO.class);
    }

    @DELETE
    @Path("/content/{content_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public boolean deleteContent(@PathParam("content_id") String contentId) {
        return this.datastore.deleteContent(contentId) != null;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/products/{product_id}/content")
    public ProductDTO addContentToProduct(
        @PathParam("product_id") String productId,
        Map<String, Boolean> contentMap) {

        ProductInfo pinfo = this.datastore.getProduct(productId);

        if (pinfo == null) {
            throw new NotFoundException("product not found: " + productId);
        }

        for (String contentId : contentMap.keySet()) {
            if (this.datastore.getContent(contentId) == null) {
                throw new NotFoundException("content not found: " + contentId);
            }
        }

        for (Entry<String, Boolean> entry : contentMap.entrySet()) {
            String contentId = entry.getKey();

            boolean enabled = entry.getValue() != null ?
                entry.getValue() :
                ProductContent.DEFAULT_ENABLED_STATE;

            this.datastore.addContentToProduct(productId, contentId, enabled);
        }

        ProductInfo updatedProduct = this.datastore.getProduct(productId);
        return this.translator.translate(updatedProduct, ProductDTO.class);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.WILDCARD)
    @Path("/products/{product_id}/content/{content_id}")
    public ProductDTO addContentToProduct(
        @PathParam("product_id") String productId,
        @PathParam("content_id") String contentId,
        @QueryParam("enabled") Boolean enabled) {

        // Package the params up and pass it off to our batch method
        Map<String, Boolean> contentMap = Collections.singletonMap(contentId, enabled);
        return this.addContentToProduct(productId, contentMap);
    }

    @DELETE
    @Path("/products/{product_id}/content")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ProductDTO removeContentFromProduct(@PathParam("product_id") String productId,
        Collection<String> contentIds) {

        if (this.datastore.getProduct(productId) == null) {
            throw new NotFoundException("product not found: " + productId);
        }

        if (contentIds != null) {
            for (String contentId : contentIds) {
                this.datastore.removeContentFromProduct(productId, contentId);
            }
        }

        ProductInfo updatedProduct = this.datastore.getProduct(productId);
        return this.translator.translate(updatedProduct, ProductDTO.class);
    }

    @DELETE
    @Path("/products/{product_id}/content/{content_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public ProductDTO removeContentFromProduct(@PathParam("product_id") String productId,
        @PathParam("content_id") String contentId) {

        return this.removeContentFromProduct(productId, Collections.singletonList(contentId));
    }

    private InputStream getAttachedFileInputStream(AttachedFile attached) throws IOException {
        boolean archive = ARCHIVE_FILENAME_REGEX.matcher(attached.getFilename("")).find();

        InputStream istream = attached.getInputStream();
        return archive ? new GZIPInputStream(istream) : istream;
    }

    private <T> void processAttachedFile(AttachedFile attached, TypeReference<T> typeref,
        Consumer<T> consumer) {

        try (InputStream istream = this.getAttachedFileInputStream(attached)) {
            JsonParser parser = this.mapper.createParser(istream);

            switch (parser.nextToken()) {
                case START_ARRAY:
                    // iterate through objects in the array until we hit END_ARRAY
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        T entity = parser.readValueAs(typeref);
                        consumer.accept(entity);
                    }
                    break;

                case START_OBJECT:
                    // read entity object straight up
                    T entity = parser.readValueAs(typeref);
                    consumer.accept(entity);
                    break;

                default:
                    throw new BadRequestException("Unable to deserialize attached file: " +
                        "Malformed JSON received");
            }
        }
        catch (IOException e) {
            log.error("Unable to deserialize attached file:", e);
            throw new IseException("Unable to deserialize attached file", e);
        }
    }

    @POST
    @Path("import/subscriptions")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public String importSubscriptions(MultipartInput input) {
        AttachedFile attached = AttachedFile.getAttachedFile(input);

        TypeReference<SubscriptionDTO> typeref = new TypeReference<SubscriptionDTO>() {};

        AtomicInteger created = new AtomicInteger();
        AtomicInteger updated = new AtomicInteger();

        this.processAttachedFile(attached, typeref, subscription -> {
            if (subscription == null) {
                // Silently ignore null entries
                return;
            }

            // Impl note: if the subscription lacks an ID, this will return null and we'll create
            // it, generating an ID for the subscription in the process.
            if (this.datastore.getSubscription(subscription.getId()) == null) {
                this.createSubscription(true, subscription);
                created.incrementAndGet();
            }
            else {
                this.updateSubscription(subscription.getId(), true, subscription);
                updated.incrementAndGet();
            }
        });

        return String.format("%d subscriptions imported (%d created; %d updated)\n",
            created.get() + updated.get(), created.get(), updated.get());
    }

    @POST
    @Path("import/products")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public String importProducts(MultipartInput input) {
        AttachedFile attached = AttachedFile.getAttachedFile(input);

        TypeReference<ProductDTO> typeref = new TypeReference<ProductDTO>() {};

        AtomicInteger created = new AtomicInteger();
        AtomicInteger updated = new AtomicInteger();

        this.processAttachedFile(attached, typeref, product -> {
            if (product == null) {
                // Silently ignore null entries
                return;
            }

            if (product.getId() == null) {
                throw new BadRequestException("product data contains one or more products lacking an ID");
            }

            if (this.datastore.getProduct(product.getId()) == null) {
                this.createProduct(true, product);
                created.incrementAndGet();
            }
            else {
                this.updateProduct(product.getId(), true, product);
                updated.incrementAndGet();
            }
        });

        return String.format("%d products imported (%d created; %d updated)\n",
            created.get() + updated.get(), created.get(), updated.get());
    }

    @POST
    @Path("cloud/offers")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public void addCloudOfferToProductMapping(String json) {
        JsonNode root = this.mapper.readTree(json);
        String offerId = root.get("cloudOfferId").asText();
        if (offerId == null) {
            throw new BadRequestException("cloud offer ID is null");
        }

        String cloudOfferType  = root.get("cloudOfferType").asText();
        if (cloudOfferType == null) {
            throw new BadRequestException("cloud offer type is null");
        }

        JsonNode productIdsNode = root.get("productIds");
        if (productIdsNode == null) {
            throw new BadRequestException("productIds is null");
        }

        List<String> productIds = new ArrayList<>();
        productIdsNode.valueStream().forEach(prod -> productIds.add(prod.asText()));

        this.datastore.setProductIdsForCloudOfferId(offerId, productIds);
        this.datastore.setOfferTypeForCloudOfferId(offerId, cloudOfferType);
    }

    @POST
    @Path("cloud/accounts")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public void addCloudAccountToOwnerMapping(String json) {
        JsonNode root = this.mapper.readTree(json);
        String cloudAccountId = root.get("cloudAccountId").asText();
        if (cloudAccountId == null) {
            throw new BadRequestException("cloud account ID is null");
        }

        String ownerId = root.get("ownerId").asText();
        if (ownerId == null) {
            throw new BadRequestException("owner ID is null");
        }

        this.datastore.setCloudAccountIdForOwnerKey(cloudAccountId, ownerId);
    }

}
