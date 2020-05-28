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
package org.candlepin.resource;

import org.candlepin.auth.Verify;
import org.candlepin.common.exceptions.ForbiddenException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.controller.ContentAccessManager;
import org.candlepin.controller.ContentManager;
import org.candlepin.controller.PoolManager;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.v1.ContentDTO;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.EnvironmentContentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerContentCurator;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.ProductCurator;
import org.candlepin.resource.util.InfoAdapter;
import org.candlepin.service.UniqueIdGenerator;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * OwnerContentResource
 *
 * Manage the content that exists in an organization.
 */

public class OwnerContentResource implements OwnersApi {
    private static Logger log = LoggerFactory.getLogger(OwnerContentResource.class);

    private ContentCurator contentCurator;
    private ContentManager contentManager;
    private EnvironmentContentCurator envContentCurator;
    private I18n i18n;
    private OwnerCurator ownerCurator;
    private OwnerContentCurator ownerContentCurator;
    private PoolManager poolManager;
    private ProductCurator productCurator;
    private UniqueIdGenerator idGenerator;
    private ContentAccessManager contentAccessManager;
    private ModelTranslator translator;

    @Inject
    public OwnerContentResource(ContentCurator contentCurator, ContentManager contentManager,
        EnvironmentContentCurator envContentCurator, I18n i18n, OwnerCurator ownerCurator,
        OwnerContentCurator ownerContentCurator, PoolManager poolManager, ProductCurator productCurator,
        UniqueIdGenerator idGenerator, ContentAccessManager contentAccessManager,
        ModelTranslator translator) {

        this.contentCurator = contentCurator;
        this.contentManager = contentManager;
        this.envContentCurator = envContentCurator;
        this.i18n = i18n;
        this.ownerCurator = ownerCurator;
        this.ownerContentCurator = ownerContentCurator;
        this.poolManager = poolManager;
        this.productCurator = productCurator;
        this.idGenerator = idGenerator;
        this.contentAccessManager = contentAccessManager;
        this.translator = translator;
    }

    /**
     * Retrieves an Owner instance for the owner with the specified key/account. If a matching owner
     * could not be found, this method throws an exception.
     *
     * @param key
     *  The key for the owner to retrieve
     *
     * @throws NotFoundException
     *  if an owner could not be found for the specified key.
     *
     * @return
     *  the Owner instance for the owner with the specified key.
     */
    protected Owner getOwnerByKey(String key) {
        Owner owner = this.ownerCurator.getByKey(key);

        if (owner == null) {
            throw new NotFoundException(i18n.tr("Owner with key \"{0}\" was not found.", key));
        }

        return owner;
    }

    /**
     * Retrieves the content entity with the given content ID for the specified owner. If a
     * matching entity could not be found, this method throws a NotFoundException.
     *
     * @param owner
     *  The owner in which to search for the content
     *
     * @param contentId
     *  The Red Hat ID of the content to retrieve
     *
     * @throws NotFoundException
     *  If a content with the specified Red Hat ID could not be found
     *
     * @return
     *  the content entity with the given owner and content ID
     */
    protected Content fetchContent(Owner owner, String contentId) {
        Content content = this.ownerContentCurator.getContentById(owner, contentId);

        if (content == null) {
            throw new NotFoundException(
                i18n.tr("Content with ID \"{0}\" could not be found.", contentId)
            );
        }

        return content;
    }

    @Override
    public CandlepinQuery<ContentDTO> listOwnerContent(
        @Verify(Owner.class) String ownerKey) {

        final Owner owner = this.getOwnerByKey(ownerKey);

        CandlepinQuery<Content> query = this.ownerContentCurator.getContentByOwner(owner);
        return this.translator.translateQuery(query, ContentDTO.class);
    }

    @Override
    public ContentDTO getOwnerContent(
        @Verify(Owner.class) String ownerKey, String contentId) {

        Owner owner = this.getOwnerByKey(ownerKey);
        Content content = this.fetchContent(owner, contentId);

        return this.translator.translate(content, ContentDTO.class);
    }

    /**
     * Creates or merges the given Content object.
     *
     * @param owner
     *  The owner for which to create the new content
     *
     * @param content
     *  The content to create or merge
     *
     * @return
     *  the newly created and/or merged Content object.
     */

    private Content createContentImpl(Owner owner, ContentDTO content) {
        // TODO: check if arches have changed ??

        Content entity = null;

        if (content.getId() == null || content.getId().trim().length() == 0) {
            content.setId(this.idGenerator.generateId());

            entity = this.contentManager.createContent(owner, InfoAdapter.contentInfoAdapter(content));
        }
        else {
            Content existing = this.ownerContentCurator.getContentById(owner, content.getId());

            if (existing != null) {
                if (existing.isLocked()) {
                    throw new ForbiddenException(i18n.tr("content \"{0}\" is locked", existing.getId()));
                }

                entity = this.contentManager.updateContent(owner, InfoAdapter.contentInfoAdapter(content),
                    true);
            }
            else {
                entity = this.contentManager.createContent(owner, InfoAdapter.contentInfoAdapter(content));
            }
        }

        return entity;
    }

    @Override
    public ContentDTO createContent(String ownerKey, ContentDTO content) {

        Owner owner = this.getOwnerByKey(ownerKey);
        Content entity = this.createContentImpl(owner, content);

        this.contentAccessManager.refreshOwnerForContentAccess(owner);

        return this.translator.translate(entity, ContentDTO.class);
    }

    @Override
    @Transactional
    public Collection<ContentDTO> createBatchContent(String ownerKey, List<ContentDTO> contents) {

        for (ContentDTO content : contents) {
            this.validator.validateConstraints(content);
            this.validator.validateCollectionElementsNotNull(content::getModifiedProductIds);
        }

        Collection<ContentDTO> result = new LinkedList<>();
        Owner owner = this.getOwnerByKey(ownerKey);

        for (ContentDTO content : contents) {
            Content entity = this.createContentImpl(owner, content);
            result.add(this.translator.translate(entity, ContentDTO.class));
        }

        this.contentAccessManager.refreshOwnerForContentAccess(owner);

        return result;
    }

    @Override
    public ContentDTO updateContent(String ownerKey, String contentId, ContentDTO content) {

        this.validator.validateConstraints(content);
        this.validator.validateCollectionElementsNotNull(content::getModifiedProductIds);

        Owner owner = this.getOwnerByKey(ownerKey);
        Content existing  = this.fetchContent(owner, contentId);

        if (existing.isLocked()) {
            throw new ForbiddenException(i18n.tr("content \"{0}\" is locked", existing.getId()));
        }

        existing = this.contentManager.updateContent(owner, InfoAdapter.contentInfoAdapter(content), true);
        this.contentAccessManager.refreshOwnerForContentAccess(owner);

        return this.translator.translate(existing, ContentDTO.class);
    }

    @Override
    public void remove(String ownerKey, String contentId) {
        Owner owner = this.getOwnerByKey(ownerKey);
        Content content = this.fetchContent(owner, contentId);

        if (content.isLocked()) {
            throw new ForbiddenException(i18n.tr("content \"{0}\" is locked", content.getId()));
        }

        this.contentManager.removeContent(owner, content, true);
        this.contentAccessManager.refreshOwnerForContentAccess(owner);
    }
}
