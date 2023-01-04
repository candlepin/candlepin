/**
 * Copyright (c) 2009 - 2022 Red Hat, Inc.
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
import org.candlepin.controller.ContentAccessManager;
import org.candlepin.controller.ContentManager;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.server.v1.ContentDTO;
import org.candlepin.exceptions.ForbiddenException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerContentCurator;
import org.candlepin.model.OwnerCurator;
import org.candlepin.resource.server.v1.OwnerContentApi;
import org.candlepin.resource.util.InfoAdapter;
import org.candlepin.resource.validation.DTOValidator;
import org.candlepin.service.UniqueIdGenerator;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.xnap.commons.i18n.I18n;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class OwnerContentResource implements OwnerContentApi {
    private DTOValidator validator;
    private ModelTranslator translator;
    private ContentAccessManager contentAccessManager;
    private I18n i18n;
    private ContentManager contentManager;
    private OwnerContentCurator ownerContentCurator;
    private UniqueIdGenerator idGenerator;
    private OwnerCurator ownerCurator;

    @Inject
    @SuppressWarnings("checkstyle:parameternumber")
    public OwnerContentResource(OwnerCurator ownerCurator,
        I18n i18n,
        ModelTranslator translator,
        DTOValidator validator,
        OwnerContentCurator ownerContentCurator,
        ContentAccessManager contentAccessManager,
        ContentManager contentManager,
        UniqueIdGenerator idGenerator) {

        this.ownerCurator = ownerCurator;
        this.i18n = i18n;
        this.translator = translator;
        this.validator = validator;
        this.ownerContentCurator = ownerContentCurator;
        this.contentAccessManager = contentAccessManager;
        this.contentManager = contentManager;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    public ContentDTO createContent(String ownerKey, ContentDTO content) {
        this.validator.validateCollectionElementsNotNull(content::getModifiedProductIds);

        Owner owner = this.getOwnerByKey(ownerKey);
        Content entity = this.createContentImpl(owner, content);

        this.contentAccessManager.syncOwnerLastContentUpdate(owner);

        return this.translator.translate(entity, ContentDTO.class);
    }

    @Override
    public ContentDTO getOwnerContent(
        @Verify(Owner.class) String ownerKey, String contentId) {

        Owner owner = this.getOwnerByKey(ownerKey);
        Content content = this.fetchContent(owner, contentId);

        return this.translator.translate(content, ContentDTO.class);
    }

    @Override
    public CandlepinQuery<ContentDTO> listOwnerContent(@Verify(Owner.class) String ownerKey) {
        Owner owner = this.getOwnerByKey(ownerKey);
        CandlepinQuery<Content> query = this.ownerContentCurator.getContentByOwnerCPQ(owner);

        return this.translator.translateQuery(query, ContentDTO.class);
    }


    @Override
    @Transactional
    public Collection<ContentDTO> createBatchContent(String ownerKey, List<ContentDTO> contents) {
        for (ContentDTO content : contents) {
            this.validator.validateCollectionElementsNotNull(content::getModifiedProductIds);
        }

        Owner owner = this.getOwnerByKey(ownerKey);

        List<ContentDTO> output = contents.stream()
            .map(content -> this.createContentImpl(owner, content))
            .map(this.translator.getStreamMapper(Content.class, ContentDTO.class))
            .collect(Collectors.toList());

        this.contentAccessManager.syncOwnerLastContentUpdate(owner);

        return output;
    }

    @Override
    @Transactional
    public ContentDTO updateContent(String ownerKey, String contentId, ContentDTO content) {
        this.validator.validateCollectionElementsNotNull(content::getModifiedProductIds);

        Owner owner = this.getOwnerByKey(ownerKey);
        Content existing = this.fetchContent(owner, contentId);

        if (existing.isLocked()) {
            throw new ForbiddenException(i18n.tr("content \"{0}\" is locked", existing.getId()));
        }

        Content updated = this.contentManager
            .updateContent(owner, InfoAdapter.contentInfoAdapter(content.id(contentId)), true);
        this.contentAccessManager.syncOwnerLastContentUpdate(owner);

        return this.translator.translate(updated, ContentDTO.class);
    }

    @Override
    @Transactional
    public void remove(String ownerKey, String contentId) {
        Owner owner = this.getOwnerByKey(ownerKey);
        Content content = this.fetchContent(owner, contentId);

        if (content.isLocked()) {
            throw new ForbiddenException(i18n.tr("content \"{0}\" is locked", content.getId()));
        }

        this.contentManager.removeContent(owner, content, true);
        this.contentAccessManager.syncOwnerLastContentUpdate(owner);
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
     *
     * @httpcode 200
     * @httpcode 404
     */
    protected Owner getOwnerByKey(String key) {
        Owner owner = this.ownerCurator.getByKey(key);
        if (owner == null) {
            throw new NotFoundException(i18n.tr("Owner with key \"{0}\" was not found.", key));
        }

        return owner;
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
}
