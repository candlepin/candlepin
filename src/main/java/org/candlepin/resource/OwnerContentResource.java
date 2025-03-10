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

import org.candlepin.auth.Verify;
import org.candlepin.controller.ContentAccessManager;
import org.candlepin.controller.ContentManager;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.server.v1.ContentDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.ForbiddenException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.ContentQueryBuilder;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.QueryBuilder.Inclusion;
import org.candlepin.paging.PagingUtilFactory;
import org.candlepin.resource.server.v1.OwnerContentApi;
import org.candlepin.resource.util.InfoAdapter;
import org.candlepin.resource.validation.DTOValidator;
import org.candlepin.service.UniqueIdGenerator;
import org.candlepin.service.model.ContentInfo;
import org.candlepin.util.BatchSpliterator;
import org.candlepin.util.Util;

import com.google.inject.persist.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.inject.Inject;
import javax.persistence.LockModeType;



public class OwnerContentResource implements OwnerContentApi {
    private static final Logger log = LoggerFactory.getLogger(OwnerContentResource.class);

    private final I18n i18n;
    private final UniqueIdGenerator idGenerator;
    private final DTOValidator validator;
    private final ModelTranslator translator;
    private final PagingUtilFactory pagingUtilFactory;
    private final OwnerCurator ownerCurator;
    private final ContentAccessManager contentAccessManager;
    private final ContentManager contentManager;
    private final ContentCurator contentCurator;

    @Inject
    @SuppressWarnings("checkstyle:parameternumber")
    public OwnerContentResource(
        I18n i18n,
        UniqueIdGenerator idGenerator,
        DTOValidator validator,
        ModelTranslator translator,
        PagingUtilFactory pagingUtilFactory,
        OwnerCurator ownerCurator,
        ContentAccessManager contentAccessManager,
        ContentManager contentManager,
        ContentCurator contentCurator) {

        this.i18n = Objects.requireNonNull(i18n);
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.validator = Objects.requireNonNull(validator);
        this.translator = Objects.requireNonNull(translator);
        this.pagingUtilFactory = Objects.requireNonNull(pagingUtilFactory);
        this.ownerCurator = Objects.requireNonNull(ownerCurator);
        this.contentAccessManager = Objects.requireNonNull(contentAccessManager);
        this.contentManager = Objects.requireNonNull(contentManager);
        this.contentCurator = Objects.requireNonNull(contentCurator);
    }

    /**
     * Validates that fields that are required for content creation are populated and non-empty.
     *
     * @param dto
     *  the content DTO to validate
     *
     * @throws BadRequestException
     *  if the DTO does not pass validation
     */
    private void validateContentForCreation(ContentDTO dto) {
        if (dto == null) {
            return;
        }

        this.validator.validateCollectionElementsNotNull(dto::getModifiedProductIds);

        if (dto.getLabel() == null || dto.getLabel().isBlank()) {
            throw new BadRequestException(this.i18n.tr("content label cannot be null or empty"));
        }

        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BadRequestException(this.i18n.tr("content name cannot be null or empty"));
        }

        if (dto.getType() == null || dto.getType().isBlank()) {
            throw new BadRequestException(this.i18n.tr("content type cannot be null or empty"));
        }

