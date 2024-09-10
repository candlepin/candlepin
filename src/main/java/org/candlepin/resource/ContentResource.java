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
import org.candlepin.util.Util;

import com.google.inject.persist.Transactional;

import org.xnap.commons.i18n.I18n;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;
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

private static class BatchSpliterator<T> implements Spliterator<List<T>> {

        private final Spliterator<T> source;
        private final int batchSize;

        public BatchSpliterator(Spliterator<T> source, int batchSize) {
            this.source = Objects.requireNonNull(source);
            this.batchSize = batchSize;
        }

        @Override
        public int characteristics() {
            return this.source.characteristics();
        }

        @Override
        public long estimateSize() {
            return (long) Math.ceil((double) this.source.estimateSize() / (double) this.batchSize);
        }

        @Override
        public Spliterator<List<T>> trySplit() {
            // Don't bother splitting unless we're still above our desired batch size
            if (this.estimateSize() > this.batchSize) {
                Spliterator<T> split = this.source.trySplit();
                if (split != null) {
                    return new BatchSpliterator(split, this.batchSize);
                }
            }

            return null;
        }

        @Override
        public boolean tryAdvance(Consumer<? super List<T>> consumer) {
            if (consumer == null) {
                throw new IllegalArgumentException("consumer is null");
            }

            List<T> buffer = new ArrayList<T>(this.batchSize);
            for (long i = 0; i < this.batchSize && this.source.tryAdvance(buffer::add); ++i);

            if (buffer.isEmpty()) {
                return false;
            }

            consumer.accept(buffer);
            return true;
        }

        // public static <T> Stream<List<T>> batchStream(Stream<T> source, int batchSize) {
        //     BatchSpliterator spliterator = new BatchSpliterator<T>(source.spliterator, batchSize);
        //     return StreamSupport.stream(spliterator, source.isParallel());
        // }

    }

    private static class ContentTranslator {

        private final ContentCurator contentCurator;
        private final int batchSize;

        public ContentTranslator(ContentCurator contentCurator) {
            this.contentCurator = Objects.requireNonNull(contentCurator);
            this.batchSize = this.contentCurator.getInBlockSize();
        }

        public ContentDTO translate(Content source) {
            if (source == null) {
                return null;
            }

            return new ContentDTO()
                .uuid(source.getUuid())
                .id(source.getId())
                .type(source.getType())
                .label(source.getLabel())
                .name(source.getName())
                .created(Util.toDateTime(source.getCreated()))
                .updated(Util.toDateTime(source.getUpdated()))
                .vendor(source.getVendor())
                .contentUrl(source.getContentUrl())
                .requiredTags(source.getRequiredTags())
                .releaseVer(source.getReleaseVersion())
                .gpgUrl(source.getGpgUrl())
                .metadataExpire(source.getMetadataExpiration())
                .modifiedProductIds(source.getModifiedProductIds())
                .arches(source.getArches());
        }

        public List<ContentDTO> translate(Collection<Content> contents) {
            if (contents == null) {
                return null;
            }

            List<String> cuuids = contents.stream()
                .map(Content::getUuid)
                .toList();

            Map<String, Set<String>> requiredPIDMap = this.contentCurator.getRequiredProductIds(cuuids);

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
                .modifiedProductIds(requiredPIDMap.getOrDefault(content.getUuid(), Set.of()))
                .arches(content.getArches());

            return contents.stream()
                .map(translator)
                .toList();
        }

        public Stream<ContentDTO> translate(Stream<Content> source) {
            BatchSpliterator<Content> spliterator = new BatchSpliterator<>(source.spliterator(),
                this.batchSize);

            return StreamSupport.stream(spliterator, source.isParallel())
                .map(this::translate)
                .flatMap(Collection::stream);
        }

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

        Stream<Content> contentStream = this.pagingUtilFactory.forClass(Content.class)
            .applyPaging(queryBuilder);

        return new ContentTranslator(this.contentCurator)
            .translate(contentStream);
    }

    @Override
    public ContentDTO getContentByUuid(String contentUuid) {
        Content content = this.contentCurator.getByUuid(contentUuid);

        if (content == null) {
            throw new NotFoundException(
                i18n.tr("Content with UUID \"{0}\" could not be found.", contentUuid));
        }

        return this.modelTranslator.translate(content, ContentDTO.class);
    }

}
