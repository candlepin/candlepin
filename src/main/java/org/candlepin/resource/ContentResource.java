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
package org.candlepin.resource;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.controller.ContentManager;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.server.v1.ContentDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.ForbiddenException;
import org.candlepin.exceptions.IseException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.resource.server.v1.ContentApi;
import org.candlepin.resource.util.InfoAdapter;
import org.candlepin.resource.validation.DTOValidator;

import com.google.inject.persist.Transactional;

import org.xnap.commons.i18n.I18n;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.persistence.LockModeType;



public class ContentResource implements ContentApi {

    private final Configuration config;
    private final I18n i18n;
    private final ModelTranslator translator;
    private final DTOValidator validator;

    private final ContentManager contentManager;

    private final ContentCurator contentCurator;

    @Inject
    public ContentResource(
        Configuration config, I18n i18n, ModelTranslator translator, DTOValidator validator,
        ContentManager contentManager, ContentCurator contentCurator) {

        this.config = Objects.requireNonNull(config);
        this.i18n = Objects.requireNonNull(i18n);
        this.translator = Objects.requireNonNull(translator);
        this.validator = Objects.requireNonNull(validator);

        this.contentManager = Objects.requireNonNull(contentManager);

        this.contentCurator = Objects.requireNonNull(contentCurator);
    }

    /**
     * Retrieves a Content instance for the content with the specified ID. If no matching content
     * could be found, this method throws an exception.
     *
     * @param contentId
     *  The ID of the content to retrieve
     *
     * @param lockMode
     *  the locking mode with which to fetch the content, or null to omit locking the content
     *
     * @throws NotFoundException
     *  if no matching content could be found with the specified ID
     *
     * @return
     *  the content instance for the content with the specified ID
     */
    private Content resolveContentId(String contentId, LockModeType lockMode) {
        Content content = this.contentCurator.getContentById(contentId, lockMode);

        if (content == null) {
            throw new NotFoundException(i18n.tr("Content with ID \"{0}\" could not be found.", contentId));
        }

        return content;
    }

    /**
     * Alias for resolveContentId that omits the lock mode.
     *
     * @param contentId
     *  The ID of the content to retrieve
     *
     * @throws NotFoundException
     *  if no matching content could be found with the specified ID
     *
     * @return
     *  the content instance for the content with the specified ID
     */
    private Content resolveContentId(String contentId) {
        return this.resolveContentId(contentId, null);
    }

    /**
     * GET /content
     */
    @Override
    @Transactional
    public Stream<ContentDTO> getContentsByIds(List<String> contentIds) {
        Collection<Content> contents = contentIds != null && !contentIds.isEmpty() ?
            this.contentCurator.getContentsByIds(contentIds).values() :
            this.contentCurator.listAll().list();

        return contents.stream()
            .map(this.translator.getStreamMapper(Content.class, ContentDTO.class));
    }

    /**
     * GET /content/{content_id}
     */
    @Override
    @Transactional
    public ContentDTO getContentById(String contentId) {
        Content content = this.resolveContentId(contentId);

        return this.translator.translate(content, ContentDTO.class);
    }

    /**
     * POST /content/{content_id}
     */
    @Override
    @Transactional
    public ContentDTO createContent(ContentDTO contentDTO) {
        if (contentDTO.getId() == null || contentDTO.getId().isBlank()) {
            throw new BadRequestException(i18n.tr("content has a null or invalid ID"));
        }

        if (contentDTO.getType() == null || contentDTO.getType().isBlank()) {
            throw new BadRequestException(i18n.tr("content has a null or invalid type"));
        }

        if (contentDTO.getLabel() == null || contentDTO.getLabel().isBlank()) {
            throw new BadRequestException(i18n.tr("content has a null or invalid label"));
        }

        if (contentDTO.getName() == null || contentDTO.getName().isBlank()) {
            throw new BadRequestException(i18n.tr("content has a null or invalid name"));
        }

        if (contentDTO.getVendor() == null || contentDTO.getVendor().isBlank()) {
            throw new BadRequestException(i18n.tr("content has a null or invalid vendor"));
        }

        if (this.contentCurator.contentExistsById(contentDTO.getId())) {
            throw new BadRequestException(i18n.tr("a content already exists with ID: " + contentDTO.getId()));
        }

        this.validator.validateCollectionElementsNotNull(contentDTO::getModifiedProductIds);

        Content created = this.contentManager.createContent(InfoAdapter.contentInfoAdapter(contentDTO));
        return this.translator.translate(created, ContentDTO.class);
    }

    /**
     * PUT /content/{content_id}
     */
    @Override
    @Transactional
    public ContentDTO updateContent(String contentId, ContentDTO contentDTO) {
        // TODO: FIXME: No matter when we lock in this method, we run the risk of our update
        // clobbering a parallel update. Our best-case scenario is that we do our initial fetch
        // with a lock, but transaction isolation or Hibernate caching may still burn us there since
        // by the time we apply our lock, something else may have snuck through with its lookup.
        Content content = this.resolveContentId(contentId, LockModeType.PESSIMISTIC_WRITE);

        if (content.isLocked()) {
            throw new ForbiddenException(i18n.tr("content \"{0}\" is locked", content.getId()));
        }

        this.validator.validateCollectionElementsNotNull(contentDTO::getModifiedProductIds);

        // This field should be ignored during an update anyway, but just to be certain, we'll
        // override it with the ID specified in the request
        contentDTO.setId(contentId);

        Content updated = this.contentManager.updateContent(content,
            InfoAdapter.contentInfoAdapter(contentDTO));

        return this.translator.translate(updated, ContentDTO.class);
    }

    /**
     * DELETE /content/{content_id}
     */
    @Override
    @Transactional
    public void deleteContent(String contentId) {
        Content content = this.resolveContentId(contentId, LockModeType.PESSIMISTIC_WRITE);

        if (content.isLocked()) {
            throw new ForbiddenException(i18n.tr("content \"{0}\" is locked", content.getId()));
        }

        if (this.contentCurator.contentIsReferencedByProducts(content)) {
            throw new BadRequestException(i18n.tr(
                "Content \"{0}\" cannot be deleted while referenced by one or more subscriptions",
                contentId));
        }

        this.contentManager.deleteContent(content);
    }
}
