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
import org.hibernate.Criteria;
import org.hibernate.criterion.DetachedCriteria;

import java.util.List;



/**
 * The CandlepinCriteria class represents a criteria and provides fluent-style methods for
 * configuring how the criteria is to be executed and how the result should be processed.
 *
 * @param T
 *  The entity type to be returned by this criteria's result output methods
 */
public class CandlepinCriteria<T> {

    protected DetachedCriteria criteria;
    protected Session session;

    /**
     * Creates a new CandlepinCriteria instance using the specified criteria and session.
     *
     * @param criteria
     *  The detached criteria to execute
     *
     * @param session
     *  The session to use to execute the given criteria
     *
     * @throws IllegalArgumentException
     *  if either criteria or session are null
     */
    public CandlepinCriteria(DetachedCriteria criteria, Session session) {
        if (criteria == null) {
            throw new IllegalArgumentException("criteria is null");
        }

        if (session == null) {
            throw new IllegalArgumentException("session is null");
        }

        this.criteria = criteria;
        this.session = session;
    }

    /**
     * Sets the session to be used for executing this criteria
     *
     * @param session
     *  The session to use for executing this criteria
     *
     * @throws IllegalArgumentException
     *  if session is null
     *
     * @return
     *  this criteria instance
     */
    public CandlepinCriteria<T> useSession(Session session) {
        if (session == null) {
            throw new IllegalArgumentException("session is null");
        }

        this.session = session;
        return this;
    }

    /**
     * Retreives an executable criteria and configures it to be ready to run the criteria with the
     * configuration set by this criteria instance.
     *
     * @return
     *  a fully configured, executable criteria
     */
    protected Criteria getExecutableCriteria() {
        Criteria executable = this.criteria.getExecutableCriteria(this.session);

        // TODO:
        // Apply pending changes to the executable criteria:
        //  - read only
        //  - first/max results, order
        //  - fetch and cache mode

        return executable;
    }

    /**
     * Executes this criteria and returns the entities as a list. If no entities could be found,
     * this method returns an empty list.
     * <p></p>
     * <strong>Warning</strong>:
     * This method loads the entire result set into memory. As such, this method should not be used
     * with queries that can return extremely large data sets.
     *
     * @return
     *  a list containing the results of executing this criteria
     */
    @SuppressWarnings("unchecked")
    public List<T> list() {
        Criteria executable = this.getExecutableCriteria();
        return (List<T>) executable.list();
    }

    /**
     * Executes this criteria and returns a single, unique entity. If no entities could be found,
     * this method returns null. If more than one entity is found, a runtime exception will be
     * thrown.
     *
     * @return
     *  a single entity, or null if no entities were found
     */
    public T uniqueResult() {
        Criteria executable = this.getExecutableCriteria();
        return (T) executable.uniqueResult();
    }

    // TODO:
    // Add support for stateless sessions (which requires some workarounds because stateless sessions
    // and sessions don't have a common parent class)

    // TODO:
    // Add some other utility/passthrough methods as a need arises:
    //  - setReadOnly
    //  - setFirstResults
    //  - setMaxResults
    //  - setOrder
    //  - setFetchMode
    //  - setCacheMode/setCacheable

    // TODO:
    // Add the methods here from the cursor PR:
    //  - scroll
    //  - iterate

}
