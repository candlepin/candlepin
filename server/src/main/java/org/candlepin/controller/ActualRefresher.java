/*
 *  Copyright (c) 2009 - ${YEAR} Red Hat, Inc.
 *
 *  This software is licensed to you under the GNU General Public License,
 *  version 2 (GPLv2). There is NO WARRANTY for this software, express or
 *  implied, including the implied warranties of MERCHANTABILITY or FITNESS
 *  FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 *  along with this software; if not, see
 *  http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 *  Red Hat trademarks are not licensed under GPLv2. No permission is
 *  granted to use or replicate Red Hat trademarks that are incorporated
 *  in this software or its documentation.
 */
package org.candlepin.controller;

import org.candlepin.model.AbstractHibernateObject;
import org.candlepin.model.Branding;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.OwnerContentCurator;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.service.model.BrandingInfo;
import org.candlepin.service.model.ContentInfo;
import org.candlepin.service.model.ProductContentInfo;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.service.model.ServiceModelInfo;
import org.candlepin.service.model.SubscriptionInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.Collectors;



/**
 * The ActualRefresher class does actual refresh operations, as opposed to calling into other
 * classes to do the refresh work. It does that, too, but the Refresher name is already in use
 * and this is PoC code so I'm not going to change the world to call this something a little
 * nicer... yet.
 *
 * This is looking like it'll almost entirely replace the ImportedEntityCompiler, and its
 * primary output from the execute operation is going to be a beefier version of ImportResult
 * that allows for looking up things directly rather than just spitting out maps.
 */
public class ActualRefresher {

    private static Logger log = LoggerFactory.getLogger(ImportedEntityCompiler.class);

    private ContentCurator contentCurator;
    private OwnerContentCurator ownerContentCurator;
    private OwnerProductCurator ownerProductCurator;
    private ProductCurator productCurator;

    private Map<String, SubscriptionInfo> importSubscriptions;
    private Map<String, ProductInfo> importProducts;
    private Map<String, ContentInfo> importContent;

    /**
     * Creates a new ActualRefresher
     */
    public ActualRefresher(OwnerProductCurator ownerProductCurator,
        OwnerContentCurator ownerContentCurator) {

        // TODO: Actually use the proper curators in a real implementation

        // this.contentCurator = Objects.requireNonNull(contentCurator);
        this.ownerContentCurator = Objects.requireNonNull(ownerContentCurator);
        this.ownerProductCurator = Objects.requireNonNull(ownerProductCurator);
        // this.productCurator = Objects.requireNonNull(productCurator);

        this.importSubscriptions = new HashMap<>();
        this.importProducts = new HashMap<>();
        this.importContent = new HashMap<>();
    }

    /**
     * Adds the specified subscriptions to this refresher. If a given subscription has already
     * been added, but differs from the existing version, a warning will be generated and the
     * previous entry will be replaced. Products and content attached to the subscriptions will be
     * mapped by this compiler. Null subscriptions will be silently ignored.
     *
     * @param subscriptions
     *  The subscriptions to add to this compiler
     *
     * @throws IllegalArgumentException
     *  if any provided subscription does not contain a valid subscription ID
     */
    public void addSubscriptions(SubscriptionInfo... subscriptions) {
        this.addSubscriptions(Arrays.asList(subscriptions));
    }

    /**
     * Adds the specified subscriptions to this refresher. If a given subscription has already
     * been added, but differs from the existing version, a warning will be generated and the
     * previous entry will be replaced. Products and content attached to the subscriptions will be
     * mapped by this compiler. Null subscriptions will be silently ignored.
     *
     * @param subscriptions
     *  The subscriptions to add to this compiler
     *
     * @throws IllegalArgumentException
     *  if any provided subscription does not contain a valid subscription ID
     */
    public void addSubscriptions(Collection<? extends SubscriptionInfo> subscriptions) {
        if (subscriptions != null) {
            for (SubscriptionInfo subscription : subscriptions) {
                if (subscription == null) {
                    continue;
                }

                if (subscription.getId() == null || subscription.getId().isEmpty()) {
                    String msg = String.format(
                        "subscription does not contain a mappable Red Hat ID: %s", subscription);

                    log.error(msg);
                    throw new IllegalArgumentException(msg);
                }

                SubscriptionInfo existing = this.importSubscriptions.get(subscription.getId());
                if (existing != null && !existing.equals(subscription)) {
                    log.warn("Multiple versions of the same subscription received during refresh; " +
                        "discarding previous: {} => {}, {}", subscription.getId(), existing, subscription);
                }

                this.importSubscriptions.put(subscription.getId(), subscription);

                // Add any products attached to this subscription...
                this.addProducts(subscription.getProduct());
                this.addProducts(subscription.getDerivedProduct());
            }
        }
    }

