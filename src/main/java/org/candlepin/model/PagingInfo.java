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

package org.candlepin.model;

import org.candlepin.paging.PageRequest;

import javax.persistence.metamodel.Attribute;

/*
    AbstractHibernateCurator (or a paging utility):
        - Criteria applyPaging(Paging, Criteria)
            - adds paging bits to the criteria object, and returns it
        - String applyPaging(Paging, String)
            - adds paging bits to the JPQL string, returning a new string


    Convert input strings to metadata fields -- might require intervention due to
    external naming and internal naming deltas.




    PageRequest: user-requested page info. May be incomplete or contain invalid values
    PagingInfo<T>
        - converted and validated page info; passed to curator layer
        - <T>: entity type for which the paging info should apply

        - getJPQLString() - ??
            - Returns a string containing the order and limits to append to a JPQL query to apply
              this paging info
        - getJPAOrder(javax.persistence.criteria.Path path) - ??
            - Returns a javax.persistence.criteria.Order instance containing the expression and order
              to apply this paging info to a given JPA criteria query.



    PagingUtil (name not set in stone; maybe PagingController, PagingManager, etc.)
        Note: Some of this might be fine to drop in AbstractHibernateCurator instead

        - translatePageRequest(PageRequest)
            - Converts a PageRequest into a complete PagingInfo object
                - resolves the requested field to a metamodel attribute
                - should this throw user-facing exceptions or internal exceptions?

        - applyPaging(Query)
        - applyPaging(String jpql)
            - Applies paging to the given query to simplify/standardize application







Major issues with paging:
- Original/current implementation relies on user knowing the names of attributes *as they exist on
  our internal model*

    - Can be addressed by having a translation layer that understands how to translate what properties
      a user will be familiar with via object output (that is, attributes on the DTOs) and can translate
      those to internal values where there are deviations. In most cases, a metamodel lookup will sort
      out any differences

- The page context for REST header population requires the total number of elements (unpaged) to be
  present. In virtually all cases, this means a second query needs to be performed to get that count.
  This is how things work today as well, but it's clunky to do manually, and doing it in an
  "automated" fashion without crossing responsibility boundries is non-trivial








*/









/**
 *
 */
public class PagingInfo<T extends AbstractHibernateObject> {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_PAGE_SIZE = 10;

    private int firstResult;
    private int maxResults;
    private Attribute<T, ?> sortField;
    private boolean ascending;


    public PagingInfo() {
        this.firstResult = 0;
        this.maxResults = DEFAULT_PAGE_SIZE;
        this.sortField = null;
        this.ascending = false;
    }



    // Accessors
    public int getFirstResult() {
        return this.firstResult;
    }

    public int getMaxResults() {
        return this.maxResults;
    }

    public Attribute<T, ?> getSortAttribute() {
        return this.sortField;
    }

    public boolean isAscending() {
        return this.ascending;
    }

    public boolean isDescending() {
        return !this.ascending;
    }



    // Mutators
    public PagingInfo setFirstResult(int firstResult) {
        if (firstResult < 0) {
            throw new IllegalArgumentException("firstResult is a negative value: " + firstResult);
        }

        this.firstResult = firstResult;
        return this;
    }

    public PagingInfo setMaxResults(int maxResults) {
        if (maxResults < 1) {
            throw new IllegalArgumentException("maxResults is less than one: " + maxResults);
        }

        this.maxResults = maxResults;
        return this;
    }

    // Alias of setMaxResults
    public PagingInfo setPageSize(int pageSize) {
        return this.setMaxResults(pageSize);
    }

    // Pseudo-alias to make it quicker to translate from the page request
    public PagingInfo setPage(int page) {
        if (page < 1) {
            throw new IllegalArgumentException("page is less than one: " + page);
        }

        this.firstResult = (page - 1) * this.maxResults;
        return this;
    }

    public PagingInfo setDescendingOrder() {
        this.ascending = false;
        return this;
    }

    public PagingInfo setAscendingOrder() {
        this.ascending = true;
        return this;
    }

    public PagingInfo setOrder(PageRequest.Order order) {
        if (order == null) {
            order = PageRequest.DEFAULT_ORDER;
        }

        return order == PageRequest.Order.ASCENDING ?
            this.setAscendingOrder() :
            this.setDescendingOrder();
    }

    public PagingInfo setSortAttribute(Attribute<T, ?> attribute) {
        if (attribute == null) {
            throw new IllegalArgumentException("attribute is null");
        }

        this.sortField = attribute;
        return this;
    }





}
