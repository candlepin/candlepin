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
import org.candlepin.dto.api.server.v1.DeletedConsumerDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.model.DeletedConsumer;
import org.candlepin.model.DeletedConsumerCurator;
import org.candlepin.model.DeletedConsumerCurator.DeletedConsumerQueryArguments;
import org.candlepin.paging.Page;
import org.candlepin.paging.PageRequest;
import org.candlepin.resource.server.v1.DeletedConsumerApi;

import com.google.inject.persist.Transactional;

import org.jboss.resteasy.core.ResteasyContext;
import org.xnap.commons.i18n.I18n;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.stream.Stream;

import javax.inject.Inject;

/**
 * DeletedConsumerResource
 */
public class DeletedConsumerResource implements DeletedConsumerApi {
    private final DeletedConsumerCurator deletedConsumerCurator;
    private final I18n i18n;
    private final ModelTranslator translator;
    private final Configuration config;

    @Inject
    public DeletedConsumerResource(DeletedConsumerCurator deletedConsumerCurator, I18n i18n,
        ModelTranslator translator, Configuration config) {
        this.i18n = Objects.requireNonNull(i18n);
        this.deletedConsumerCurator = Objects.requireNonNull(deletedConsumerCurator);
        this.translator = Objects.requireNonNull(translator);
        this.config = Objects.requireNonNull(config);
    }

    @Override
    @Transactional
    public Stream<DeletedConsumerDTO> listByDate(OffsetDateTime date, Integer page, Integer perPage,
        String order, String sortBy) {
        PageRequest pageRequest = ResteasyContext.getContextData(PageRequest.class);
        DeletedConsumerQueryArguments queryArgs = new DeletedConsumerQueryArguments();
        long count = this.deletedConsumerCurator.getDeletedConsumerCount(queryArgs);

        if (pageRequest != null) {
            Page<Stream<DeletedConsumerDTO>> pageResponse = new Page<>();
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

        return this.deletedConsumerCurator.listAll(queryArgs).stream()
            .map(this.translator.getStreamMapper(DeletedConsumer.class, DeletedConsumerDTO.class));
    }
}
