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
package org.candlepin.common.paging;

/**
 * Represents a request to page data coming back from Candlepin.
 */
public class PageRequest {
    /**
     * Represents the order things can be sorted in.
     */
    public enum Order {
        ASCENDING,
        DESCENDING
    }

    public static final String ORDER_PARAM = "order";
    public static final String SORT_BY_PARAM = "sort_by";
    public static final String PAGE_PARAM = "page";
    public static final String PER_PAGE_PARAM = "per_page";

    public static final Integer DEFAULT_PAGE = 1;
    public static final Integer DEFAULT_PER_PAGE = 10;
    public static final Order DEFAULT_ORDER = Order.DESCENDING;

    private Integer page;
    private Integer perPage;
    private String sortBy;
    private Order order;

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getPerPage() {
        return perPage;
    }

    public void setPerPage(Integer perPage) {
        this.perPage = perPage;
    }

    public String getSortBy() {
        return sortBy;
    }

    public void setSortBy(String sortBy) {
        this.sortBy = sortBy;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public boolean isPaging() {
        return perPage != null && page != null;
    }
}
