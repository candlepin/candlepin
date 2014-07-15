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

import java.util.List;

import org.hibernate.criterion.Restrictions;

import com.google.inject.persist.Transactional;

/**
 * ContentOverrideCurator
 *
 * @param <T> ContentOverride type
 * @param <Parent> parent of the content override, Consumer or ActivationKey for example
 */
public abstract class ContentOverrideCurator
        <T extends ContentOverride, Parent extends AbstractHibernateObject>
        extends AbstractHibernateCurator<T> {

    private String parentAttr;

    /**
     * @param entityType
     */
    protected ContentOverrideCurator(Class<T> entityType, String parentAttrName) {
        super(entityType);
        this.parentAttr = parentAttrName;
    }

    @SuppressWarnings("unchecked")
    public List<T> getList(Parent parent) {
        return currentSession()
            .createCriteria(this.entityType())
            .add(Restrictions.eq(parentAttr, parent)).list();
    }

    public void removeByName(Parent parent, String contentLabel, String name) {
        List<T> overrides = currentSession()
            .createCriteria(this.entityType())
            .add(Restrictions.eq(parentAttr, parent))
            .add(Restrictions.eq("contentLabel", contentLabel))
            .add(Restrictions.eq("name", name).ignoreCase()).list();
        for (T cco : overrides) {
            delete(cco);
        }
    }

    public void removeByContentLabel(Parent parent, String contentLabel) {
        List<T> overrides = currentSession()
            .createCriteria(this.entityType())
            .add(Restrictions.eq(parentAttr, parent))
            .add(Restrictions.eq("contentLabel", contentLabel)).list();
        for (T cco : overrides) {
            delete(cco);
        }
    }

    public void removeByParent(Parent parent) {
        List<T> overrides = currentSession()
            .createCriteria(this.entityType())
            .add(Restrictions.eq(parentAttr, parent)).list();
        for (T cco : overrides) {
            delete(cco);
        }
    }

    public T retrieve(Parent parent, String contentLabel,
        String name) {
        return (T) currentSession()
            .createCriteria(this.entityType())
            .add(Restrictions.eq(parentAttr, parent))
            .add(Restrictions.eq("contentLabel", contentLabel))
            .add(Restrictions.eq("name", name).ignoreCase())
            .setMaxResults(1).uniqueResult();
    }

    /* (non-Javadoc)
     * @see org.candlepin.model.AbstractHibernateCurator#create(
     *      org.candlepin.model.Persisted)
     */
    @Override
    @Transactional
    public T create(T override) {
        sanitize(override);
        return super.create(override);
    }

    /* (non-Javadoc)
     * @see org.candlepin.model.AbstractHibernateCurator#merge(
     *     org.candlepin.model.Persisted)
     */
    @Override
    @Transactional
    public T merge(T override) {
        sanitize(override);
        return super.merge(override);
    }

    @Transactional
    public T addOrUpdate(Parent parent, ContentOverride override) {
        sanitize(override);
        T current = this.retrieve(parent,
            override.getContentLabel(), override.getName());
        if (current != null) {
            current.setValue(override.getValue());
            this.merge(current);
            return current;
        }
        return this.createWithParent(override, parent);
    }

    private void sanitize(ContentOverride override) {
        // Always make sure that the name is lowercase.
        if (override.getName() != null && !override.getName().isEmpty()) {
            override.setName(override.getName().toLowerCase());
        }
    }

    protected abstract T createWithParent(ContentOverride override, Parent parent);
}
