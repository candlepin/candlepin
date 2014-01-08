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
 * ConsumerContentOverrideCurator
 */
public class ConsumerContentOverrideCurator
    extends AbstractHibernateCurator<ConsumerContentOverride> {

    public ConsumerContentOverrideCurator() {
        super(ConsumerContentOverride.class);
    }

    @SuppressWarnings("unchecked")
    public List<ConsumerContentOverride> getList(Consumer consumer) {
        return currentSession()
            .createCriteria(ConsumerContentOverride.class)
            .add(Restrictions.eq("consumer", consumer)).list();
    }

    public void removeByName(Consumer consumer, String contentLabel, String name) {
        List<ConsumerContentOverride> overrides = currentSession()
            .createCriteria(ConsumerContentOverride.class)
            .add(Restrictions.eq("consumer", consumer))
            .add(Restrictions.eq("contentLabel", contentLabel))
            .add(Restrictions.eq("name", name).ignoreCase()).list();
        for (ConsumerContentOverride cco : overrides) {
            delete(cco);
        }
    }

    public void removeByContentLabel(Consumer consumer, String contentLabel) {
        List<ConsumerContentOverride> overrides = currentSession()
            .createCriteria(ConsumerContentOverride.class)
            .add(Restrictions.eq("consumer", consumer))
            .add(Restrictions.eq("contentLabel", contentLabel)).list();
        for (ConsumerContentOverride cco : overrides) {
            delete(cco);
        }
    }

    public void removeByConsumer(Consumer consumer) {
        List<ConsumerContentOverride> overrides = currentSession()
            .createCriteria(ConsumerContentOverride.class)
            .add(Restrictions.eq("consumer", consumer)).list();
        for (ConsumerContentOverride cco : overrides) {
            delete(cco);
        }
    }

    public ConsumerContentOverride retrieve(Consumer consumer, String contentLabel,
        String name) {
        return (ConsumerContentOverride) currentSession()
            .createCriteria(ConsumerContentOverride.class)
            .add(Restrictions.eq("consumer", consumer))
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
    public ConsumerContentOverride create(ConsumerContentOverride override) {
        sanitize(override);
        return super.create(override);
    }

    /* (non-Javadoc)
     * @see org.candlepin.model.AbstractHibernateCurator#merge(
     *     org.candlepin.model.Persisted)
     */
    @Override
    @Transactional
    public ConsumerContentOverride merge(ConsumerContentOverride override) {
        sanitize(override);
        return super.merge(override);
    }

    private void sanitize(ConsumerContentOverride override) {
        // Always make sure that the name is lowercase.
        if (override.getName() != null && !override.getName().isEmpty()) {
            override.setName(override.getName().toLowerCase());
        }
    }
}
