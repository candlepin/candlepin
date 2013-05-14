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
package org.candlepin.paging;

/**
 * Represents the presentation of data coming back from Candlepin.
 */
public class DataPresentation {
    /**
     * Represents the order things can be sorted in.
     */
    public enum Order {
        ASCENDING,
        DESCENDING
    }

    public static final Integer DEFAULT_OFFSET = new Integer(0);
    public static final Integer DEFAULT_LIMIT = new Integer(10);
    public static final Order DEFAULT_ORDER = Order.DESCENDING;

    private Integer offset;
    private Integer limit;
    private String sortBy;
    private Order order;

    public Integer getOffset() {
        return offset;
    }

    public void setOffset(Integer offset) {
        this.offset = offset;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
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
        return limit != null && offset != null;
    }
}
