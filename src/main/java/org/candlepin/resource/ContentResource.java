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

import com.google.inject.persist.Transactional;

import org.xnap.commons.i18n.I18n;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

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

        return this.pagingUtilFactory.forClass(Content.class)
            .applyPaging(queryBuilder)
            .map(this.modelTranslator.getStreamMapper(Content.class, ContentDTO.class));
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
