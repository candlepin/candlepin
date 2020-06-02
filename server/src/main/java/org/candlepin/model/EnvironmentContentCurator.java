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

import org.hibernate.criterion.Restrictions;

import java.util.List;

import javax.inject.Singleton;



/**
 * EnvironmentContentCurator
 */
@Singleton
public class EnvironmentContentCurator extends AbstractHibernateCurator<EnvironmentContent> {

    public EnvironmentContentCurator() {
        super(EnvironmentContent.class);
    }

    public EnvironmentContent getByEnvironmentAndContent(Environment e, String contentId) {
        return (EnvironmentContent) this.currentSession().createCriteria(EnvironmentContent.class)
            .createAlias("content", "content")
            .add(Restrictions.eq("environment", e))
            .add(Restrictions.eq("content.id", contentId))
            .uniqueResult();
    }

    public EnvironmentContent getByEnvironmentAndContent(Environment e, Content content) {
        return (EnvironmentContent) this.currentSession().createCriteria(EnvironmentContent.class)
            .add(Restrictions.eq("environment", e))
            .add(Restrictions.eq("content", content))
            .uniqueResult();
    }

    public List<EnvironmentContent> getByContent(Owner owner, String contentId) {
        return this.currentSession().createCriteria(EnvironmentContent.class)
            .createAlias("environment", "environment")
            .createAlias("content", "content")
            .add(Restrictions.eq("environment.owner", owner))
            .add(Restrictions.eq("content.id", contentId))
            .list();
    }

}