    /**
     * Adds the specified products to this refresher. If a given product has already
     * been added, but differs from the existing version, a warning will be generated and the
     * previous entry will be replaced. Content attached to the products will be mapped by this
     * compiler. Null products will be silently ignored.
     *
     * @param products
     *  The products to add to this compiler
     *
     * @throws IllegalArgumentException
     *  if any provided product does not contain a valid product ID
     */
    public void addProducts(ProductInfo... products) {
        this.addProducts(Arrays.asList(products));
    }

    /**
     * Adds the specified products to this refresher. If a given product has already
     * been added, but differs from the existing version, a warning will be generated and the
     * previous entry will be replaced.
     * <p></p>
     * Child entities attached to the product (such as provided products and content) will also be
     * added to this refresher. Null products and children will be silently discarded.
     *
     * @param products
     *  The products to add to this compiler
     *
     * @throws IllegalArgumentException
     *  if any provided product does not contain a valid product ID
     */
    public void addProducts(Collection<? extends ProductInfo> products) {
        if (products != null) {
            for (ProductInfo product : products) {
                if (product == null) {
                    continue;
                }

                if (product.getId() == null || product.getId().isEmpty()) {
                    String msg = String.format("product does not contain a mappable Red Hat ID: %s", product);

                    log.error(msg);
                    throw new IllegalArgumentException(msg);
                }

                ProductInfo existing = this.importProducts.get(product.getId());
                if (existing != null && !existing.equals(product)) {
                    log.warn("Multiple versions of the same product received during refresh; " +
                        "discarding previous: {} => {}, {}", product.getId(), existing, product);
                }

                this.importProducts.put(product.getId(), product);

                // Add any content attached to this product...
                this.addProductContent(product.getProductContent());
            }
        }
    }

    /**
     * Adds the specified content to this refresher. If a given content has already
     * been added, but differs from the existing version, a warning will be generated and the
     * previous entry will be replaced. Null content will be silently ignored.
     *
     * @param contents
     *  The content to add to this compiler
     *
     * @throws IllegalArgumentException
     *  if any provided content does not contain a valid content ID
     */
    public void addProductContent(ProductContentInfo... contents) {
        this.addProductContent(Arrays.asList(contents));
    }

    /**
     * Adds the specified content to this refresher. If a given content has already
     * been added, but differs from the existing version, a warning will be generated and the
     * previous entry will be replaced. Null content will be silently ignored.
     *
     * @param contents
     *  The content to add to this compiler
     *
     * @throws IllegalArgumentException
     *  if any provided content does not contain a valid content ID
     */
    public void addProductContent(Collection<? extends ProductContentInfo> contents) {
        if (contents != null) {
            for (ProductContentInfo container : contents) {
                if (container == null) {
                    continue;
                }

                ContentInfo content = container.getContent();

                // Do some simple mapping validation
                if (content == null) {
                    String msg = "collection contains an incomplete product-content mapping";

                    log.error(msg);
                    throw new IllegalArgumentException(msg);
                }

                if (content.getId() == null || content.getId().isEmpty()) {
                    String msg = String.format("content does not contain a mappable Red Hat ID: %s", content);

                    log.error(msg);
                    throw new IllegalArgumentException(msg);
                }

                ContentInfo existing = this.importContent.get(content.getId());
                if (existing != null && !existing.equals(content)) {
                    log.warn("Multiple versions of the same content received during refresh; " +
                        "discarding previous: {} => {}, {}", content.getId(), existing, content);
                }

                this.importContent.put(content.getId(), content);
            }
        }
    }

    /**
     * Adds the specified content to this refresher. If a given content has already
     * been added, but differs from the existing version, a warning will be generated and the
     * previous entry will be replaced. Null content will be silently ignored.
     *
     * @param contents
     *  The content to add to this compiler
     *
     * @throws IllegalArgumentException
     *  if any provided content does not contain a valid content ID
     */
    public void addContent(ContentInfo... contents) {
        this.addContent(Arrays.asList(contents));
    }

    /**
     * Adds the specified content to this refresher. If a given content has already
     * been added, but differs from the existing version, a warning will be generated and the
     * previous entry will be replaced. Null content will be silently ignored.
     *
     * @param contents
     *  The content to add to this compiler
     *
     * @throws IllegalArgumentException
     *  if any provided content does not contain a valid content ID
     */
    public void addContent(Collection<? extends ContentInfo> contents) {
        if (contents != null) {
            for (ContentInfo content : contents) {
                if (content == null) {
                    continue;
                }

                if (content.getId() == null || content.getId().isEmpty()) {
                    String msg = String.format("content does not contain a mappable Red Hat ID: %s", content);

                    log.error(msg);
                    throw new IllegalArgumentException(msg);
                }

                ContentInfo existing = this.importContent.get(content.getId());
                if (existing != null && !existing.equals(content)) {
                    log.warn("Multiple versions of the same content received during refresh; " +
                        "discarding previous: {} => {}, {}", content.getId(), existing, content);
                }

                this.importContent.put(content.getId(), content);
            }
        }
    }

