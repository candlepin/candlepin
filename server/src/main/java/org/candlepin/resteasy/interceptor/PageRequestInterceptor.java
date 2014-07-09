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
package org.candlepin.resteasy.interceptor;

import org.candlepin.exceptions.BadRequestException;
import org.candlepin.paging.PageRequest;
import org.candlepin.paging.Paginate;
import org.candlepin.paging.PageRequest.Order;

import com.google.inject.Inject;

import org.jboss.resteasy.annotations.interception.ServerInterceptor;
import org.jboss.resteasy.core.ResourceMethod;
import org.jboss.resteasy.core.ServerResponse;
import org.jboss.resteasy.spi.Failure;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.jboss.resteasy.spi.interception.AcceptedByMethod;
import org.jboss.resteasy.spi.interception.PreProcessInterceptor;
import org.xnap.commons.i18n.I18n;

import java.lang.reflect.Method;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.Provider;

/**
 * DataPresentationInterceptor
 */
@Provider
@ServerInterceptor
public class PageRequestInterceptor implements PreProcessInterceptor,
    AcceptedByMethod {

    private I18n i18n;

    @Inject
    public PageRequestInterceptor(I18n i18n) {
        super();
        this.i18n = i18n;
    }

    @Override
    public boolean accept(Class declaring, Method method) {
        return method.isAnnotationPresent(Paginate.class);
    }

    @Override
    public ServerResponse preProcess(HttpRequest request, ResourceMethod method)
        throws Failure, WebApplicationException {
        PageRequest p = null;

        MultivaluedMap<String, String> params = request.getUri().getQueryParameters();

        String page = params.getFirst(PageRequest.PAGE_PARAM);
        String perPage = params.getFirst(PageRequest.PER_PAGE_PARAM);
        String order = params.getFirst(PageRequest.ORDER_PARAM);
        String sortBy = params.getFirst(PageRequest.SORT_BY_PARAM);

        if (page != null || perPage != null || order != null || sortBy != null) {
            p = new PageRequest();

            if (order == null) {
                p.setOrder(PageRequest.DEFAULT_ORDER);
            }
            else {
                p.setOrder(readOrder(order));
            }

            /* We'll leave it to the curator layer to figure out what to sort by if
             * sortBy is null. */
            p.setSortBy(sortBy);

            try {
                if (page == null && perPage != null) {
                    p.setPage(PageRequest.DEFAULT_PAGE);
                    p.setPerPage(readInteger(perPage));
                }
                else if (page != null && perPage == null) {
                    p.setPage(readInteger(page));
                    p.setPerPage(PageRequest.DEFAULT_PER_PAGE);
                }
                else {
                    p.setPage(readInteger(page));
                    p.setPerPage(readInteger(perPage));
                }
            }
            catch (NumberFormatException nfe) {
                throw new BadRequestException(i18n.tr("offset and limit parameters" +
                    " must be positive integers"), nfe);
            }
        }

        ResteasyProviderFactory.pushContext(PageRequest.class, p);

        return null;
    }

    private Order readOrder(String order) {
        if ("ascending".equalsIgnoreCase(order) || "asc".equalsIgnoreCase(order)) {
            return Order.ASCENDING;
        }
        else if ("descending".equalsIgnoreCase(order) || "desc".equalsIgnoreCase(order)) {
            return Order.DESCENDING;
        }

        throw new BadRequestException(i18n.tr("the order parameter must be either" +
                " 'ascending' or 'descending'"));
    }

    private Integer readInteger(String value) {
        if (value != null) {
            int i = Integer.parseInt(value);

            if (i <= 0) {
                throw new NumberFormatException(i18n.tr("Expected a positive integer."));
            }
            return i;
        }

        return null;
    }
}
