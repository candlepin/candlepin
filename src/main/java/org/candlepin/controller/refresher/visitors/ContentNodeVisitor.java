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
import org.candlepin.service.model.ContentInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;



/**
 * A NodeVisitor implementation that supports content entity nodes
 */
public class ContentNodeVisitor implements NodeVisitor<Content, ContentInfo> {
    private static final Logger log = LoggerFactory.getLogger(ContentNodeVisitor.class);

    private final ContentCurator contentCurator;

    /**
     * Creates a new ContentNodeVisitor that uses the provided curators for performing database
     * operations.
     *
     * @param contentCurator
     *  the ContentCurator to use for content database operations
     *
     * @throws IllegalArgumentException
     *  if any of the provided curators are null
     */
    public ContentNodeVisitor(ContentCurator contentCurator) {
        this.contentCurator = Objects.requireNonNull(contentCurator);
    }

    /**
     *
     */
    private Content applyContentChanges(Content existingEntity, ContentInfo importedEntity) {
        if (importedEntity != null) {
            if (importedEntity.getType() != null) {
                existingEntity.setType(importedEntity.getType());
            }

            if (importedEntity.getLabel() != null) {
                existingEntity.setLabel(importedEntity.getLabel());
            }

            if (importedEntity.getName() != null) {
                existingEntity.setName(importedEntity.getName());
            }

            if (importedEntity.getVendor() != null) {
                existingEntity.setVendor(importedEntity.getVendor());
            }

            if (importedEntity.getContentUrl() != null) {
                existingEntity.setContentUrl(importedEntity.getContentUrl());
            }

            if (importedEntity.getRequiredTags() != null) {
                existingEntity.setRequiredTags(importedEntity.getRequiredTags());
            }

            if (importedEntity.getReleaseVersion() != null) {
                existingEntity.setReleaseVersion(importedEntity.getReleaseVersion());
            }

            if (importedEntity.getGpgUrl() != null) {
                existingEntity.setGpgUrl(importedEntity.getGpgUrl());
            }

            if (importedEntity.getMetadataExpiration() != null) {
                existingEntity.setMetadataExpiration(importedEntity.getMetadataExpiration());
            }

            if (importedEntity.getRequiredProductIds() != null) {
                existingEntity.setModifiedProductIds(importedEntity.getRequiredProductIds());
            }

            if (importedEntity.getArches() != null) {
                existingEntity.setArches(importedEntity.getArches());
            }
        }

        return existingEntity;
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
            if (importedEntity != null && ContentManager.isChangedBy(existingEntity, importedEntity)) {
                node.setNodeState(NodeState.UPDATED);
            }
        }
        else if (importedEntity != null) {
            node.setNodeState(NodeState.CREATED);
        }
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

        Content existingEntity = node.getExistingEntity();
        ContentInfo importedEntity = node.getImportedEntity();
        Content updatedEntity;

        switch (node.getNodeState()) {
            case CREATED:
                updatedEntity = this.applyContentChanges(new Content(importedEntity.getId()), importedEntity);
                updatedEntity = this.contentCurator.create(updatedEntity, false);

                node.setExistingEntity(updatedEntity);
                break;

            case UPDATED:
                updatedEntity = this.applyContentChanges(existingEntity, importedEntity);
                updatedEntity = this.contentCurator.merge(updatedEntity);

                node.setExistingEntity(updatedEntity);
                break;

            case DELETED:
                this.contentCurator.delete(existingEntity);
                break;

            default:
                // intentionally left empty
        }
    }

}