    /**
     * Fetches the compiled set of subscriptions to import, mapped by subscription ID. If no subscriptions
     * have been added, this method returns an empty map.
     *
     * @return
     *  A compiled mapping of the subscriptions to import
     */
    public Map<String, ? extends SubscriptionInfo> getSubscriptions() {
        return this.importSubscriptions;
    }

    /**
     * Fetches the compiled set of products to import, mapped by product ID. If no products have been added,
     * this method returns an empty map.
     *
     * @return
     *  A compiled mapping of the products to import
     */
    public Map<String, ? extends ProductInfo> getProducts() {
        return this.importProducts;
    }

    /**
     * Fetches the compiled set of content to import, mapped by content ID. If no content has been added,
     * this method returns an empty map.
     *
     * @return
     *  A compiled mapping of the content to import
     */
    public Map<String, ? extends ContentInfo> getContent() {
        return this.importContent;
    }

    /**
     * Performs the import operation on the currently compiled objects
     */
    public RefreshResult execute(Owner owner) {

        // Step 01: Determine which entities are to be created, updated, or skipped
        // Step 02: From the entities being updated, find all existing entities *not* being imported
        //          that will be implicitly updated
        // Step 03: Initialize a collection of "finished" entities
        // Step 04: Invoke the recursive block with the combined list of entities to be created or
        //          updated as the current list.

        // Recursive block:
        // Step R1: For each entity in the current list, check if the entity has already been
        //          processed. If so, continue to the next entity in the list
        // Step R2: Create a list of all children entities of the current entity.
        // Step R3: If child list is not empty, invoke the recursive block with the child list as
        //          the current list
        // Step R4: Apply the received changes to the entity, using only the finished entities for
        //          resolution of child entity referencing
        // Step R5: Check if an existing version of the updated entity already exists locally. If so,
        //          store existing version in the finished entity collection; Otherwise, store updated
        //          entity in finished entity collection
        // Step R6: If current entity list has more entities, continue to next entity in the list;
        //          otherwise, break out of the current invocation of the recursive block

        // Step 05: Persist pending entity changes
        // Step 06: Update entity references not already corrected in the traversal above. This
        //          should include updating owners, pools, activation keys, and other such objects
        //          which reference products or content, but are not part of the tree.
        // Step 07: Return the collections of the finalized imported entities



        // Persist the entity changes

        // Update entity references

        // Return the collection of updated things




        // Give up on "true" tree processing; the entities are separate, and keeping track of all of them
        // in a singular operation is going to be a nightmare. Unless we create some type-aware collections
        // to store everything. Even then, there are difficulties to deal with.

        /*
            ActualRefresher (this class)
                - Should be instantiated/created via provider
                - collects inbound objects (*Info) to import/refresh
                - performs the core refresh process (execute)
                    - May call out to other classes to perform the various bits of work
                - returns a collection of final entity objects, sorted into
                    - This may not be needed long-term if even the pool updating gets rolled into
                      this class. However, for the short-term, this is still something we need to
                      refresh

            RefreshResult
                - Contains a set of collections of the various objects that were refreshed
                - Could also act as the cache of "finalized" entity changes; would act as a passable
                  state cache
                - Needs to perform lookup duties according to the object type
                - Needs to be able to generate typed collections of "imported" entities (created+updated)
                - Loooots of collections nested in here; but ideally those are encapsulated away so it's
                  a hidden detail

            ServiceModelInfo
                - Parent interface to all interfaces for the service models
                - Provides no inherent functionality other than the ability to collect disparate
                  objects in a single collection
                - Probably unnecessary at this point

            StateCache
                - A collection of collections. Probably unnecessary at the moment without some
                  better encapsulation to justify its existence. Could be used to store all of
                  the various components we need with methods for doing the lookup effectively
                  without needing to know the type ahead of time. Performance concerns lie here
                  if the polymorphic lookups prove to be slow or cumbersome compared to just
                  doing it directly.

            Things currently unaccounted for:
                - Source UUID tracking
                    - Need to carry around a map of objects that represent our source entities
                      to be able to look up the original UUID; may also act as a local cache to
                      avoid repeated lookups, but is not strictly necessary... probably
                - If we don't do method recursion, we'll need some structure to hold the various
                  collections we'll be iterating through

            Other difficulties:
                - Categorization involves stepping through every imported object once to identify
                  whether or not the object is going to be updated, created, or skipped entirely.
                  This is fine on its own, but means we'll be doing two iterations through
                  everything: once for categorization, and again to apply the changes. This seems
                  horrid but necessary, as it provides us an opportunity to fetch an optimized
                  list of entities that are affected by the changes we'll be applying.

        */

        RefreshResult result = new RefreshResult();

        NodeMapper mapper = new NodeMapper();
        VersionMapper vMap = new VersionMapper();

        // Step through each of our collections and build our forest of treeeeeeeees
        for (Product existingEntity : this.ownerProductCurator.getProductsByOwner(owner)) {
            this.buildNode(mapper, null, existingEntity);
        }

        for (ProductInfo importedEntity : this.importProducts.values()) {
            this.buildNode(mapper, null, importedEntity);
        }

        for (Content existingEntity : this.ownerContentCurator.getContentByOwner(owner)) {
            this.buildNode(mapper, null, existingEntity);
        }

        for (ContentInfo importedEntity : this.importContent.values()) {
            this.buildNode(mapper, null, importedEntity);
        }

        // At this point we should have a stable set of nodes representing our roots
        Iterator<EntityNode> rootIterator = mapper.getRootIterator();

        // From here we can walk each tree, check for updates, apply updates and determine which
        // trees can be skipped entirely

        // Step through the nodes and get the IDs for any created or updated entity, so we can do
        // versioning lookups
        if (mapper.hasNodesOfType(EntityNode.Type.PRODUCT)) {
            Set<String> pids = mapper.getNodesByType(EntityNode.Type.PRODUCT).stream()
                .filter(entry -> entry.getValue().changed())
                .map(entry -> entry.getKey())
                .collect(Collectors.toSet());

            // These casts are ridiculous. Java's implementation of generics are such a pain at times.
            Map versions = this.ownerProductCurator.getVersionedProductsById(owner, pids);

            vMap.addEntities(EntityNode.Type.PRODUCT,
                (Map<String, Set<? extends AbstractHibernateObject>>) versions);
        }

        if (mapper.hasNodesOfType(EntityNode.Type.CONTENT)) {
            Set<String> cids = mapper.getNodesByType(EntityNode.Type.CONTENT).stream()
                .filter(entry -> entry.getValue().changed())
                .map(entry -> entry.getKey())
                .collect(Collectors.toSet());

            Map versions = this.ownerContentCurator.getVersionedContentById(owner, cids);

            vMap.addEntities(EntityNode.Type.CONTENT,
                (Map<String, Set<? extends AbstractHibernateObject>>) versions);
        }

        // Process our nodes for real this timm
        while (rootIterator.hasNext()) {
            EntityNode node = rootIterator.next();
            this.processNode(mapper, vMap, node);
        }

        // At this point our nodes should be completely processed. One final walk through it to grab the
        // updated entities should be all we need (as well as DB hits to update ancillary refs)


        // This is slow; update the streamNodes method to not exist or return an iterator much like
        // RootIterator does
        for (EntityNode node : mapper.streamNodes().collect(Collectors.toList())) {
            if (node.changed()) {
                switch (node.getType()) {
                    case CONTENT:
                        // TODO: Update this to use the correct curator instead of getting the EM directly

                        this.ownerContentCurator.getEntityManager().merge((Content) node.getUpdatedEntity());

                        if (node.isNewEntity()) {
                            result.addCreatedContent((Content) node.getUpdatedEntity());
                        }
                        else {
                            result.addUpdatedContent((Content) node.getUpdatedEntity());
                        }

                        break;

                    case PRODUCT:
                        this.ownerProductCurator.getEntityManager().merge((Product) node.getUpdatedEntity());

                        if (node.isNewEntity()) {
                            result.addCreatedProduct((Product) node.getUpdatedEntity());
                        }
                        else {
                            result.addUpdatedProduct((Product) node.getUpdatedEntity());
                        }

                        break;

                    default:
                        throw new IllegalStateException("Unexpected node type: " + node.getType());
                }
            }
        }

        this.ownerProductCurator.flush();

        // TODO: Add owner-product and owner-content reference updating/creation
        // TODO: Update external references

        return result;
    }

