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
package org.candlepin.resource.util;

import org.candlepin.exceptions.BadRequestException;
import org.candlepin.model.AbstractHibernateObject;
import org.candlepin.model.QueryArguments;
import org.candlepin.paging.Page;
import org.candlepin.paging.PageRequest;
import org.candlepin.util.Util;

import org.jboss.resteasy.core.ResteasyContext;
import org.xnap.commons.i18n.I18n;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.EntityType;

import java.util.Objects;


@Singleton
public class PagingUtil {

    private final Provider<I18n> i18nProvider;
    private final Provider<EntityManager> entityManagerProvider;

    @Inject
    public PagingUtil(Provider<I18n> i18nProvider, Provider<EntityManager> entityManagerProvider) {
        this.i18nProvider = Objects.requireNonNull(i18nProvider);
        this.entityManagerProvider = Objects.requireNonNull(entityManagerProvider);
    }

    private String tr(String msg) {
        I18n i18n = this.i18nProvider.get();
        return i18n.tr(msg);
    }

    public boolean isPaging() {
        PageRequest pageRequest = ResteasyContext.getContextData(PageRequest.class);

        if (pageRequest == null) {
            return false;
        }

        return pageRequest.isPaging();
    }

    public <T extends AbstractHibernateObject> QueryArguments<T, ?> getPagingQueryArguments(
        Class<T> entityType, QueryArguments<T, ?> queryArguments, boolean required) {

        PageRequest pageRequest = ResteasyContext.getContextData(PageRequest.class);
        if (pageRequest == null) {
            if (required) {
                // TODO: Write a better exception message
                throw new BadRequestException(this.tr("This request must be paged"));
            }

            return null;
        }

        EntityType<T> metamodel = this.entityManagerProvider.get()
            .getMetamodel()
            .entity(entityType);

        // TODO:
        // add some configuration layer so we can do per-entity default fields and translations
        // for now, default to "updated" with no translation
        String sortField = Util.firstOf(pageRequest.getSortBy(), "updated");
        Attribute<T, ?> attribute;

        try {
            attribute = (Attribute<T, ?>) metamodel.getAttribute(sortField);
        }
        catch (IllegalArgumentException e) {
            // TODO: clean up exception message
            throw new BadRequestException(this.tr("cannot order by field: \"{}\"; attribute not found"));
        }

        return queryArguments.setLimits(pageRequest.getPage(), pageRequest.getPerPage())
            .addOrder(attribute, !(pageRequest.getOrder() == PageRequest.Order.ASCENDING));
    }

    public <T extends AbstractHibernateObject> QueryArguments<T, ?> getPagingQueryArguments(
        Class<T> entityType, boolean required) {

        return this.getPagingQueryArguments(entityType, new QueryArguments<>(), required);
    }


    public void updatePagingRecordCount(long recordCount) {
        PageRequest pageRequest = ResteasyContext.getContextData(PageRequest.class);
        if (pageRequest == null) {
            return;
        }

        Page page = ResteasyContext.getContextData(Page.class);
        if (page == null) {
            page = new Page()
                .setPageRequest(pageRequest);

            ResteasyContext.pushContext(Page.class, page);
        }

        page.setMaxRecords((int) recordCount);
    }

}
