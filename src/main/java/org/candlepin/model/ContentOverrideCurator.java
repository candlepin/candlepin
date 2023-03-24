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
package org.candlepin.model;

import com.google.inject.persist.Transactional;

import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Restrictions;

import java.util.List;



/**
 * ContentOverrideCurator
 *
 * @param <T> ContentOverride type
 * @param <Parent> parent of the content override, Consumer or ActivationKey for example
 */
public abstract class ContentOverrideCurator<T extends ContentOverride<T, Parent>,
    Parent extends AbstractHibernateObject> extends AbstractHibernateCurator<T> {

    private String parentAttr;

    /**
     * @param entityType
     */
    public ContentOverrideCurator(Class<T> entityType, String parentAttrName) {
        super(entityType);
        this.parentAttr = parentAttrName;
    }

    @SuppressWarnings("unchecked")
    public CandlepinQuery<T> getList(Parent parent) {
        DetachedCriteria criteria = DetachedCriteria.forClass(this.entityType())
            .add(Restrictions.eq(parentAttr, parent));

        return this.cpQueryFactory.<T>buildQuery(this.currentSession(), criteria);
    }

    public void removeByName(Parent parent, String contentLabel, String name) {
        List<T> overrides = currentSession()
            .createCriteria(this.entityType())
            .add(Restrictions.eq(parentAttr, parent))
            .add(Restrictions.eq("contentLabel", contentLabel))
            .add(Restrictions.eq("name", name).ignoreCase())
            .list();

        for (T cco : overrides) {
            delete(cco);
        }
    }

    public void removeByContentLabel(Parent parent, String contentLabel) {
        List<T> overrides = currentSession()
            .createCriteria(this.entityType())
            .add(Restrictions.eq(parentAttr, parent))
            .add(Restrictions.eq("contentLabel", contentLabel))
            .list();

        for (T cco : overrides) {
            delete(cco);
        }
    }

    public void removeByParent(Parent parent) {
        List<T> overrides = currentSession()
            .createCriteria(this.entityType())
            .add(Restrictions.eq(parentAttr, parent))
            .list();

        for (T cco : overrides) {
            delete(cco);
        }
    }

    public T retrieve(Parent parent, String contentLabel, String name) {
        if (parent != null && contentLabel != null && name != null) {
            return (T) currentSession()
                .createCriteria(this.entityType())
                .add(Restrictions.eq(parentAttr, parent))
                .add(Restrictions.eq("contentLabel", contentLabel))
                .add(Restrictions.eq("name", name.toLowerCase()))
                .setMaxResults(1)
                .uniqueResult();
        }

        return null;
    }

    @Transactional
    public T addOrUpdate(Parent parent, ContentOverride override) {
        if (parent == null) {
            throw new IllegalArgumentException("parent is null");
        }

        if (override == null) {
            throw new IllegalArgumentException("override is null");
        }

        T current = this.retrieve(parent, override.getContentLabel(), override.getName());

        if (current != null) {
            current.setValue(override.getValue());

            current = this.merge(current);
        }
        else {
            current = this.createOverride();

            current.setParent(parent);
            current.setContentLabel(override.getContentLabel());
            current.setName(override.getName());
            current.setValue(override.getValue());

            current = this.create(current);
        }

        return current;
    }

    /**
     * Creates an empty/default override, to be completed by the caller.
     *
     * @return
     *  An empty/default ContentOverride instance
     */
    protected abstract T createOverride();

}