    private EntityNode buildNode(NodeMapper mapper, EntityNode parent, ProductInfo existingEntity) {
        String entityId = existingEntity.getId();
        EntityNode<Product, ProductInfo> node = (EntityNode<Product, ProductInfo>) mapper
            .getNode(EntityNode.Type.PRODUCT, entityId);

        // Check if we already have a node for this entity
        if (node != null) {
            return node;
        }

        // Otherwise, we need to create a node for this entity and its children
        node = new EntityNode<Product, ProductInfo>(EntityNode.Type.PRODUCT)
            .addParentNode(parent);

        ProductInfo importedEntity = this.importProducts.get(entityId);
        if (importedEntity != null) {
            node.setImportedEntity(importedEntity);

            if (existingEntity != importedEntity) {
                node.setExistingEntity((Product) existingEntity);

                if (ProductManager.isChangedBy(existingEntity, importedEntity)) {
                    node.markChanged();
                }
            }
            else {
                // Net new products are always "changed"
                node.markChanged();
            }
        }
        else {
            node.setExistingEntity((Product) existingEntity);
        }

        // Add provided products
        Collection<? extends ProductInfo> providedProducts = existingEntity.getProvidedProducts();
        if (providedProducts != null) {
            for (ProductInfo provided : providedProducts) {
                EntityNode child = this.buildNode(mapper, node, provided);

                node.addChildNode(child);
                child.addParentNode(node);
            }
        }

        // Add content nodes
        Collection<? extends ProductContentInfo> productContent = existingEntity.getProductContent();

        // Product content processing is a bit... weird. We don't care about the join object for
        // our purposes here. It will be properly updated accordingly when we apply updates later.
        // However, we do want to track the content referencing for the tree construction.

        if (productContent != null) {
            for (ProductContentInfo pc : productContent) {
                ContentInfo content = pc.getContent();

                if (content == null) {
                    // This is so very bad. Fail out immediately.
                    throw new IllegalStateException("Product content references a null or invalid content");
                }

                EntityNode child = this.buildNode(mapper, node, content);

                node.addChildNode(child);
                child.addParentNode(node);
            }
        }

        mapper.putNode(EntityNode.Type.PRODUCT, entityId, node);
        return node;
    }

