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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


/**
 *
 * UserCurator
 */
@Component
@Transactional
public class UserCurator extends AbstractHibernateCurator<User> {

    public UserCurator() {
        super(User.class);
    }

    public User findByLogin(String login) {
        return (User) createSecureCriteria()
            .add(Restrictions.eq("username", login))
            .uniqueResult();
    }

    /**
     * Fetch the number of users without loading them all.
     * @return number of user accounts in our database
     */
    public Long getUserCount() {
        return (Long) currentSession().createQuery("select count(*) from User").
            iterate().next();
    }

}
