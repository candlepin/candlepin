/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.hostedtest;

import org.candlepin.model.Consumer;
import org.candlepin.model.Owner;
import org.candlepin.model.dto.ContentData;
import org.candlepin.model.dto.ProductContentData;
import org.candlepin.model.dto.ProductData;
import org.candlepin.model.dto.Subscription;
import org.candlepin.service.SubscriptionServiceAdapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;



/**
 * The HostedTestSubscriptionServiceAdapter class is used to provide an
 * in-memory upstream source for subscriptions when candlepin is run in hosted
 * mode, while it is built with candlepin, it is not packaged in candlepin.war,
 * as the only purpose of this class is to support spec tests.
 */
public class HostedTestSubscriptionServiceAdapter implements SubscriptionServiceAdapter {
    private static Logger log = LoggerFactory.getLogger(HostedTestSubscriptionServiceAdapter.class);

    // Impl note:
    // These are static due to a limitation with our customizable modules that allows guice to make
    // multiple instances of this class rather than letting us create a singleton.

    // Subscription mapping. Subscriptions themselves are mapped by subscription ID to DTO. Since
    // we don't store owners (orgs) at this level, we just maintain a mapping of owner keys
    // (account) to subscription IDs.
    protected static Map<String, Subscription> subscriptions;
    protected static Map<String, Set<String>> ownerSubscriptionMap;

    // At the time of writing, upstream product and content data is global; no need to separate these
    // by org. Mapped by RHID to DTO
    protected static Map<String, ProductData> productData;
    protected static Map<String, ContentData> contentData;

    // These are used to provide reverse lookups from child objects back to their parents
    protected static Map<String, Set<String>> contentProductMap;
    protected static Map<String, Set<String>> productSubscriptionMap;

    static {
        subscriptions = new HashMap<String, Subscription>();
        ownerSubscriptionMap = new HashMap<String, Set<String>>();

        productData = new HashMap<String, ProductData>();
        contentData = new HashMap<String, ContentData>();

        contentProductMap = new HashMap<String, Set<String>>();
        productSubscriptionMap = new HashMap<String, Set<String>>();
    }

    /**
     * Creates a new HostedTestSubscriptionServiceAdapter instance
     */
    public HostedTestSubscriptionServiceAdapter() {
        // Intentionally left empty
    }

    /**
     * Clears all data for this service adapter
     */
    public void clearData() {
        subscriptions.clear();
        ownerSubscriptionMap.clear();
        productData.clear();
        contentData.clear();
        contentProductMap.clear();
        productSubscriptionMap.clear();
    }