    private EntityNode buildNode(NodeMapper mapper, EntityNode parent, ContentInfo existingEntity) {
        String entityId = existingEntity.getId();
        EntityNode<Content, ContentInfo> node = (EntityNode<Content, ContentInfo>) mapper
            .getNode(EntityNode.Type.CONTENT, entityId);

        // Check if we already have a node for this entity
        if (node != null) {
            return node;
        }

        // Otherwise, we need to create a node for this entity and its children
        node = new EntityNode<Content, ContentInfo>(EntityNode.Type.CONTENT)
            .addParentNode(parent);

        ContentInfo importedEntity = this.importContent.get(entityId);
        if (importedEntity != null) {
            node.setImportedEntity(importedEntity);

            if (existingEntity != importedEntity) {
                node.setExistingEntity((Content) existingEntity);

                if (ContentManager.isChangedBy(existingEntity, importedEntity)) {
                    node.markChanged();
                }
            }
        }
        else {
            node.setExistingEntity((Content) existingEntity);
        }

        mapper.putNode(EntityNode.Type.CONTENT, entityId, node);
        return node;
    }

    private void processNode(NodeMapper mapper, VersionMapper vMap, EntityNode node) {
        // Ensure we don't re-process nodes we've already visited
        if (node.visited()) {
            return;
        }

        boolean childrenUpdated = false;

        // Process children nodes first (depth-first), so we can update references and avoid
        // rework; also check if we need to make reference updates on this entity.
        for (EntityNode child : (Set<EntityNode>) node.getChildren()) {
            this.processNode(mapper, vMap, child);
            childrenUpdated |= child.changed();
        }

        // Apply changes as necessary...

        // Scenarios here:

        /*
                Existing Entity     Imported Entity     Children Updated            Operation

        1       non-null            non-null            true                        entity update
        2       non-null            non-null            false                       entity update
        3       non-null            null                true                        reference update (affected entity)
        4       non-null            null                false                       skip
        5       null                non-null            true                        entity creation
        6       null                non-null            false                       entity creation
        7       null                null                true                        - ERROR -
        8       null                null                false                       - ERROR -

        */

        // Check if we have changes
        if (node.changed()) {
            if (!node.hasExistingEntity()) {
                // Creation (5, 6)

                switch (node.getType()) {
                    case PRODUCT:
                        node.setUpdatedEntity(this.createEntity(mapper, vMap,
                            (Product) null,
                            (Product) node.getImportedEntity()));

                        break;

                    case CONTENT:
                        node.setUpdatedEntity(this.createEntity(mapper, vMap,
                            (Content) null,
                            (Content) node.getImportedEntity()));

                        break;

                    default:
                        throw new IllegalStateException("Unexpected node type: " + node.getType());
                }
            }
            else {
                // Update (1, 2)

                switch (node.getType()) {
                    case PRODUCT:
                        node.setUpdatedEntity(this.createEntity(mapper, vMap,
                            (Product) node.getExistingEntity(), (ProductInfo) node.getImportedEntity()));

                        break;

                    case CONTENT:
                        node.setUpdatedEntity(this.createEntity(mapper, vMap,
                            (Content) node.getExistingEntity(), (ContentInfo) node.getImportedEntity()));

                        break;

                    default:
                        throw new IllegalStateException("Unexpected node type: " + node.getType());
                }
            }
        }
        else if (childrenUpdated) {
            node.markChanged(); // probably unnecessary, but might as well be consistent

            // Resolve children references
            switch (node.getType()) {
                case PRODUCT:
                    node.setUpdatedEntity(this.createEntity(mapper, vMap,
                        (Product) null,
                        (Product) node.getImportedEntity()));

                    break;

                case CONTENT:
                    node.setUpdatedEntity(this.createEntity(mapper, vMap,
                        (Content) null,
                        (Content) node.getImportedEntity()));

                    break;

                default:
                    throw new IllegalStateException("Unexpected node type: " + node.getType());
            }
        }

        // Flag the node as visited so we don't risk rework or making changes multiple
        // times, screwing up our entity refs
        node.markVisited();
    }


