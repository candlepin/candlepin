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
package org.candlepin.controller.refresher.visitors;

import org.candlepin.controller.ContentManager;
import org.candlepin.controller.refresher.nodes.EntityNode;
import org.candlepin.controller.refresher.nodes.EntityNode.NodeState;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerContent;
import org.candlepin.model.OwnerContentCurator;
import org.candlepin.service.model.ContentInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;



/**
 * A NodeVisitor implementation that supports content entity nodes
 */
public class ContentNodeVisitor implements NodeVisitor<Content, ContentInfo> {
    private static final Logger log = LoggerFactory.getLogger(ContentNodeVisitor.class);

    private final ContentCurator contentCurator;
    private final OwnerContentCurator ownerContentCurator;

    private Set<OwnerContent> ownerContentEntities;
    private Map<Owner, Map<String, String>> ownerContentUuidMap;
    private Map<Owner, Set<String>> deletedContentUuids;
    private Map<Owner, Set<Long>> ownerEntityVersions;
    private Map<Owner, Map<String, List<Content>>> ownerVersionedEntityMap;

    /**
     * Creates a new ContentNodeVisitor that uses the provided curators for performing database
     * operations.
     *
     * @param contentCurator
     *  the ContentCurator to use for content database operations
     *
     * @param ownerContentCurator
     *  the OwnerContentCurator to use for owner-content database operations
     *
     * @throws IllegalArgumentException
     *  if any of the provided curators are null
     */
    public ContentNodeVisitor(ContentCurator contentCurator, OwnerContentCurator ownerContentCurator) {
        if (contentCurator == null) {
            throw new IllegalArgumentException("contentCurator is null");
        }

        if (ownerContentCurator == null) {
            throw new IllegalArgumentException("ownerContentCurator is null");
        }

        this.contentCurator = contentCurator;
        this.ownerContentCurator = ownerContentCurator;

        this.ownerContentEntities = new HashSet<>();
        this.ownerContentUuidMap = new HashMap<>();
        this.deletedContentUuids = new HashMap<>();
        this.ownerEntityVersions = new HashMap<>();
        this.ownerVersionedEntityMap = new HashMap<>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<Content> getEntityClass() {
        return Content.class;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void processNode(EntityNode<Content, ContentInfo> node) {
        // Content nodes should not have any children
        if (!node.isLeafNode()) {
            throw new IllegalStateException("Content node has one or more children nodes");
        }

        // If this node already has a state, we don't need to reprocess it (probably)
        if (node.getNodeState() != null) {
            return;
        }

        Content existingEntity = node.getExistingEntity();
        ContentInfo importedEntity = node.getImportedEntity();

        // Default the node state to UNCHANGED and let our cases below overwrite this
        node.setNodeState(NodeState.UNCHANGED);

        if (existingEntity != null) {
            if (importedEntity != null) {
                if (ContentManager.isChangedBy(existingEntity, importedEntity)) {
                    Content mergedEntity = this.createEntity(node);
                    node.setMergedEntity(mergedEntity);

                    node.setNodeState(NodeState.UPDATED);
                }
            }
        }
        else if (importedEntity != null) {
            // Node is new
            Content mergedEntity = this.createEntity(node);
            node.setMergedEntity(mergedEntity);

            node.setNodeState(NodeState.CREATED);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void pruneNode(EntityNode<Content, ContentInfo> node) {
        Content existingEntity = node.getExistingEntity();

        // We're only going to prune existing entities that are locked
        if (existingEntity != null && this.clearedForDeletion(node)) {
            this.deletedContentUuids.computeIfAbsent(node.getOwner(), key -> new HashSet<>())
                .add(existingEntity.getUuid());

            node.setNodeState(NodeState.DELETED);
        }
    }

    /**
     * Checks that the entity is no longer present upstream, and is not part of any active subtrees.
     *
     * @param node
     *  the entity node to check
     *
     * @return
     *  true if the node is cleared for deletion; false otherwise
     */
    private boolean clearedForDeletion(EntityNode<Content, ContentInfo> node) {
        // We don't delete custom entities, ever.
        if (!node.getExistingEntity().isLocked()) {
            return false;
        }

        // If the node is still defined upstream and is part of this refresh, we should keep it
        // around locally
        if (node.getImportedEntity() != null) {
            return false;
        }

        // Otherwise, if the node is referenced by one or more parent nodes that are not being
        // deleted themselves, we should keep it.
        return !node.getParentNodes()
            .anyMatch(elem -> elem.getNodeState() != NodeState.DELETED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void applyChanges(EntityNode<Content, ContentInfo> node) {
        // If we don't have a state, bad things have happened...
        if (node.getNodeState() == null) {
            throw new IllegalStateException("Attempting to apply changes to a node without a state: " + node);
        }

        // We only need to do work in the UPDATED or CREATED cases; all other states are no-ops here
        if (node.getNodeState() == NodeState.UPDATED) {
            node.setMergedEntity(this.resolveEntityVersion(node));

            // Store the mapping to be updated later
            Content existingEntity = node.getExistingEntity();
            Content mergedEntity = node.getMergedEntity();

            this.ownerContentUuidMap.computeIfAbsent(node.getOwner(), key -> new HashMap<>())
                .put(existingEntity.getUuid(), mergedEntity.getUuid());
        }
        else if (node.getNodeState() == NodeState.CREATED) {
            node.setMergedEntity(this.resolveEntityVersion(node));

            // Create a new owner-content mapping for this entity. This will get persisted later
            // during the completion step
            this.ownerContentEntities.add(new OwnerContent(node.getOwner(), node.getMergedEntity()));
        }
    }

    /**
     * Performs version resolution for the specified entity node. If a local version of the entity
     * already exists, this method returns the existing entity; otherwise, the new entity stored in
     * the provided node is persisted and returned.
     *
     * @param node
     *  the entity node on which to perform version resolution
     *
     * @return
     *  the existing version of the entity if such a version exists, or the persisted version of
     *  the newly created entity
     */
    private Content resolveEntityVersion(EntityNode<Content, ContentInfo> node) {
        Owner owner = node.getOwner();
        Content entity = node.getMergedEntity();
        long entityVersion = entity.getEntityVersion();

        Map<String, List<Content>> entityMap = this.ownerVersionedEntityMap.computeIfAbsent(owner, key -> {
            Set<Long> versions = this.ownerEntityVersions.remove(key);

            return versions != null ?
                this.ownerContentCurator.getContentByVersions(versions) :
                Collections.emptyMap();
        });

        for (Content candidate : entityMap.getOrDefault(entity.getId(), Collections.emptyList())) {
            if (entityVersion == candidate.getEntityVersion()) {
                if (entity.equals(candidate)) {
                    // We found a match! Map to the candidate entity
                    return candidate;
                }

                // If we have a version collision, and the entity IDs are the same, there's likely
                // some shenanigans going on. Rather than halting all behavior, let's just clear
                // the old entity's version so we start mapping to the new one.
                // If we have a collision where the products are actually different, we won't
                // fail at this point (but we *will* fail), and we won't be able to detect it.

                // Impl note:
                // We've already implicitly checked the ID above by how we're pulling candidate
                // entities from the map
                log.error("Entity version collision detected; attempting resolution..." +
                    "\nConflicting entities:\n{}\n{}", entity, candidate);

                this.ownerContentCurator.clearContentEntityVersion(candidate);
                break;
            }
        }

        return this.contentCurator.create(entity, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void complete() {
        // Remove owner-specific content references for deleted content
        for (Map.Entry<Owner, Set<String>> entry : this.deletedContentUuids.entrySet()) {
            this.ownerContentCurator.removeOwnerContentReferences(entry.getKey(), entry.getValue());
        }

        // Save new owner-content entities
        this.ownerContentEntities.stream()
            .forEach(elem -> this.ownerContentCurator.create(elem, false));
        this.ownerContentCurator.flush();

        // Update owner content references
        for (Map.Entry<Owner, Map<String, String>> entry : this.ownerContentUuidMap.entrySet()) {
            this.ownerContentCurator.updateOwnerContentReferences(entry.getKey(), entry.getValue());
        }

        // Clear our various caches
        this.ownerContentUuidMap.clear();
        this.ownerContentEntities.clear();
        this.deletedContentUuids.clear();
        this.ownerEntityVersions.clear();
        this.ownerVersionedEntityMap.clear();
    }

    /**
     * Creates a new entity for the given node, potentially remapping to an existing node
     */
    private Content createEntity(EntityNode<Content, ContentInfo> node) {
        Content existingEntity = node.getExistingEntity();
        ContentInfo importedEntity = node.getImportedEntity();
        ContentInfo sourceEntity = importedEntity != null ? importedEntity : existingEntity;

        if (sourceEntity == null) {
            throw new IllegalArgumentException("No source entity provided"); // Sanity check
        }

        Content updatedEntity = existingEntity != null ?
            (Content) existingEntity.clone() :
            (new Content().setLocked(true));

        // Ensure the RH content ID is set properly
        updatedEntity.setId(sourceEntity.getId());

        // Clear the UUID so we don't attempt to inherit it
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

        // Save entity version for later version resolution
        this.ownerEntityVersions.computeIfAbsent(node.getOwner(), key -> new HashSet<>())
            .add(updatedEntity.getEntityVersion());

        return updatedEntity;
    }
}
