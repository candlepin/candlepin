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
package org.candlepin.resteasy.filter;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.paging.PageRequest;
import org.candlepin.paging.PageRequest.Order;

import org.jboss.resteasy.core.ResteasyContext;
import org.xnap.commons.i18n.I18n;

import java.util.Objects;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.Provider;



/**
 * PageRequestFilter parses a common set of query parameters used to page through results from Candlepin.
 */
@Provider
@Priority(Priorities.USER)
public class PageRequestFilter implements ContainerRequestFilter {

    private final Configuration config;
    private final javax.inject.Provider<I18n> i18nProvider;

    private final int defaultPageSize;
    private final int maxPageSize;

    @Inject
    public PageRequestFilter(Configuration config, javax.inject.Provider<I18n> i18nProvider) {
        this.config = Objects.requireNonNull(config);
        this.i18nProvider = Objects.requireNonNull(i18nProvider);

        this.defaultPageSize = this.config.getInt(ConfigProperties.PAGING_DEFAULT_PAGE_SIZE);
        this.maxPageSize = this.config.getInt(ConfigProperties.PAGING_MAX_PAGE_SIZE);
    }

    private BadRequestException buildPageSizeException(int maxSize) {
        I18n i18n = this.i18nProvider.get();
        return new BadRequestException(i18n.tr("page size cannot exceed {0} elements", maxSize));
    }

    private BadRequestException buildIntegerParsingException(String field) {
        I18n i18n = this.i18nProvider.get();
        return new BadRequestException(i18n.tr("param \"{0}\" must be a positive integer", field));
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        PageRequest pageRequest = null;

        MultivaluedMap<String, String> params = requestContext.getUriInfo().getQueryParameters();

        String page = params.getFirst(PageRequest.PAGE_PARAM);
        String perPage = params.getFirst(PageRequest.PER_PAGE_PARAM);
        String order = params.getFirst(PageRequest.ORDER_PARAM);
        String sortBy = params.getFirst(PageRequest.SORT_BY_PARAM);

        if (page != null || perPage != null || order != null || sortBy != null) {
            pageRequest = new PageRequest()
                .setOrder(PageRequest.DEFAULT_ORDER);

            if (order != null) {
                pageRequest.setOrder(readOrder(order));
            }

            // We'll leave it to the curator layer to figure out what to sort by if sortBy is null.
            pageRequest.setSortBy(sortBy);

            if (page != null || perPage != null) {
                pageRequest.setPage(PageRequest.DEFAULT_PAGE)
                    .setPerPage(this.defaultPageSize);

                if (page != null) {
                    pageRequest.setPage(this.readInteger(PageRequest.PAGE_PARAM, page));
                }

                if (perPage != null) {
                    int perPageValue = this.readInteger(PageRequest.PER_PAGE_PARAM, perPage);
                    if (perPageValue > this.maxPageSize) {
                        throw this.buildPageSizeException(this.maxPageSize);
                    }

                    pageRequest.setPerPage(perPageValue);
                }
            }
        }

        ResteasyContext.pushContext(PageRequest.class, pageRequest);
    }

    private Order readOrder(String order) {
        if ("ascending".equalsIgnoreCase(order) || "asc".equalsIgnoreCase(order)) {
            return Order.ASCENDING;
        }
        else if ("descending".equalsIgnoreCase(order) || "desc".equalsIgnoreCase(order)) {
            return Order.DESCENDING;
        }

        I18n i18n = this.i18nProvider.get();
        throw new BadRequestException(i18n.tr("the order parameter must be either" +
            " \"ascending\" or \"descending\""));
    }

    private int readInteger(String field, String value) {
        try {
            int parsed = Integer.parseInt(value);

            if (parsed <= 0) {
                throw this.buildIntegerParsingException(field);
            }

            return parsed;
        }
        catch (NumberFormatException e) {
            throw this.buildIntegerParsingException(field);
        }
    }
}
