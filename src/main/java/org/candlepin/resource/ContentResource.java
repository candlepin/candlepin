/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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

import org.candlepin.config.Configuration;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.server.v1.ContentDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.ContentQueryBuilder;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.QueryBuilder.Inclusion;
import org.candlepin.paging.PagingUtilFactory;
import org.candlepin.resource.server.v1.ContentApi;
import org.candlepin.util.BatchSpliterator;
import org.candlepin.util.Util;

import com.google.inject.persist.Transactional;

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



public class ContentResource implements ContentApi {

    private final Configuration config;
    private final I18n i18n;
    private final OwnerCurator ownerCurator;
    private final ContentCurator contentCurator;
    private final ModelTranslator modelTranslator;
    private final PagingUtilFactory pagingUtilFactory;

    @Inject
    public ContentResource(
        Configuration config,
        I18n i18n,
        OwnerCurator ownerCurator,
        ContentCurator contentCurator,
        ModelTranslator modelTranslator,
        PagingUtilFactory pagingUtilFactory) {

        this.config = Objects.requireNonNull(config);
        this.i18n = Objects.requireNonNull(i18n);
        this.ownerCurator = Objects.requireNonNull(ownerCurator);
        this.contentCurator = Objects.requireNonNull(contentCurator);
        this.modelTranslator = Objects.requireNonNull(modelTranslator);
        this.pagingUtilFactory = Objects.requireNonNull(pagingUtilFactory);
    }

    /**
     * Retrieves an Owner instance for the owner with the specified key/account. If a matching owner could
     * not be found, this method throws an exception.
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
    private Owner resolveOwner(String key) {
        Owner owner = this.ownerCurator.getByKey(key);
        if (owner == null) {
            throw new NotFoundException(i18n.tr("Owner with key \"{0}\" was not found.", key));
        }

        return owner;
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
    // GET /contents
    public Stream<ContentDTO> getContents(List<String> ownerKeys, List<String> contentIds,
        List<String> contentLabels, String active, String custom) {

        List<Owner> owners = (ownerKeys != null ? ownerKeys.stream() : Stream.<String>empty())
            .map(this::resolveOwner)
            .toList();

        Inclusion activeInc = Inclusion.fromName(active, Inclusion.EXCLUSIVE)
            .orElseThrow(() ->
                new BadRequestException(i18n.tr("Invalid active inclusion type: {0}", active)));
        Inclusion customInc = Inclusion.fromName(custom, Inclusion.INCLUDE)
            .orElseThrow(() ->
                new BadRequestException(i18n.tr("Invalid custom inclusion type: {0}", custom)));

        ContentQueryBuilder queryBuilder = this.contentCurator.getContentQueryBuilder()
            .addOwners(owners)
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
    public ContentDTO getContentByUuid(String contentUuid) {
        Content content = this.contentCurator.get(contentUuid);

        if (content == null) {
            throw new NotFoundException(
                i18n.tr("Content with UUID \"{0}\" could not be found.", contentUuid));
        }

        return this.modelTranslator.translate(content, ContentDTO.class);
    }

}
