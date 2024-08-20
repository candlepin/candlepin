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
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.server.v1.ContentDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.ContentCurator.ContentQueryArguments;
import org.candlepin.paging.Page;
import org.candlepin.paging.PageRequest;
import org.candlepin.resource.server.v1.ContentApi;

import org.jboss.resteasy.core.ResteasyContext;
import org.xnap.commons.i18n.I18n;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import javax.inject.Inject;



public class ContentResource implements ContentApi {

    private final ContentCurator contentCurator;
    private final I18n i18n;
    private final ModelTranslator modelTranslator;
    private final Configuration config;

    @Inject
    public ContentResource(ContentCurator contentCurator, I18n i18n, ModelTranslator modelTranslator,
        Configuration config) {
        this.i18n = Objects.requireNonNull(i18n);
        this.contentCurator = Objects.requireNonNull(contentCurator);
        this.modelTranslator = Objects.requireNonNull(modelTranslator);
        this.config = Objects.requireNonNull(config);
    }

    @Override
    public Stream<ContentDTO> getContents(List<String> ownerKeys, List<String> contentIds,
        List<String> contentLabels, String active, String custom) {

        // TODO: Finish this

        PageRequest pageRequest = ResteasyContext.getContextData(PageRequest.class);
        ContentQueryArguments queryArgs = new ContentQueryArguments();
        long count = this.contentCurator.getContentCount();

        if (pageRequest != null) {
            Page<Stream<ContentDTO>> pageResponse = new Page<>();
            pageResponse.setPageRequest(pageRequest);

            if (pageRequest.isPaging()) {
                queryArgs.setOffset((pageRequest.getPage() - 1) * pageRequest.getPerPage())
                    .setLimit(pageRequest.getPerPage());
            }

            if (pageRequest.getSortBy() != null) {
                boolean reverse = pageRequest.getOrder() == PageRequest.DEFAULT_ORDER;
                queryArgs.addOrder(pageRequest.getSortBy(), reverse);
            }

            pageResponse.setMaxRecords((int) count);

            // Store the page for the LinkHeaderResponseFilter
            ResteasyContext.pushContext(Page.class, pageResponse);
        }
        // If no paging was specified, force a limit on amount of results
        else {
            int maxSize = config.getInt(ConfigProperties.PAGING_MAX_PAGE_SIZE);
            if (count > maxSize) {
                String errmsg = this.i18n.tr("This endpoint does not support returning more than {0} " +
                    "results at a time, please use paging.", maxSize);
                throw new BadRequestException(errmsg);
            }
        }

        return this.contentCurator.listAll(queryArgs).stream()
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
