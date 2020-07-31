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

import org.hibernate.Session;
import org.hibernate.criterion.DetachedCriteria;
import org.springframework.stereotype.Component;



/**
 * The CandlepinQueryFactory is responsible for building CandlepinQuery instances based on the
 * given input.
 */
@Component
public class CandlepinQueryFactory {

    /**
     * Builds an empty CandlepinQuery
     *
     * @return
     *  an empty CandlepinQuery
     */
    public <T> CandlepinQuery<T> buildQuery() {
        return new EmptyCandlepinQuery<>();
    }

    /**
     * Builds a CandlepinQuery using the specified session and detached criteria.
     *
     * @param session
     *  The session to use to build the CandlepinQuery
     *
     * @param criteria
     *  The session to use to build the CandlepinQuery
     *
     * @return
     *  a CandlepinQuery built from the given session and criteria
     */
    public <T> CandlepinQuery<T> buildQuery(Session session, DetachedCriteria criteria) {
        return new DetachedCandlepinQuery<>(session, criteria);
    }

    // Add more implementations here as necessary

}