    // Methods required by the SubscriptionServiceAdapter interface
    /**
     * {@inheritDoc}
     */
    @Override
    public List<Subscription> getSubscriptions(Owner owner) {
        if (owner == null || owner.getKey() == null) {
            throw new IllegalArgumentException("owner is null or does not have a valid key");
        }

        Set<String> subIds = this.ownerSubscriptionMap.get(owner.getKey());
        List<Subscription> subs = new ArrayList<Subscription>(subIds != null ? subIds.size() + 1 : 1);

        if (subIds != null) {
            for (String sid : subIds) {
                Subscription subscription = this.subscriptions.get(sid);

                if (subscription == null) {
                    // Sanity check; this shouldn't happen
                    throw new IllegalStateException("No subscription found for subscription ID: " + sid);
                }

                subs.add(subscription);
            }
        }

        return subs;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getSubscriptionIds(Owner owner) {
        if (owner == null || owner.getKey() == null) {
            throw new IllegalArgumentException("owner is null or does not have a valid key");
        }

        Set<String> subIds = this.ownerSubscriptionMap.get(owner.getKey());
        return subIds != null ? new ArrayList<String>(subIds) : new ArrayList<String>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Subscription getSubscription(String subscriptionId) {
        return subscriptions.get(subscriptionId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Subscription> getSubscriptions() {
        return new ArrayList<Subscription>(subscriptions.values());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Subscription> getSubscriptions(ProductData product) {
        if (product == null || product.getId() == null) {
            throw new IllegalArgumentException("product is null or lacks a product ID");
        }

        Set<String> subIds = productSubscriptionMap.get(product.getId());
        List<Subscription> subs = new ArrayList<Subscription>(subIds != null ? subIds.size() + 1 : 1);

        if (subIds != null) {
            for (String sid : subIds) {
                Subscription subscription = subscriptions.get(sid);

                if (subscription == null) {
                    // Sanity check; this shouldn't happen
                    throw new IllegalStateException("No subscription found for subscription ID: " + sid);
                }

                subs.add(subscription);
            }
        }

        return subs;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Subscription createSubscription(Subscription dto) {
        if (dto == null || dto.getId() == null || dto.getId().trim().isEmpty()) {
            throw new IllegalArgumentException("dto is null or does not have a valid ID");
        }

        if (dto.getOwner() == null || dto.getOwner().getKey() == null ||
            dto.getOwner().getKey().trim().isEmpty()) {

            throw new IllegalArgumentException("dto contains incomplete owner information");
        }

        if (subscriptions.containsKey(dto.getId())) {
            throw new IllegalStateException("subscription already exists: " + dto.getId());
        }

        // Resolve product references
        dto = this.resolveSubscriptionProductRefs(dto);

        // Store subscription
        subscriptions.put(dto.getId(), dto);

        // Update product=>subscription mappings
        this.updateProductSubscriptionMapping(dto.getId());

        // Update owner=>subscription mapping
        this.updateSubscriptionOwnerMapping(dto.getId());

        // return
        return dto;
    }

    /**
     * Updates the subscription with the given subscription ID using the data from the provided
     * DTO. If a subscription with the given ID cannot be found, an exception is thrown.
     *
     * @param subscriptionId
     *  The ID of the subscription to update
     *
     * @param dto
     *  The DTO containing the updates to apply to the subscription
     *
     * @throws IllegalArgumentException
     *  if the given subscription ID or dto is null
     *
     * @throws IllegalStateException
     *  if a subscription with the given ID cannot be found
     *
     * @return
     *  The updated subscription
     */
    public Subscription updateSubscription(String subscriptionId, Subscription dto) {
        if (subscriptionId == null) {
            throw new IllegalArgumentException("subscriptionId is null");
        }

        if (dto == null) {
            throw new IllegalArgumentException("dto is null");
        }

        Subscription sdata = subscriptions.get(subscriptionId);
        if (sdata == null) {
            String msg = String.format("Unable to find a subscription with ID \"%s\"", subscriptionId);
            throw new IllegalStateException(msg);
        }

        // Resolve the DTO's product references
        dto = this.resolveSubscriptionProductRefs(dto);

        // Check if the owner has been specified and, if so, if it's complete.
        if (dto.getOwner() != null && (dto.getOwner().getKey() == null ||
            dto.getOwner().getKey().trim().isEmpty())) {

            throw new IllegalArgumentException("dto contains incomplete owner information");
        }

        // Apply changes
        if (dto.getOwner() != null) {
            sdata.setOwner(dto.getOwner());
        }

        if (dto.getProduct() != null) {
            sdata.setProduct(dto.getProduct());
        }

        // Impl note:
        // This is a special case where we need to always set the value, since using null to
        // represent no-change would prevent unsetting this value, or would require a special
        // product representation (which is arguably worse than just specially allowing null)
        sdata.setDerivedProduct(dto.getDerivedProduct());

        if (dto.getProvidedProducts() != null) {
            sdata.setProvidedProducts(dto.getProvidedProducts());
        }

        if (dto.getDerivedProvidedProducts() != null) {
            sdata.setDerivedProvidedProducts(dto.getDerivedProvidedProducts());
        }

        if (dto.getBranding() != null) {
            sdata.setBranding(dto.getBranding());
        }

        if (dto.getQuantity() != null) {
            sdata.setQuantity(dto.getQuantity());
        }

        if (dto.getStartDate() != null) {
            sdata.setStartDate(dto.getStartDate());
        }

        if (dto.getEndDate() != null) {
            sdata.setEndDate(dto.getEndDate());
        }

        if (dto.getContractNumber() != null) {
            sdata.setContractNumber(!dto.getContractNumber().isEmpty() ? dto.getContractNumber() : null);
        }

        if (dto.getAccountNumber() != null) {
            sdata.setAccountNumber(!dto.getAccountNumber().isEmpty() ? dto.getAccountNumber() : null);
        }

        if (dto.getModified() != null) {
            sdata.setModified(dto.getModified());
        }

        if (dto.getOrderNumber() != null) {
            sdata.setOrderNumber(dto.getOrderNumber());
        }

        if (dto.getUpstreamPoolId() != null) {
            sdata.setUpstreamPoolId(!dto.getUpstreamPoolId().isEmpty() ? dto.getUpstreamPoolId() : null);
        }

        if (dto.getUpstreamEntitlementId() != null) {
            sdata.setUpstreamEntitlementId(!dto.getUpstreamEntitlementId().isEmpty() ?
                dto.getUpstreamEntitlementId() : null);
        }

        if (dto.getUpstreamConsumerId() != null) {
            sdata.setUpstreamConsumerId(!dto.getUpstreamConsumerId().isEmpty() ?
                dto.getUpstreamConsumerId() : null);
        }

        if (dto.getCertificate() != null) {
            sdata.setCertificate(dto.getCertificate());
        }

        if (dto.getCdn() != null) {
            sdata.setCdn(dto.getCdn());
        }

        // Update mappings
        this.updateProductSubscriptionMapping(subscriptionId);
        this.updateSubscriptionOwnerMapping(subscriptionId);

        return sdata;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteSubscription(Subscription dto) {
        if (dto == null || dto.getId() == null || dto.getId().trim().isEmpty()) {
            throw new IllegalArgumentException("dto is null or does not have a valid ID");
        }

        this.deleteSubscription(dto.getId());
    }

    public Subscription deleteSubscription(String subscriptionId) {
        if (subscriptionId == null) {
            throw new IllegalArgumentException("subscriptionId is null");
        }

        if (!subscriptions.containsKey(subscriptionId)) {
            String msg = String.format("Unable to find a subscription with ID \"%s\"", subscriptionId);
            throw new IllegalStateException(msg);
        }

        Subscription sdata = subscriptions.remove(subscriptionId);

        // Remove any product=>subscription mappings for this subscription ID
        for (Set<String> subIds : productSubscriptionMap.values()) {
            subIds.remove(subscriptionId);
        }

        // Remove owner=>subscription mappings
        // Impl note: we hit all of the owners, since there's no guarantee we'll still have a
        // valid owner key on the subscription object.
        Iterator<Map.Entry<String, Set<String>>> ei = ownerSubscriptionMap.entrySet().iterator();
        while (ei.hasNext()) {
            Map.Entry<String, Set<String>> entry = ei.next();
            Set<String> subIds = entry.getValue();

            if (subIds.remove(subscriptionId) && subIds.isEmpty()) {
                ei.remove();
            }
        }

        return sdata;
    }

    /**
     * Resolves and corrects the product references on the specified subscription DTO. If the DTO
     * contains invalid product references, this method throws an exception.
     *
     * @param dto
     *  The subscription DTO on which to resolve product references
     *
     * @throws IllegalArgumentException
     *  if the given DTO is null
     *
     * @throws IllegalStateException
     *  if the DTO has null product references or references products which does not exist
     *
     * @return
     *  The updated subscription DTO
     */
    protected Subscription resolveSubscriptionProductRefs(Subscription dto) {
        if (dto == null) {
            throw new IllegalArgumentException("dto is null or does not have a valid ID");
        }

        // product
        if (dto.getProduct() != null) {
            dto.setProduct(this.resolveSubscriptionProduct(dto.getProduct()));
        }

        // derived product
        if (dto.getDerivedProduct() != null) {
            dto.setDerivedProduct(this.resolveSubscriptionProduct(dto.getDerivedProduct()));
        }

        // provided products
        if (dto.getProvidedProducts() != null) {
            Set<ProductData> resolved = new HashSet<ProductData>();

            for (ProductData provided : dto.getProvidedProducts()) {
                resolved.add(this.resolveSubscriptionProduct(provided));
            }

            dto.setProvidedProducts(resolved);
        }

        // derived provided products
        if (dto.getDerivedProvidedProducts() != null) {
            Set<ProductData> resolved = new HashSet<ProductData>();

            for (ProductData provided : dto.getDerivedProvidedProducts()) {
                resolved.add(this.resolveSubscriptionProduct(provided));
            }

            dto.setDerivedProvidedProducts(resolved);
        }

        return dto;
    }

    /**
     * Resolves the product specified by the given product DTO to one mapped by this adapter. If
     * the DTO is null, lacks an ID or references a product that doesn't exist, this method throws
     * an exception.
     *
     * @param dto
     *  The product DTO to resolve
     *
     * @throws IllegalStateException
     *  if the DTO cannot be resolved to an existing product
     *
     * @return
     *  the resolved product
     */
    private ProductData resolveSubscriptionProduct(ProductData dto) {
        if (dto == null || dto.getId() == null) {
            throw new IllegalStateException("Subscription contains a malformed product reference");
        }

        if (!this.productData.containsKey(dto.getId())) {
            throw new IllegalStateException("Subscription references a non-existent product: " + dto.getId());
        }

        return productData.get(dto.getId());
    }

    /**
     * Updates the product=>subscription mappings for the specified subscription ID. If the
     * subscription contains invalid product mappings, this method throws an exception.
     *
     * @param subscriptionId
     *  The ID of the subscription for which to update product=>subscription mappings
     *
     * @throws IllegalArgumentException
     *  if subscriptionId is null or invalid
     *
     * @throws IllegalStateException
     *  if the subscription contains malformed product references
     */
    protected void updateProductSubscriptionMapping(String subscriptionId) {
        if (subscriptionId == null || !subscriptions.containsKey(subscriptionId)) {
            throw new IllegalArgumentException("subscriptionId is null or invalid");
        }

        Subscription sub = subscriptions.get(subscriptionId);
        Set<String> productIds = new HashSet<String>();

        if (sub.getProduct() != null) {
            productIds.add(sub.getProduct().getId());
        }

        // derived product
        if (sub.getDerivedProduct() != null) {
            productIds.add(sub.getDerivedProduct().getId());
        }

        // provided products
        if (sub.getProvidedProducts() != null) {
            for (ProductData provided : sub.getProvidedProducts()) {
                // If this is a null ref, we'll throw an exception later in the validation step
                productIds.add(provided != null ? provided.getId() : null);
            }
        }

        // derived provided products
        if (sub.getDerivedProvidedProducts() != null) {
            for (ProductData provided : sub.getDerivedProvidedProducts()) {
                // If this is a null ref, we'll throw an exception later in the validation step
                productIds.add(provided != null ? provided.getId() : null);
            }
        }

        // Validate fetched product IDs...
        for (String pid : productIds) {
            if (!productData.containsKey(pid)) {
                String msg = String.format("Subscription \"%s\" contains a malformed product reference",
                    subscriptionId);

                throw new IllegalStateException(msg);
            }
        }

        // Remove mappings for products that are no longer referenced by this subscription...
        Iterator<Map.Entry<String, Set<String>>> ei = productSubscriptionMap.entrySet().iterator();
        while (ei.hasNext()) {
            Map.Entry<String, Set<String>> entry = ei.next();
            String pid = entry.getKey();
            Set<String> subIds = entry.getValue();

            if (!productIds.contains(pid) && subIds.remove(subscriptionId) && subIds.isEmpty()) {
                ei.remove();
            }
        }

        for (String pid : productIds) {
            Set<String> subIds = productSubscriptionMap.get(pid);
            if (subIds == null) {
                subIds = new HashSet<String>();
                productSubscriptionMap.put(pid, subIds);
            }

            subIds.add(subscriptionId);
        }
    }

    /**
     * Updates the owner=>subscription mappings for the specified subscription ID. If the specified
     * subscription does not exist, this method throws an exception
     *
     * @param subscriptionId
     *  The ID of the subscription for which to update owner=>subscription mappings
     *
     * @throws IllegalArgumentException
     *  if subscriptionId is null or invalid
     */
    protected void updateSubscriptionOwnerMapping(String subscriptionId) {
        if (subscriptionId == null || !subscriptions.containsKey(subscriptionId)) {
            throw new IllegalArgumentException("subscriptionId is null or invalid");
        }

        Subscription sub = subscriptions.get(subscriptionId);
        String ownerKey = sub.getOwner().getKey();

        // Clear all owner mappings for this sub
        Iterator<Map.Entry<String, Set<String>>> ei = ownerSubscriptionMap.entrySet().iterator();
        while (ei.hasNext()) {
            Map.Entry<String, Set<String>> entry = ei.next();
            Set<String> subIds = entry.getValue();

            if (subIds.remove(subscriptionId) && subIds.isEmpty()) {
                ei.remove();
            }
        }

        // Create new mapping
        Set<String> subIds = ownerSubscriptionMap.get(ownerKey);
        if (subIds == null) {
            subIds = new HashSet<String>();
            ownerSubscriptionMap.put(ownerKey, subIds);
        }
        subIds.add(subscriptionId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasUnacceptedSubscriptionTerms(Owner owner) {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendActivationEmail(String subscriptionId) {
        // method intentionally left blank
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canActivateSubscription(Consumer consumer) {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void activateSubscription(Consumer consumer, String email, String emailLocale) {
        // method intentionally left blank
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isReadOnly() {
        return false;
    }

    // Required CRUD operations for our children objects.

    /**
     * Creates (stores) the given product DTO. If the provided DTO does not have a valid product
     * ID, this method throws an exception.
     *
     * @param dto
     *  The DTO representing the product to create/store
     *
     * @throws IllegalArgumentException
     *  if the product does not contain a valid product ID
     *
     * @throws IllegalStateException
     *  if a product with the same product ID already exists
     *
     * @return
     *  the "new" product instance
     */
    public ProductData createProduct(ProductData dto) {
        if (dto == null || dto.getId() == null || dto.getId().trim().isEmpty()) {
            throw new IllegalArgumentException("dto is null or does not have a valid ID");
        }

        if (dto.getName() == null || dto.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("dto does not have a valid name");
        }

        if (this.productData.containsKey(dto.getId())) {
            throw new IllegalStateException("product already exists: " + dto.getId());
        }

        // Verify all the referenced content exists and, if so, make sure we used the content
        // that exists in our maps rather than what was received.
        if (dto.getProductContent() != null) {
            dto = this.resolveProductContentRefs(dto);
        }

        // Store product
        productData.put(dto.getId(), dto);

        // Update mapping
        this.updateContentProductMapping(dto.getId());

        return dto;
    }

    /**
     * Fetches the product data for the given product ID. If no such product could be found, this
     * method returns null
     *
     * @param productId
     *  The ID of the product to fetch
     *
     * @return
     *  The product data for the given product ID, or null if the product could not be found
     */
    public ProductData getProduct(String productId) {
        return productData.get(productId);
    }

    /**
     * Fetches the set of all known products. If there are no known products, this method returns
     * an empty set.
     *
     * @return
     *  A set of all known products
     */
    public Set<ProductData> getProducts() {
        return new HashSet<ProductData>(productData.values());
    }

    /**
     * Fetches the products for the given product IDs. If a matching product object cannot be found
     * for a given product ID, it will be silently ignored. If there are no matching product
     * objects, this method returns an empty set.
     *
     * @param productIds
     *  A collection of IDs of the product to fetch
     *
     * @return
     *  A set of product objects for the requested product IDs
     */
    public Set<ProductData> getProducts(Iterable<String> productIds) {
        Set<ProductData> output = new HashSet<ProductData>();

        if (productIds != null && productIds.iterator().hasNext()) {
            for (String productId : productIds) {
                ProductData pdata = productData.get(productId);

                if (pdata != null) {
                    output.add(pdata);
                }
            }
        }

        return output;
    }

    /**
     * Updates the product with the given product ID using the data from the provided DTO. If a
     * product with the given ID cannot be found, an exception is thrown.
     *
     * @param productId
     *  The ID of the product to update
     *
     * @param dto
     *  The DTO containing the updates to apply to the product
     *
     * @throws IllegalArgumentException
     *  if the given product ID or dto is null
     *
     * @throws IllegalStateException
     *  if a product with the given ID cannot be found
     *
     * @return
     *  The updated product
     */
    public ProductData updateProduct(String productId, ProductData dto) {
        if (productId == null) {
            throw new IllegalArgumentException("productId is null");
        }

        if (dto == null) {
            throw new IllegalArgumentException("dto is null");
        }

        ProductData pdata = productData.get(productId);
        if (pdata == null) {
            String msg = String.format("Unable to find a product with ID \"%s\"", productId);
            throw new IllegalStateException(msg);
        }

        // Resolve the DTO's content references
        dto = this.resolveProductContentRefs(dto);

        // Apply updates...
        if (dto.getName() != null) {
            pdata.setName(dto.getName());
        }

        if (dto.getMultiplier() != null) {
            pdata.setMultiplier(dto.getMultiplier());
        }

        if (dto.getAttributes() != null) {
            pdata.setAttributes(dto.getAttributes());
        }

        if (dto.getProductContent() != null) {
            pdata.setProductContent(dto.getProductContent());
            this.updateContentProductMapping(productId);
        }

        if (dto.getDependentProductIds() != null) {
            pdata.setDependentProductIds(dto.getDependentProductIds());
        }

        if (dto.isLocked() != null) {
            pdata.setLocked(dto.isLocked());
        }

        this.updateAffectedSubscriptions(productId);

        return pdata;
    }

    /**
     * Deletes the specified product. If a product with the given ID cannot be found or the product
     * is still associated with one or more subscriptions, an exception is thrown.
     *
     * @param productId
     *  The ID of the product to delete
     *
     * @throws IllegalArgumentException
     *  if the given product ID is null
     *
     * @throws IllegalStateException
     *  if a product with the given ID cannot be found or the product is still associated with one
     *  or more subscriptions
     *
     * @return
     *  The last known state of the now-deleted product
     */
    public ProductData deleteProduct(String productId) {
        if (productId == null) {
            throw new IllegalArgumentException("productId is null");
        }

        if (!productData.containsKey(productId)) {
            String msg = String.format("Unable to find a product with ID \"%s\"", productId);
            throw new IllegalStateException(msg);
        }

        if (this.productSubscriptionMap.containsKey(productId)) {
            String msg = String.format("Product is still referenced by one or more subscriptions: \"%s\"",
                productId);

            throw new IllegalStateException(msg);
        }

        ProductData pdata = productData.remove(productId);

        // Update our content=>product mappings
        if (pdata.getProductContent() != null) {
            for (ProductContentData pcdata : pdata.getProductContent()) {
                ContentData cdata = pcdata.getContent();

                if (cdata != null && contentProductMap.containsKey(cdata.getId())) {
                    Set<String> pids = contentProductMap.get(cdata.getId());

                    if (pids.remove(pdata.getId()) && pids.isEmpty()) {
                        contentProductMap.remove(cdata.getId());
                    }
                }
            }
        }

        return pdata;
    }

    /**
     * Resolves and corrects the content references on the specified product DTO. If the DTO
     * contains invalid content references, this method throws an exception.
     *
     * @param dto
     *  The product DTO on which to resolve content references
     *
     * @throws IllegalArgumentException
     *  if the given DTO is null
     *
     * @throws IllegalStateException
     *  if the DTO has null content references or references content which does not exist
     *
     * @return
     *  The updated product DTO
     */
    protected ProductData resolveProductContentRefs(ProductData dto) {
        if (dto == null) {
            throw new IllegalArgumentException("dto is null");
        }

        if (dto.getProductContent() != null) {
            for (Iterator<ProductContentData> pci = dto.getProductContent().iterator(); pci.hasNext();) {
                ProductContentData pcdata = pci.next();

                // Remove any null join objects
                if (pcdata == null) {
                    pci.remove();
                }

                // Make sure the content is present and has an ID
                if (pcdata.getContent() == null || pcdata.getContent().getId() == null) {
                    throw new IllegalStateException("Product contains a malformed content reference");
                }

                // Make sure the content exists
                String cid = pcdata.getContent().getId();
                ContentData cdata = contentData.get(cid);
                if (cdata == null) {
                    throw new IllegalStateException("Product references a non-existent content: " + cid);
                }

                // Replace the content reference with the one we currently have mapped.
                pcdata.setContent(cdata);
            }
        }

        return dto;
    }

    /**
     * Updates the content=>product mappings for the specified product ID. If the product contains
     * invalid content mappings, this method throws an exception.
     *
     * @param productId
     *  The ID of the product for which to update content=>product mappings
     *
     * @throws IllegalArgumentException
     *  if productId is null or invalid
     *
     * @throws IllegalStateException
     *  if the product contains malformed content references
     */
    protected void updateContentProductMapping(String productId) {
        if (productId == null || !productData.containsKey(productId)) {
            throw new IllegalArgumentException("productId is null or invalid");
        }

        ProductData pdata = productData.get(productId);
        Set<String> contentIds = new HashSet<String>();

        // Gather content IDs
        if (pdata.getProductContent() != null) {
            for (ProductContentData pcdata : pdata.getProductContent()) {
                if (pcdata == null || pcdata.getContent() == null ||
                    !contentData.containsKey(pcdata.getContent().getId())) {

                    String msg = String.format("Product \"%s\" contains malformed content references",
                        productId);

                    throw new IllegalStateException(msg);
                }

                contentIds.add(pcdata.getContent().getId());
            }
        }

        // Remove mappings for content that is no longer referenced by this product...
        Iterator<Map.Entry<String, Set<String>>> ei = contentProductMap.entrySet().iterator();
        while (ei.hasNext()) {
            Map.Entry<String, Set<String>> entry = ei.next();
            String cid = entry.getKey();
            Set<String> pids = entry.getValue();

            if (!contentIds.contains(cid) && pids.remove(productId) && pids.isEmpty()) {
                ei.remove();
            }
        }

        // Add/update the existing mappings
        for (String cid : contentIds) {
            Set<String> pids = contentProductMap.get(cid);
            if (pids == null) {
                pids = new HashSet<String>();
                contentProductMap.put(cid, pids);
            }

            pids.add(productId);
        }
    }

    /**
     * Replaces the ProductData reference with the correct reference on all subscriptions
     * referencing the given productId.
     *
     * @param productId
     *  The ID of the product for which to update subscriptions
     *
     * @throws IllegalArgumentException
     *  if productId is null or invalid
     */
    protected void updateAffectedSubscriptions(String productId) {
        if (productId == null || !productData.containsKey(productId)) {
            throw new IllegalArgumentException("productId is null or invalid");
        }

        if (productSubscriptionMap.containsKey(productId)) {
            ProductData pdata = productData.get(productId);

            for (String subId : productSubscriptionMap.get(productId)) {
                Subscription sub = subscriptions.get(subId);

                // product
                if (sub.getProduct() != null && productId.equals(sub.getProduct().getId())) {
                    sub.setProduct(pdata);
                }

                // derived product
                if (sub.getDerivedProduct() != null && productId.equals(sub.getDerivedProduct().getId())) {
                    sub.setDerivedProduct(pdata);
                }

                // provided products
                if (sub.getProvidedProducts() != null) {
                    // Impl note:
                    // At the time of writing, Subscription does not encapsulate its provided products
                    // collection. We do a bunch of extra work to protect us from that behavior and any
                    // potential future changes that would clean it up (like the looming DTO refactor).

                    Set<ProductData> products = new HashSet<ProductData>(sub.getProvidedProducts());
                    boolean replace = false;

                    for (Iterator<ProductData> ppi = products.iterator(); ppi.hasNext();) {
                        ProductData provided = ppi.next();

                        if (provided != null && productId.equals(provided.getId())) {
                            ppi.remove();
                            replace = true;
                        }
                    }

                    if (replace) {
                        products.add(pdata);
                        sub.setProvidedProducts(products);
                    }
                }

                // derived provided products
                if (sub.getDerivedProvidedProducts() != null) {
                    Set<ProductData> products = new HashSet<ProductData>(sub.getDerivedProvidedProducts());
                    boolean replace = false;

                    for (Iterator<ProductData> ppi = products.iterator(); ppi.hasNext();) {
                        ProductData provided = ppi.next();

                        if (provided != null && productId.equals(provided.getId())) {
                            ppi.remove();
                            replace = true;
                        }
                    }

                    if (replace) {
                        products.add(pdata);
                        sub.setDerivedProvidedProducts(products);
                    }
                }
            }
        }
    }



    /**
     * Creates (stores) the given content DTO. If the provided DTO does not have a valid content
     * ID or a content with the given ID already exists, this method throws an exception.
     *
     * @param dto
     *  The DTO representing the content to create/store
     *
     * @throws IllegalArgumentException
     *  if the content does not contain a valid content ID
     *
     * @throws IllegalStateException
     *  if a content with the same content ID already exists, or the provided content is incomplete
     *
     * @return
     *  the "new" content instance
     */
    public ContentData createContent(ContentData dto) {
        if (dto == null || dto.getId() == null || dto.getId().trim().isEmpty()) {
            throw new IllegalArgumentException("dto is null or does not have a valid ID");
        }

        if (contentData.containsKey(dto.getId())) {
            throw new IllegalStateException("content already exists: " + dto.getId());
        }

        // These fields are required by Candlepin. If we let them be null/empty, we'll get
        // exceptions during refresh
        if (dto.getName() == null || dto.getName().trim().isEmpty() ||
            dto.getType() == null || dto.getType().trim().isEmpty() ||
            dto.getLabel() == null || dto.getLabel().trim().isEmpty() ||
            dto.getVendor() == null || dto.getVendor().trim().isEmpty()) {

            throw new IllegalStateException("content is incomplete");
        }

        contentData.put(dto.getId(), dto);
        return dto;
    }

    /**
     * Fetches the content data for the given content ID. If no such content could be found, this
     * method returns null
     *
     * @param contentId
     *  The ID of the content to fetch
     *
     * @return
     *  The content data for the given content ID, or null if the content could not be found
     */
    public ContentData getContent(String contentId) {
        return contentData.get(contentId);
    }

    /**
     * Fetches the set of all known content. If there are no known content, this method returns
     * an empty set.
     *
     * @return
     *  A set of all known content
     */
    public Set<ContentData> getContent() {
        return new HashSet<ContentData>(contentData.values());
    }

    /**
     * Fetches the content for the given content IDs. If a matching content object cannot be found
     * for a given content ID, it will be silently ignored. If there are no matching content
     * objects, this method returns an empty set.
     *
     * @param contentIds
     *  A collection of IDs of the content to fetch
     *
     * @return
     *  A set of content objects for the requested content IDs
     */
    public Set<ContentData> getContent(Iterable<String> contentIds) {
        Set<ContentData> output = new HashSet<ContentData>();

        if (contentIds != null && contentIds.iterator().hasNext()) {
            for (String contentId : contentIds) {
                ContentData cdata = contentData.get(contentId);

                if (cdata != null) {
                    output.add(cdata);
                }
            }
        }

        return output;
    }

    /**
     * Updates the content with the given content ID using the data from the provided DTO. If a
     * content with the given ID cannot be found, an exception is thrown.
     *
     * @param contentId
     *  The ID of the content to update
     *
     * @param dto
     *  The DTO containing the updates to apply to the content
     *
     * @throws IllegalArgumentException
     *  if the given content ID is null
     *
     * @throws IllegalStateException
     *  if a content with the given ID cannot be found
     *
     * @return
     *  The updated content
     */
    public ContentData updateContent(String contentId, ContentData dto) {
        if (contentId == null) {
            throw new IllegalArgumentException("contentId is null");
        }

        if (dto == null) {
            throw new IllegalArgumentException("dto is null");
        }

        ContentData cdata = this.contentData.get(contentId);
        if (cdata == null) {
            String msg = String.format("Unable to find a content with ID \"%s\"", contentId);
            throw new IllegalStateException(msg);
        }

        // Apply changes
        if (dto.getType() != null) {
            cdata.setType(dto.getType());
        }

        if (dto.getLabel() != null) {
            cdata.setLabel(dto.getLabel());
        }

        if (dto.getName() != null) {
            cdata.setName(dto.getName());
        }

        if (dto.getVendor() != null) {
            cdata.setVendor(dto.getVendor());
        }

        if (dto.getContentUrl() != null) {
            cdata.setContentUrl(dto.getContentUrl());
        }

        if (dto.getRequiredTags() != null) {
            cdata.setRequiredTags(dto.getRequiredTags());
        }

        if (dto.getReleaseVersion() != null) {
            cdata.setReleaseVersion(dto.getReleaseVersion());
        }

        if (dto.getGpgUrl() != null) {
            cdata.setGpgUrl(dto.getGpgUrl());
        }

        if (dto.getMetadataExpire() != null) {
            cdata.setMetadataExpire(dto.getMetadataExpire());
        }

        if (dto.getModifiedProductIds() != null) {
            cdata.setModifiedProductIds(dto.getModifiedProductIds());
        }

        if (dto.getArches() != null) {
            cdata.setArches(dto.getArches());
        }

        if (dto.isLocked() != null) {
            cdata.setLocked(dto.isLocked());
        }

        this.updateAffectedProducts(contentId);
        return cdata;
    }

    /**
     * Deletes the specified content, removing it from any products to which it is currently
     * attached. If a content with the given ID cannot be found, an exception is thrown.
     *
     * @param contentId
     *  The ID of the content to delete
     *
     * @throws IllegalArgumentException
     *  if the given content ID is null
     *
     * @throws IllegalStateException
     *  if a content with the given ID cannot be found
     *
     * @return
     *  The last known state of the now-deleted content
     */
    public ContentData deleteContent(String contentId) {
        if (contentId == null) {
            throw new IllegalArgumentException("contentId is null");
        }

        if (!contentData.containsKey(contentId)) {
            String msg = String.format("Unable to find a content with ID \"%s\"", contentId);
            throw new IllegalStateException(msg);
        }

        ContentData cdata = contentData.remove(contentId);

        this.updateAffectedProducts(contentId);
        this.contentProductMap.remove(contentId);

        return cdata;
    }


    /**
     * Replaces or removes the ContentData reference with the correct reference on all products
     * referencing the given contentId. This will also trigger an update on all the subscriptions
     * referencing the affected products.
     *
     * @param contentId
     *  The ID of the content for which to update products
     *
     * @throws IllegalArgumentException
     *  if contentId is null
     */
    protected void updateAffectedProducts(String contentId) {
        if (contentId == null) {
            throw new IllegalArgumentException("contentId is null");
        }

        ContentData cdata = contentData.get(contentId);
        Set<String> pids = contentProductMap.get(contentId);

        if (pids != null) {
            for (String pid : pids) {
                ProductData pdata = productData.get(pid);

                if (pdata.getProductContent() == null) {
                    // sanity check; this shouldn't happen unless someone else is fiddling with our data.
                    String msg = String.format("State mismatch! content=>product mapping does not match " +
                        "product=>content mapping for product, content pair: %s, %s",
                        pid, contentId);

                    throw new IllegalStateException(msg);
                }

                Iterator<ProductContentData> pci = pdata.getProductContent().iterator();
                while (pci.hasNext()) {
                    ProductContentData pcdata = pci.next();
                    // More sanity checking...
                    if (pcdata == null) {
                        String msg = String.format("State mismatch! Product contains a null ProductContent " +
                            "reference: %s", pid);

                        throw new IllegalStateException(msg);
                    }

                    ContentData current = pcdata.getContent();
                    // Annnnd even more sanity checks...
                    if (current == null || current.getId() == null) {
                        String msg = String.format("State mismatch! Product references null content or " +
                            "content without a valid content ID: %s", pid);

                        throw new IllegalStateException(msg);
                    }

                    if (contentId.equals(current.getId())) {
                        if (cdata != null) {
                            pcdata.setContent(cdata);
                        }
                        else {
                            pci.remove();
                        }

                        // We could break here in normal circumstances, but it's safer to check all
                        // of the products in the event some out-of-band changes have been occurring.
                    }
                }

                // Update the subscriptions affected by this product change...
                this.updateAffectedSubscriptions(pid);
            }
        }
    }

    /**
     * Adds the specified content to the product. If a product with the given ID cannot be found,
     * or any of the specified content cannot be resolved, this method throws an exception.
     *
     * @param productId
     *  The ID of the product to receive the specified content
     *
     * @param contentIdMap
     *  A mapping of content ID to its respective enabled flag for the product
     *
     * @throws IllegalArgumentException
     *  if product ID is null
     *
     * @throws IllegalStateException
     *  if the product or any content cannot be found
     *
     * @return
     *  the updated product
     */
    public ProductData addContentToProduct(String productId, Map<String, Boolean> contentIdMap) {
        if (productId == null) {
            throw new IllegalArgumentException("productId is null");
        }

        ProductData pdata = productData.get(productId);
        if (pdata == null) {
            String msg = String.format("Unable to find a product with ID \"%s\"", productId);
            throw new IllegalStateException(msg);
        }

        if (contentIdMap != null && !contentIdMap.isEmpty()) {
            // Validate inbound content IDs
            for (String cid : contentIdMap.keySet()) {
                if (!contentData.containsKey(cid)) {
                    String msg = String.format("Unable to find a content with ID \"%s\"", cid);
                    throw new IllegalStateException(msg);
                }
            }

            // Update product accordingly...
            for (Map.Entry<String, Boolean> entry : contentIdMap.entrySet()) {
                String cid = entry.getKey();
                Boolean enabled = entry.getValue();

                ProductContentData pcdata = pdata.getProductContent(cid);
                if (pcdata != null) {
                    pcdata.setEnabled(enabled != null ? enabled : true);
                }
                else {
                    ContentData cdata = contentData.get(cid);
                    pdata.addContent(cdata, enabled != null ? enabled : true);
                }
            }

            this.updateContentProductMapping(productId);
            this.updateAffectedSubscriptions(productId);
        }

        return pdata;
    }

    /**
     * Removes the specified content from the product. If a product with the given ID cannot be found,
     * or any of the specified content cannot be resolved, this method throws an exception.
     *
     * @param productId
     *  The ID of the product to receive the specified content
     *
     * @param contentIds
     *  A collection of IDs of content to remove from the product
     *
     * @throws IllegalArgumentException
     *  if product ID is null
     *
     * @throws IllegalStateException
     *  if the product cannot be found
     *
     * @return
     *  the updated product
     */
    public ProductData removeContentFromProduct(String productId, Iterable<String> contentIds) {
        if (productId == null) {
            throw new IllegalArgumentException("productId is null");
        }

        ProductData pdata = productData.get(productId);
        if (pdata == null) {
            String msg = String.format("Unable to find a product with ID \"%s\"", productId);
            throw new IllegalStateException(msg);
        }

        if (contentIds != null) {
            boolean changed = false;

            // Update product accordingly...
            for (String cid : contentIds) {
                changed |= pdata.removeContent(cid);
            }

            if (changed) {
                this.updateContentProductMapping(productId);
                this.updateAffectedSubscriptions(productId);
            }
        }

        return pdata;
    }
}
