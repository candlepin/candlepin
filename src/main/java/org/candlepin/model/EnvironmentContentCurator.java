/**
 * Copyright (c) 2009 Red Hat, Inc.
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

/**
 * EnvironmentContentCurator
 */
public class EnvironmentContentCurator extends
        AbstractHibernateCurator<EnvironmentContent> {

    protected EnvironmentContentCurator() {
        super(EnvironmentContent.class);
    }

    public EnvironmentContent lookupByEnvironmentAndContent(
        Environment e, String contentId) {

        return (EnvironmentContent) this.currentSession().createCriteria(
            EnvironmentContent.class).add(Restrictions.eq("environment", e))
            .add(Restrictions.eq("contentId", contentId)).uniqueResult();
    }

    public List<EnvironmentContent> lookupByAndContent(
        String contentId) {

        return this.currentSession().createCriteria(
            EnvironmentContent.class)
            .add(Restrictions.eq("contentId", contentId)).list();
    }

}
