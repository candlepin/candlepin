/**
 * Copyright (c) 2009 - 2022 Red Hat, Inc.
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

package org.candlepin.spec.bootstrap.client.api;

/**
 * Simple data class for passing around paging information of generated client
 */
public final class Paging {

    private final int page;
    private final int perPage;
    private final String orderBy;
    private final String order;

    public static Paging firstPage() {
        return new Paging(0, 2, "id", "asc");
    }

    public static Paging withPage(int page) {
        return new Paging(page, 2, "id", "asc");
    }

    public Paging(int page, int perPage, String orderBy, String order) {
        this.page = page;
        this.perPage = perPage;
        this.orderBy = orderBy;
        this.order = order;
    }

    public int page() {
        return page;
    }

    public int perPage() {
        return perPage;
    }

    public String orderBy() {
        return orderBy;
    }

    public String order() {
        return order;
    }
}