    private Product createEntity(NodeMapper mapper, VersionMapper vMap,
        Product existingEntity, ProductInfo importedEntity) {

        Product updatedEntity = existingEntity != null ?
            (Product) existingEntity.clone() :
            new Product();

        // Clear the UUID so we don't attempt to inherit it
        updatedEntity.setUuid(null);

        if (importedEntity != null) {
            if (importedEntity.getName() != null) {
                updatedEntity.setName(importedEntity.getName());
            }

            if (importedEntity.getMultiplier() != null) {
                updatedEntity.setMultiplier(importedEntity.getMultiplier());
            }

            if (importedEntity.getAttributes() != null) {
                updatedEntity.setAttributes(importedEntity.getAttributes());
            }

            if (importedEntity.getDependentProductIds() != null) {
                updatedEntity.setDependentProductIds(importedEntity.getDependentProductIds());
            }

            if (importedEntity.getBranding() != null) {
                if (importedEntity.getBranding().isEmpty()) {
                    updatedEntity.setBranding(Collections.emptySet());
                }
                else {
                    Set<Branding> branding = new HashSet<>();
                    for (BrandingInfo binfo : importedEntity.getBranding()) {
                        if (binfo != null) {
                            branding.add(new Branding(
                                updatedEntity,
                                binfo.getProductId(),
                                binfo.getName(),
                                binfo.getType()
                            ));
                        }
                    }

                    updatedEntity.setBranding(branding);
                }
            }
        }

        // Perform child resolution
        ProductInfo sourceEntity = (importedEntity != null ? importedEntity : existingEntity);

        // Update products
        Collection<? extends ProductInfo> providedProducts = sourceEntity.getProvidedProducts();

        if (providedProducts != null) {
            updatedEntity.setProvidedProducts(null);

            for (ProductInfo product : providedProducts) {
                EntityNode childNode = mapper.getNode(EntityNode.Type.PRODUCT, product.getId());

                if (childNode == null) {
                    String errmsg = String.format("Product references a product which does not exist: %s",
                        product.getId());

                    throw new IllegalStateException(errmsg);
                }

                if (!childNode.visited()) {
                    String errmsg = String.format("Child node accessed before it has been processed: " +
                        "%s => %s", sourceEntity, product);

                    throw new IllegalStateException(errmsg);
                }

                updatedEntity.addProvidedProduct((Product) (childNode.changed() ?
                    childNode.getUpdatedEntity() :
                    childNode.getExistingEntity()));
            }
        }

        // Update content
        Collection<? extends ProductContentInfo> productContent = sourceEntity.getProductContent();

        if (productContent != null) {
            updatedEntity.setProductContent(null);

            for (ProductContentInfo pc : productContent) {
                ContentInfo content = pc.getContent();
                EntityNode childNode = mapper.getNode(EntityNode.Type.CONTENT, content.getId());

                if (childNode == null) {
                    String errmsg = String.format("Product references a content which does not exist: %s",
                        content.getId());

                    throw new IllegalStateException(errmsg);
                }

                if (!childNode.visited()) {
                    String errmsg = String.format("Child node accessed before it has been processed: " +
                        "%s => %s", sourceEntity, content);

                    throw new IllegalStateException(errmsg);
                }

                updatedEntity.addContent((Content) (childNode.changed() ?
                    childNode.getUpdatedEntity() :
                    childNode.getExistingEntity()),
                    pc.isEnabled());
            }
        }


        // Do version resolution
        int version = updatedEntity.getEntityVersion();

        Set<Product> candidates = vMap.<Product>getCandidateEntities(EntityNode.Type.PRODUCT,
            updatedEntity.getId());

        for (Product candidate : candidates) {
            if (candidate.getEntityVersion(true) == version && updatedEntity.equals(candidate)) {
                // We've pre-existing version. Use it rather than our updatedEntity;
                return candidate;
            }
        }

        // No matching versions. Return our updated entity.
        return updatedEntity;
    }