        if (dto.getVendor() == null || dto.getVendor().isBlank()) {
            throw new BadRequestException(this.i18n.tr("content vendor cannot be null or empty"));
        }
    }

    /**
     * Validates that the specified entity belongs to the same namespace as the given organization.
     * If the entity is part of the global namespace, or another organization's namespace, this
     * method throws a ForbiddenException.
     *
     * @param owner
     *  the org to use to validate the content entity
     *
     * @param content
     *  the content entity to validate
     *
     * @throws ForbiddenException
     *  if the entity is not part of the given organization's namespace
     */
    private void validateContentNamespace(Owner owner, Content content) {
        String namespace = owner != null ? owner.getKey() : "";

        if (!namespace.equals(content.getNamespace())) {
            throw new ForbiddenException(this.i18n.tr(
                "Cannot modify or remove contents defined outside of the organization's namespace"));
        }
    }

    /**
     * Attempts to resolve the given content ID reference to a content for the given organization.
     * This method will first attempt to resolve the content reference in the org's namespace, but
     * will fall back to the global namespace if that resolution attempt fails. If the content ID
     * cannot be resolved, this method throws an exception.
     *
     * @param owner
     *  the organization for which to resolve the content reference
     *
     * @param contentId
     *  the content ID to resolve
     *
     * @param lockModeType
     *  the type of database lock to apply to any content instance returned by this method; if null
     *  or LockModeType.NONE, no database lock will be applied
     *
     * @throws IllegalArgumentException
     *  if owner is null, or if contentId is null or empty
     *
     * @throws NotFoundException
     *  if a content with the specified ID cannot be found within the context of the given org
     *
     * @return
     *  the content for the specified ID
     */
    private Content resolveContentId(Owner owner, String contentId, LockModeType lockModeType) {
        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        if (contentId == null || contentId.isEmpty()) {
            throw new IllegalArgumentException("contentId is null or empty");
        }

        Content content = this.contentCurator.resolveContentId(owner.getKey(), contentId, lockModeType);

        if (content == null) {
            throw new NotFoundException(
                i18n.tr("Unable to find a content with the ID \"{0}\" for owner \"{1}\"",
                    contentId, owner.getKey()));
        }

        return content;
    }

    /**
     * Creates or updates content using the content data provided
     *
     * @param owner
     *  The owner for which to create the new content
     *
     * @param cdto
     *  The content data to use to create or update content
     *
     * @return
     *  the updated or newly created content entity
     */
    private Content createContentImpl(Owner owner, ContentDTO cdto) {
        // TODO: check if arches have changed ??

        String cid = cdto.getId();

        // If we don't have an ID, this is a creation request due to ancient convention
        if (cid == null || cid.isBlank()) {
            cdto.setId(this.idGenerator.generateId());
            return this.contentManager.createContent(owner, InfoAdapter.contentInfoAdapter(cdto));
        }

        ContentInfo cinfo = InfoAdapter.contentInfoAdapter(cdto);

        // We had an ID, so check if this is actually an update masquerading as a create op
        Content existing = this.contentCurator.getContentById(owner.getKey(), cid);
        if (existing != null) {
            return this.contentManager.updateContent(owner, existing, cinfo, true);
        }

        // It's not, fall back to normal creation
        return this.contentManager.createContent(owner, cinfo);
    }

    /**
     * Translates the collection of content elements to content DTOs, fetching lazily loaded fields in
     * bulk.
     *
     * @param source
     *  the source collection of content objects to translate
     *
     * @return
     *  a list of translated content DTOs, or null if the given collection was null
     */
    private List<ContentDTO> translate(Collection<Content> source) {
        // Impl note:
        // This behavior should eventually be moved to the translators, and the translator framework updated
        // to support bulk translations and generally stuff added after Java 8. However, doing so is a
        // much larger task than fixing this particular translation has far more concerns in terms of
        // both scope and design.

        if (source == null) {
            return null;
        }

        List<String> cuuids = source.stream()
            .filter(Objects::nonNull)
            .map(Content::getUuid)
            .filter(Objects::nonNull)
            .toList();

        Map<String, Set<String>> requiredProductsMap = this.contentCurator.getRequiredProductIds(cuuids);

        Function<Content, ContentDTO> translator = content -> new ContentDTO()
            .uuid(content.getUuid())
            .id(content.getId())
            .type(content.getType())
            .label(content.getLabel())
            .name(content.getName())
            .created(Util.toDateTime(content.getCreated()))
            .updated(Util.toDateTime(content.getUpdated()))
            .vendor(content.getVendor())
            .contentUrl(content.getContentUrl())
            .requiredTags(content.getRequiredTags())
            .releaseVer(content.getReleaseVersion())
            .gpgUrl(content.getGpgUrl())
            .metadataExpire(content.getMetadataExpiration())
            .modifiedProductIds(requiredProductsMap.getOrDefault(content.getUuid(), Set.of()))
            .arches(content.getArches());

        return source.stream()
            .filter(Objects::nonNull)
            .map(translator)
            .toList();
    }

    /**
     * Translates the stream of content elements to content DTOs, fetching lazily loaded fields in
     * bulk.
     *
     * @param source
     *  the source stream of content objects to translate
     *
     * @return
     *  a stream of translated content DTOs, or null if the given stream was null
     */
    private Stream<ContentDTO> translate(Stream<Content> source) {
        if (source == null) {
            return null;
        }

        // TODO: If this behavior doesn't make it into the Java API, add it ourselves with a custom Stream
        // implementation somehow
        int batchSize = this.contentCurator.getInBlockSize();
        BatchSpliterator<Content> spliterator = new BatchSpliterator<>(source.spliterator(), batchSize);

        return StreamSupport.stream(spliterator, source.isParallel())
            .map(this::translate)
            .flatMap(Collection::stream);
    }

    @Override
    @Transactional
    public Stream<ContentDTO> createContentBatch(String ownerKey, List<ContentDTO> contents) {
        Owner owner = this.getOwnerByKey(ownerKey);
        String namespace = owner.getKey();

        contents.forEach(this::validateContentForCreation);

        // Ensure that the content IDs are either net-new or only reference content defined in this
        // org's namespace
        List<String> cids = contents.stream()
            .map(ContentDTO::getId)
            .toList();

        Map<String, Content> conflictingContent = this.contentCurator.getContentsByIds(null, cids);
        if (!conflictingContent.isEmpty()) {
            throw new BadRequestException(
                this.i18n.tr("One or more contents are already defined in the global namespace: {0}",
                conflictingContent.keySet()));
        }

        // Create!
        return contents.stream()
            .map(cdto -> this.createContentImpl(owner, cdto))
            .map(this.translator.getStreamMapper(Content.class, ContentDTO.class));
    }

    @Override
    @Transactional
    public ContentDTO createContent(String ownerKey, ContentDTO content) {
        return this.createContentBatch(ownerKey, List.of(content))
            .findAny()
            .orElse(null);
    }

    @Override
    @Transactional
    public ContentDTO getContentById(@Verify(Owner.class) String ownerKey, String contentId) {
        Owner owner = this.getOwnerByKey(ownerKey);
        Content content = this.resolveContentId(owner, contentId, null);

        return this.translator.translate(content, ContentDTO.class);
    }

    @Override
    @Transactional
    // GET /owners/{key}/contents
    public Stream<ContentDTO> getContentsByOwner(@Verify(Owner.class) String ownerKey,
        List<String> contentIds, List<String> contentLabels, String active, String custom) {

        Owner owner = this.getOwnerByKey(ownerKey);

        Inclusion activeInc = Inclusion.fromName(active, Inclusion.EXCLUSIVE)
            .orElseThrow(() ->
                new BadRequestException(i18n.tr("Invalid active inclusion type: {0}", active)));
        Inclusion customInc = Inclusion.fromName(custom, Inclusion.INCLUDE)
            .orElseThrow(() ->
                new BadRequestException(i18n.tr("Invalid custom inclusion type: {0}", custom)));

        ContentQueryBuilder queryBuilder = this.contentCurator.getContentQueryBuilder()
            .addOwners(owner)
            .addContentIds(contentIds)
            .addContentLabels(contentLabels)
            .setActive(activeInc)
            .setCustom(customInc);

        Stream<Content> content = this.pagingUtilFactory.forClass(Content.class)
            .applyPaging(queryBuilder);

        return this.translate(content);
    }

    @Override
    @Transactional
    public ContentDTO updateContent(String ownerKey, String contentId, ContentDTO cdto) {
        Owner owner = this.getOwnerByKey(ownerKey);
        Content content = this.resolveContentId(owner, contentId, LockModeType.PESSIMISTIC_WRITE);

        this.validateContentNamespace(owner, content);
        this.validator.validateCollectionElementsNotNull(cdto::getModifiedProductIds);

        ContentInfo update = InfoAdapter.contentInfoAdapter(cdto);
        Content updated = this.contentManager.updateContent(owner, content, update, true);

        return this.translator.translate(updated, ContentDTO.class);
    }

    @Override
    @Transactional
    public void removeContent(String ownerKey, String contentId) {
        Owner owner = this.getOwnerByKey(ownerKey);
        Content content = this.resolveContentId(owner, contentId, LockModeType.PESSIMISTIC_WRITE);

        this.validateContentNamespace(owner, content);

        if (this.contentCurator.contentHasParentProducts(content)) {
            throw new BadRequestException(i18n.tr(
                "Content \"{0}\" cannot be deleted while referenced by one or more products",
                contentId));
        }

        this.contentManager.removeContent(owner, content);
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
    private Owner getOwnerByKey(String key) {
        Owner owner = this.ownerCurator.getByKey(key);
        if (owner == null) {
            throw new NotFoundException(i18n.tr("Owner with key \"{0}\" was not found.", key));
        }

        return owner;
    }
}
