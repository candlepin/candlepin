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

import org.candlepin.util.ElementTransformer;

import com.google.inject.persist.Transactional;

import org.hibernate.CacheMode;
import org.hibernate.Criteria;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.Session;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projection;
import org.hibernate.criterion.Projections;

// Potentially temporary entries. Sort these into the above blob if we're keeping them.
import org.hibernate.internal.CriteriaImpl;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.loader.criteria.CriteriaQueryTranslator;
import org.hibernate.type.Type;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;



/**
 * The DetachedCandlepinQuery class represents a detached criteria and provides fluent-style methodsfor
 *  configuring how the criteria is to be executed and how the result should be processed.
 *
 * @param <T>
 *  The entity type to be returned by this criteria's result output methods
 */
public class DetachedCandlepinQuery<T> implements CandlepinQuery<T> {

    protected Session session;
    protected DetachedCriteria criteria;

    protected int offset;
    protected int limit;

    // TODO:
    // Add support for stateless sessions (which requires some workarounds because stateless sessions
    // and sessions don't have a common parent class)

    /**
     * Creates a new DetachedCandlepinQuery instance using the specified criteria and session.
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
    public DetachedCandlepinQuery(Session session, DetachedCriteria criteria) {
        if (session == null) {
            throw new IllegalArgumentException("session is null");
        }

        if (criteria == null) {
            throw new IllegalArgumentException("criteria is null");
        }

        this.session = session;
        this.criteria = criteria;

        this.offset = -1;
        this.limit = -1;
    }

    /**
     * Retreives an executable criteria and configures it to be ready to run the criteria with the
     * configuration set by this criteria instance.
     *
     * @return
     *  a fully configured, executable criteria
     */
    protected Criteria getExecutableCriteria() {
        // Impl/sadness note:
        // As of Hibernate 5.0, this does not actually result in a new criteria instance -- it just
        // returns its internal CriteriaImpl instance. Changes we make will be reflected between
        // calls.
        CriteriaImpl executable = (CriteriaImpl) this.criteria.getExecutableCriteria(this.session);

        // These values can be safely passed through and are checked by Hibernate before actually
        // applying them.
        executable.setCacheMode(null);

        // WARNING:
        // These values cannot be reset without using reflection. Once they're set, they can only
        // be changed. We'll try to use reasonable pseudo-defaults, but only if we need to.
        if (this.offset > -1) {
            executable.setFirstResult(this.offset);
        }
        else if (executable.getFirstResult() != null) {
            executable.setFirstResult(0);
        }

        if (this.limit > -1) {
            executable.setMaxResults(this.limit);
        }
        else if (executable.getMaxResults() != null) {
            executable.setMaxResults(Integer.MAX_VALUE);
        }

        // TODO: Add read-only when we have a requirement to do so.

        return executable;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CandlepinQuery<T> useSession(Session session) {
        if (session == null) {
            throw new IllegalArgumentException("session is null");
        }

        this.session = session;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CandlepinQuery<T> setFirstResult(int offset) {
        this.offset = offset;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CandlepinQuery<T> setMaxResults(int limit) {
        this.limit = limit;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CandlepinQuery<T> addOrder(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("order is null");
        }

        this.criteria.addOrder(order);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <O> CandlepinQuery<O> transform(ElementTransformer<T, O> transformer) {
        return new TransformedCandlepinQuery(this, transformer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<T> list() {
        Criteria executable = this.getExecutableCriteria();
        List<T> list = (List<T>) executable.list();

        return list != null ? list : Collections.<T>emptyList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int forEach(ResultProcessor<T> processor) {
        return this.forEach(0, false, processor);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int forEach(int column, ResultProcessor<T> processor) {
        return this.forEach(column, false, processor);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public int forEach(int column, boolean evict, ResultProcessor<T> processor) {
        if (processor == null) {
            throw new IllegalArgumentException("processor is null");
        }

        Criteria executable = this.getExecutableCriteria();

        // We always override the cache mode here to ensure we don't evict things that may be in
        // cache from another request.
        if (evict) {
            executable.setCacheMode(CacheMode.GET);
        }

        ScrollableResults cursor = executable.scroll(ScrollMode.FORWARD_ONLY);
        int count = 0;

        try {
            boolean cont = true;

            if (evict) {
                while (cont && cursor.next()) {
                    T result = (T) cursor.get(column);

                    cont = processor.process(result);
                    this.session.evict(result);

                    ++count;
                }
            }
            else {
                while (cont && cursor.next()) {
                    cont = processor.process((T) cursor.get(column));
                    ++count;
                }
            }
        }
        finally {
            cursor.close();
        }

        return count;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public int forEachRow(ResultProcessor<Object[]> processor) {
        if (processor == null) {
            throw new IllegalArgumentException("processor is null");
        }

        Criteria executable = this.getExecutableCriteria();
        ScrollableResults cursor = executable.scroll(ScrollMode.FORWARD_ONLY);
        int count = 0;

        try {
            boolean cont = true;

            while (cont && cursor.next()) {
                cont = processor.process(cursor.get());
                ++count;
            }
        }
        finally {
            cursor.close();
        }

        return count;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResultIterator<T> iterate() {
        return this.iterate(0, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResultIterator<T> iterator() {
        return this.iterate(0, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResultIterator<T> iterate(int column) {
        return this.iterate(column, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResultIterator<T> iterate(int column, boolean evict) {
        Criteria executable = this.getExecutableCriteria();

        // We always override the cache mode here to ensure we don't evict things that may be in
        // cache from another request.
        if (evict) {
            executable.setCacheMode(CacheMode.GET);
        }

        ScrollableResults cursor = executable.scroll(ScrollMode.FORWARD_ONLY);
        return new ColumnarResultIterator<T>(this.session, cursor, column, evict);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResultIterator<Object[]> iterateByRow() {
        Criteria executable = this.getExecutableCriteria();

        ScrollableResults cursor = executable.scroll(ScrollMode.FORWARD_ONLY);
        return new RowResultIterator(cursor);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public T uniqueResult() {
        Criteria executable = this.getExecutableCriteria();
        return (T) executable.uniqueResult();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings({"unchecked", "checkstyle:indentation"})
    public int getRowCount() {
        CriteriaImpl executable = (CriteriaImpl) this.getExecutableCriteria();
        Projection projection = executable.getProjection();
        List<Order> ordering = new LinkedList<Order>();

        // Impl note:
        // We're using the projection method here over using a cursor to scroll the results due to
        // limitations on various connectors' cursor implementations. Some don't properly support
        // fast-forwarding/jumping (Oracle) and others fake the whole thing by running the query
        // and pretending to scroll (MySQL). Until these are addressed, the hack below is going to
        // be far more performant and significantly safer (which makes me sad).

        // Remove any ordering that may be applied (since we almost certainly won't have the field
        // available anymore)
        Iterator iterator = executable.iterateOrderings();
        while (iterator.hasNext()) {
            ordering.add(((CriteriaImpl.OrderEntry) iterator.next()).getOrder());
            iterator.remove();
        }

        if (projection != null && projection.isGrouped()) {
            // We have a projection that alters the grouping of the query. We need to rebuild the
            // projection such that it gets our row count and properly applies the group by
            // statement.
            // The logic for this block is largely derived from this Stack Overflow posting:
            // http://stackoverflow.com/
            //     questions/32498229/hibernate-row-count-on-criteria-with-already-set-projection
            //
            // A safer alternative may be to generate a query that uses the given criteria as a
            // subquery (SELECT count(*) FROM (<criteria SQL>)), but is probably less performant
            // than this hack.
            CriteriaQueryTranslator translator = new CriteriaQueryTranslator(
                (SessionFactoryImplementor) this.session.getSessionFactory(),
                executable,
                executable.getEntityOrClassName(),
                CriteriaQueryTranslator.ROOT_SQL_ALIAS
            );

            executable.setProjection(Projections.projectionList()
                .add(Projections.rowCount())
                .add(Projections.sqlGroupProjection(
                    "count(count(1))",
                    translator.getGroupBy(),
                    new String[] {},
                    new Type[] {}
                )));
        }
        else {
            executable.setProjection(Projections.rowCount());
        }

        Long count = (Long) executable.uniqueResult();

        // Restore projection and ordering...
        executable.setProjection(projection);

        for (Order order : ordering) {
            executable.addOrder(order);
        }

        return count != null ? count.intValue() : 0;
    }
}
