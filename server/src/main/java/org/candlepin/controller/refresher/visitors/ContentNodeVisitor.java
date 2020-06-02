/**
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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
import org.candlepin.controller.refresher.mappers.NodeMapper;
import org.candlepin.controller.refresher.nodes.EntityNode;
import org.candlepin.controller.refresher.nodes.EntityNode.NodeState;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerContent;
import org.candlepin.model.OwnerContentCurator;
import org.candlepin.service.model.ContentInfo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;



/**
 * A NodeVisitor implementation that supports content entity nodes
 */
public class ContentNodeVisitor implements NodeVisitor<Content, ContentInfo> {
    private final ContentCurator contentCurator;
    private final OwnerContentCurator ownerContentCurator;

    private Set<OwnerContent> ownerContentEntities;
    private Map<Owner, Map<String, String>> ownerContentUuidMap;
    private Map<Owner, Set<String>> deletedContentUuids;


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
    public void processNode(NodeProcessor processor, NodeMapper mapper,
        EntityNode<Content, ContentInfo> node) {

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
                    Content mergedEntity = this.createEntity(mapper, node);
                    node.setMergedEntity(mergedEntity);

                    // Store the mapping to be updated later
                    this.ownerContentUuidMap.computeIfAbsent(node.getOwner(), key -> new HashMap<>())
                        .put(existingEntity.getUuid(), mergedEntity.getUuid());

                    node.setNodeState(NodeState.UPDATED);
                }
            }
        }
        else if (importedEntity != null) {
            // Node is new
            Content mergedEntity = this.createEntity(mapper, node);
            node.setMergedEntity(mergedEntity);

            // Create a new owner-content mapping for this entity. This will get persisted
            // later during completion
            this.ownerContentEntities.add(new OwnerContent(node.getOwner(), mergedEntity));

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
     * Checks if a node is an unused root, or is part of a subtree (or subtrees) that are marked for
     * deletion.
     *
     * @param node
     *  the entity node to check
     *
     * @return
     *  true if the node is cleared for deletion; false otherwise
     */
    private boolean clearedForDeletion(EntityNode<Content, ContentInfo> node) {
        if (node.getExistingEntity().isLocked()) {
            if (node.getImportedEntity() == null && node.isRootNode()) {
                return true;
            }

            for (EntityNode parent : node.getParentNodes()) {
                if (parent.getNodeState() != NodeState.DELETED) {
                    return false;
                }
            }

            return true;
        }

        return false;
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
        this.ownerContentCurator.saveAll(this.ownerContentEntities, false, false);
        this.ownerContentCurator.flush();

        // Update owner content references
        for (Map.Entry<Owner, Map<String, String>> entry : this.ownerContentUuidMap.entrySet()) {
            this.ownerContentCurator.updateOwnerContentReferences(entry.getKey(), entry.getValue());
        }

        // Clear our cache
        this.ownerContentUuidMap.clear();
        this.ownerContentEntities.clear();
        this.deletedContentUuids.clear();
    }

    /**
     * Creates a new entity for the given node, potentially remapping to an existing node
     */
    private Content createEntity(NodeMapper mapper, EntityNode<Content, ContentInfo> node) {
        Content existingEntity = node.getExistingEntity();
        ContentInfo importedEntity = node.getImportedEntity();
        ContentInfo sourceEntity = importedEntity != null ? importedEntity : existingEntity;

        if (sourceEntity == null) {
            throw new IllegalArgumentException("No source entity provided"); // Sanity check
        }

        Content updatedEntity = existingEntity != null ?
            (Content) existingEntity.clone() :
            new Content().setLocked(true);

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

        // Do version resolution
        int version = updatedEntity.getEntityVersion();
        Set<Content> candidates = node.getCandidateEntities();

        if (candidates != null) {
            for (Content candidate : candidates) {
                if (candidate.getEntityVersion(true) == version && updatedEntity.equals(candidate)) {
                    // We've a pre-existing version; use it rather than our updatedEntity
                    return candidate;
                }
            }
        }

        // No matching versions. Persist and return our updated entity.
        return this.contentCurator.saveOrUpdate(updatedEntity);
    }
}