    private Content createEntity(NodeMapper mapper, VersionMapper vMap,
        Content existingEntity, ContentInfo importedEntity) {

        Content updatedEntity = existingEntity != null ?
            (Content) existingEntity.clone() :
            new Content();

        // Clear the UUID so we don't accidentally inherit it
        updatedEntity.setUuid(null);

        if (importedEntity != null) {
            if (importedEntity.getType() != null) {
                updatedEntity.setType(importedEntity.getType());
            }

            if (importedEntity.getLabel() != null) {
                updatedEntity.setLabel(importedEntity.getLabel());
            }

            if (importedEntity.getName() != null) {
                updatedEntity.setName(importedEntity.getName());
            }

            if (importedEntity.getVendor() != null) {
                updatedEntity.setVendor(importedEntity.getVendor());
            }

            if (importedEntity.getContentUrl() != null) {
                updatedEntity.setContentUrl(importedEntity.getContentUrl());
            }

            if (importedEntity.getRequiredTags() != null) {
                updatedEntity.setRequiredTags(importedEntity.getRequiredTags());
            }

            if (importedEntity.getReleaseVersion() != null) {
                updatedEntity.setReleaseVersion(importedEntity.getReleaseVersion());
            }

            if (importedEntity.getGpgUrl() != null) {
                updatedEntity.setGpgUrl(importedEntity.getGpgUrl());
            }

            if (importedEntity.getMetadataExpiration() != null) {
                updatedEntity.setMetadataExpiration(importedEntity.getMetadataExpiration());
            }

            if (importedEntity.getRequiredProductIds() != null) {
                updatedEntity.setModifiedProductIds(importedEntity.getRequiredProductIds());
            }

            if (importedEntity.getArches() != null) {
                updatedEntity.setArches(importedEntity.getArches());
            }
        }

        // Do version resolution
        int version = updatedEntity.getEntityVersion();

        Set<Content> candidates = vMap.<Content>getCandidateEntities(EntityNode.Type.CONTENT,
            updatedEntity.getId());

        for (Content candidate : candidates) {
            if (candidate.getEntityVersion(true) == version && updatedEntity.equals(candidate)) {
                // We've pre-existing version. Use it rather than our updatedEntity;
                return candidate;
            }
        }

        // No matching versions. Return our updated entity.
        return updatedEntity;
    }











    private static class VersionMapper {

        private Map<EntityNode.Type, Map<String, Set<? extends AbstractHibernateObject>>> typeMap;

        public VersionMapper() {
            this.typeMap = new HashMap<>();
        }

        public void addEntities(EntityNode.Type type, Map<String,
            Set<? extends AbstractHibernateObject>> map) {

            if (type == null) {
                throw new IllegalArgumentException("type is null");
            }

            if (map == null) {
                throw new IllegalArgumentException("map is null");
            }

            // We're being lazy here; just set the map in our map for later retrieval.
            this.typeMap.put(type, map);
        }

        public <E extends AbstractHibernateObject> Set<E> getCandidateEntities(EntityNode.Type type,
            String id) {

            Map<String, Set<? extends AbstractHibernateObject>> idMap = this.typeMap.get(type);
            return (Set<E>) (idMap != null ? idMap.get(id) : null);
        }

    }












    private static class NodeMapper {

        private static class RootIterator implements Iterator<EntityNode> {

            private final Iterator<Map<String, EntityNode>> typeIterator;

            private Iterator<EntityNode> nodeIterator;
            private EntityNode next;

            public RootIterator(Iterator<Map<String, EntityNode>> typeIterator) {
                if (typeIterator == null) {
                    throw new IllegalArgumentException("typeIterator is null");
                }

                this.typeIterator = typeIterator;
                this.scan();
            }

            private void scan() {
                while (true) {
                    if (this.nodeIterator != null) {
                        while (this.nodeIterator.hasNext()) {
                            EntityNode candidate = this.nodeIterator.next();

                            if (candidate.getParents().isEmpty()) {
                                this.next = candidate;
                                return;
                            }
                        }
                    }

                    if (!this.typeIterator.hasNext()) {
                        break;
                    }

                    this.nodeIterator = this.typeIterator.next()
                        .values()
                        .iterator();
                }

                this.next = null;
            }

            @Override
            public boolean hasNext() {
                return this.next != null;
            }

            @Override
            public EntityNode next() {
                if (this.next == null) {
                    throw new NoSuchElementException();
                }

                EntityNode output = this.next;
                this.scan();

                return output;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException("remove is not supported on RootIterator instances");
            }
        }




        protected Map<EntityNode.Type, Map<String, EntityNode>> typeMap;


        public NodeMapper() {
            this.typeMap = new HashMap<>();
        }

        public boolean hasNode(EntityNode.Type type, String id) {
            Map<String, EntityNode> nodeMap = this.typeMap.get(type);
            return nodeMap != null && nodeMap.containsKey(id);
        }

        public boolean hasNodesOfType(EntityNode.Type type) {
            Map<String, EntityNode> nodeMap = this.typeMap.get(type);
            return nodeMap != null;
        }

        public EntityNode getNode(EntityNode.Type type, String id) {
            Map<String, EntityNode> nodeMap = this.typeMap.get(type);
            return nodeMap != null ? nodeMap.get(id) : null;
        }

        public Set<Map.Entry<String, EntityNode>> getNodesByType(EntityNode.Type type) {
            Map<String, EntityNode> nodeMap = this.typeMap.get(type);
            return nodeMap != null ? nodeMap.entrySet() : null;
        }

        public Stream<EntityNode> streamNodes() {
            // This is pretty bad, come up with something nicer before final implementation
            Stream<EntityNode> stream = Stream.empty();

            for (Map<String, EntityNode> idMap : this.typeMap.values()) {
                stream = Stream.concat(stream, idMap.values().stream());
            }

            return stream;
        }

        public EntityNode putNode(EntityNode.Type type, String id, EntityNode node) {
            if (type == null) {
                throw new IllegalArgumentException("type is null");
            }

            if (id == null || id.isEmpty()) {
                throw new IllegalArgumentException("node id is null or empty");
            }

            if (node == null) {
                throw new IllegalArgumentException("node is null");
            }

            Map<String, EntityNode> nodeMap = this.typeMap.get(type);

            if (nodeMap == null) {
                nodeMap = new HashMap<>();
                this.typeMap.put(type, nodeMap);
            }

            return nodeMap.put(id, node);
        }

        public Iterator<EntityNode> getRootIterator() {
            return new RootIterator(this.typeMap.values().iterator());
        }
    }


    private static class EntityNode<E extends AbstractHibernateObject, I extends ServiceModelInfo> {

        public static enum Type {
            PRODUCT,
            CONTENT
        };

        private final Type type;

        private E existingEntity;
        private I importedEntity;
        private E updatedEntity;

        private boolean changed; // Eventually change this to a full set of changes detected
        private boolean visited;

        private Set<EntityNode> parents;
        private Set<EntityNode> children;


        public EntityNode(Type type) {
            if (type == null) {
                throw new IllegalArgumentException("type is null");
            }

            this.type = type;

            this.parents = new HashSet<>();
            this.children = new HashSet<>();
        }

        public Type getType() {
            return this.type;
        }

        public EntityNode addParentNode(EntityNode parent) {
            if (parent == null) {
                throw new IllegalArgumentException("parent is null");
            }

            this.parents.add(parent);
            return this;
        }

        public EntityNode addChildNode(EntityNode child) {
            if (child == null) {
                throw new IllegalArgumentException("child is null");
            }

            this.children.add(child);
            return this;
        }

        public Set<EntityNode> getParents() {
            return this.parents;
        }

        public Set<EntityNode> getChildren() {
            return this.children;
        }

        public EntityNode setExistingEntity(E entity) {
            this.existingEntity = entity;
            return this;
        }

        public EntityNode setImportedEntity(I entity) {
            this.importedEntity = entity;
            return this;
        }

        public EntityNode setUpdatedEntity(E entity) {
            this.updatedEntity = entity;
            return this;
        }

        public boolean hasExistingEntity() {
            return this.existingEntity != null;
        }

        public boolean hasImportedEntity() {
            return this.importedEntity != null;
        }

        public boolean hasUpdatedEntity() {
            return this.updatedEntity != null;
        }

        public E getExistingEntity() {
            return this.existingEntity;
        }

        public I getImportedEntity() {
            return this.importedEntity;
        }

        public E getUpdatedEntity() {
            return this.updatedEntity;
        }

        public EntityNode markChanged() {
            this.changed = true;
            return this;
        }

        public EntityNode markVisited() {
            this.visited = true;
            return this;
        }

        public boolean changed() {
            return this.changed;
        }

        public boolean visited() {
            return this.visited;
        }

        public boolean isNewEntity() {
            return this.existingEntity == null && this.importedEntity != null;
        }

        public boolean isUpdatedEntity() {
            return this.existingEntity != null && this.importedEntity != null &&
                this.existingEntity != this.importedEntity;
        }
    }


}
