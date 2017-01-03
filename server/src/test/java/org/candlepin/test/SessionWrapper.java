/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
package org.candlepin.test;

import org.hibernate.CacheMode;
import org.hibernate.Criteria;
import org.hibernate.FlushMode;
import org.hibernate.Filter;
import org.hibernate.HibernateException;
import org.hibernate.LobHelper;
import org.hibernate.LockMode;
import org.hibernate.LockOptions;
import org.hibernate.MultiIdentifierLoadAccess;
import org.hibernate.IdentifierLoadAccess;
import org.hibernate.Interceptor;
import org.hibernate.NaturalIdLoadAccess;
import org.hibernate.Query;
import org.hibernate.ReplicationMode;
import org.hibernate.ScrollableResults;
import org.hibernate.ScrollMode;
import org.hibernate.Session;
import org.hibernate.SessionEventListener;
import org.hibernate.SessionFactory;
import org.hibernate.SharedSessionBuilder;
import org.hibernate.SimpleNaturalIdLoadAccess;
import org.hibernate.SQLQuery;
import org.hibernate.Transaction;
import org.hibernate.TypeHelper;
import org.hibernate.UnknownProfileException;
import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.loader.custom.CustomQuery;
import org.hibernate.engine.jdbc.LobCreationContext;
import org.hibernate.engine.jdbc.connections.spi.JdbcConnectionAccess;
import org.hibernate.engine.jdbc.spi.JdbcCoordinator;
import org.hibernate.engine.query.spi.sql.NativeSQLQuerySpecification;
import org.hibernate.engine.spi.EntityKey;
import org.hibernate.engine.spi.LoadQueryInfluencers;
import org.hibernate.engine.spi.NamedQueryDefinition;
import org.hibernate.engine.spi.NamedSQLQueryDefinition;
import org.hibernate.engine.spi.PersistenceContext;
import org.hibernate.engine.spi.QueryParameters;
import org.hibernate.engine.spi.SessionEventListenerManager;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.jdbc.ReturningWork;
import org.hibernate.jdbc.Work;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.procedure.ProcedureCall;
import org.hibernate.resource.transaction.TransactionCoordinator;
import org.hibernate.stat.SessionStatistics;
import org.hibernate.type.descriptor.WrapperOptions;

import java.io.Serializable;
import java.sql.Connection;
import java.util.Iterator;
import java.util.List;



/**
 * The SessionWrapper is a utility class intended to be used in places where we need to spy on an
 * existing session instance, but are unable to due to the "final" nature of Hibernate's
 * SessionImpl class.
 */
public class SessionWrapper implements Session, SessionImplementor {

    protected final Session session;
    protected final SessionImplementor sessionImpl;

    public SessionWrapper(Session session) {
        if (session == null) {
            throw new IllegalArgumentException("session is null");
        }

        this.session = session;
        this.sessionImpl = (SessionImplementor) session;
    }

    // Session methods

    /**
     * {@inheritDoc}
     */
    @Override
    public SharedSessionBuilder sessionWithOptions() {
        return this.session.sessionWithOptions();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void flush() throws HibernateException {
        this.session.flush();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setFlushMode(FlushMode flushMode) {
        this.session.setFlushMode(flushMode);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FlushMode getFlushMode() {
        return this.session.getFlushMode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCacheMode(CacheMode cacheMode) {
        this.session.setCacheMode(cacheMode);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CacheMode getCacheMode() {
        return this.session.getCacheMode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SessionFactory getSessionFactory() {
        return this.session.getSessionFactory();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws HibernateException {
        this.session.close();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cancelQuery() throws HibernateException {
        this.session.cancelQuery();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isOpen() {
        return this.session.isOpen();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isConnected() {
        return this.session.isConnected();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDirty() throws HibernateException {
        return this.session.isDirty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDefaultReadOnly() {
        return this.session.isDefaultReadOnly();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDefaultReadOnly(boolean readOnly) {
        this.session.setDefaultReadOnly(readOnly);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Serializable getIdentifier(Object object) {
        return this.session.getIdentifier(object);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean contains(Object object) {
        return this.session.contains(object);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void evict(Object object) {
        this.session.evict(object);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T load(Class<T> theClass, Serializable id, LockMode lockMode) {
        return this.session.load(theClass, id, lockMode);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T load(Class<T> theClass, Serializable id, LockOptions lockOptions) {
        return this.session.load(theClass, id, lockOptions);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object load(String entityName, Serializable id, LockMode lockMode) {
        return this.session.load(entityName, id, lockMode);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object load(String entityName, Serializable id, LockOptions lockOptions) {
        return this.session.load(entityName, id, lockOptions);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T load(Class<T> theClass, Serializable id) {
        return this.session.load(theClass, id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object load(String entityName, Serializable id) {
        return this.session.load(entityName, id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void load(Object object, Serializable id) {
        this.session.load(object, id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void replicate(Object object, ReplicationMode replicationMode) {
        this.session.replicate(object, replicationMode);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void replicate(String entityName, Object object, ReplicationMode replicationMode)  {
        this.session.replicate(entityName, object, replicationMode);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Serializable save(Object object) {
        return this.session.save(object);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Serializable save(String entityName, Object object) {
        return this.session.save(entityName, object);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void saveOrUpdate(Object object) {
        this.session.saveOrUpdate(object);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void saveOrUpdate(String entityName, Object object) {
        this.session.saveOrUpdate(entityName, object);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void update(Object object) {
        this.session.update(object);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void update(String entityName, Object object) {
        this.session.update(entityName, object);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object merge(Object object) {
        return this.session.merge(object);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object merge(String entityName, Object object) {
        return this.session.merge(entityName, object);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void persist(Object object) {
        this.session.persist(object);
    }
    /**
     * {@inheritDoc}
     */
    @Override
    public void persist(String entityName, Object object) {
        this.session.persist(entityName, object);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(Object object) {
        this.session.delete(object);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(String entityName, Object object) {
        this.session.delete(entityName, object);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void lock(Object object, LockMode lockMode) {
        this.session.lock(object, lockMode);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void lock(String entityName, Object object, LockMode lockMode) {
        this.session.lock(entityName, object, lockMode);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LockRequest buildLockRequest(LockOptions lockOptions) {
        return this.session.buildLockRequest(lockOptions);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void refresh(Object object) {
        this.session.refresh(object);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void refresh(String entityName, Object object) {
        this.session.refresh(entityName, object);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void refresh(Object object, LockMode lockMode) {
        this.session.refresh(object, lockMode);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void refresh(Object object, LockOptions lockOptions) {
        this.session.refresh(object, lockOptions);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void refresh(String entityName, Object object, LockOptions lockOptions) {
        this.session.refresh(entityName, object, lockOptions);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LockMode getCurrentLockMode(Object object) {
        return this.session.getCurrentLockMode(object);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Query createFilter(Object collection, String queryString) {
        return this.session.createFilter(collection, queryString);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        this.session.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T get(Class<T> entityType, Serializable id) {
        return this.session.get(entityType, id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T get(Class<T> entityType, Serializable id, LockMode lockMode) {
        return this.session.get(entityType, id, lockMode);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T get(Class<T> entityType, Serializable id, LockOptions lockOptions) {
        return this.session.get(entityType, id, lockOptions);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object get(String entityName, Serializable id) {
        return this.session.get(entityName, id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object get(String entityName, Serializable id, LockMode lockMode) {
        return this.session.get(entityName, id, lockMode);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object get(String entityName, Serializable id, LockOptions lockOptions) {
        return this.session.get(entityName, id, lockOptions);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getEntityName(Object object) {
        return this.session.getEntityName(object);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IdentifierLoadAccess byId(String entityName) {
        return this.session.byId(entityName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> IdentifierLoadAccess<T> byId(Class<T> entityClass) {
        return this.session.byId(entityClass);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MultiIdentifierLoadAccess byMultipleIds(String entityName) {
        return this.session.byMultipleIds(entityName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> MultiIdentifierLoadAccess<T> byMultipleIds(Class<T> entityClass) {
        return this.session.byMultipleIds(entityClass);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NaturalIdLoadAccess byNaturalId(String entityName) {
        return this.session.byNaturalId(entityName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> NaturalIdLoadAccess<T> byNaturalId(Class<T> entityClass) {
        return this.session.byNaturalId(entityClass);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SimpleNaturalIdLoadAccess bySimpleNaturalId(String entityName) {
        return this.session.bySimpleNaturalId(entityName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> SimpleNaturalIdLoadAccess<T> bySimpleNaturalId(Class<T> entityClass) {
        return this.session.bySimpleNaturalId(entityClass);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Filter enableFilter(String filterName) {
        return this.session.enableFilter(filterName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Filter getEnabledFilter(String filterName) {
        return this.session.getEnabledFilter(filterName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void disableFilter(String filterName) {
        this.session.disableFilter(filterName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SessionStatistics getStatistics() {
        return this.session.getStatistics();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isReadOnly(Object entityOrProxy) {
        return this.session.isReadOnly(entityOrProxy);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setReadOnly(Object entityOrProxy, boolean readOnly) {
        this.session.setReadOnly(entityOrProxy, readOnly);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void doWork(Work work) throws HibernateException {
        this.session.doWork(work);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T doReturningWork(ReturningWork<T> work) throws HibernateException {
        return this.session.doReturningWork(work);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Connection disconnect() {
        return this.session.disconnect();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reconnect(Connection connection) {
        this.session.reconnect(connection);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isFetchProfileEnabled(String name) throws UnknownProfileException {
        return this.session.isFetchProfileEnabled(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void enableFetchProfile(String name) throws UnknownProfileException {
        this.session.enableFetchProfile(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void disableFetchProfile(String name) throws UnknownProfileException {
        this.session.disableFetchProfile(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TypeHelper getTypeHelper() {
        return this.session.getTypeHelper();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LobHelper getLobHelper() {
        return this.session.getLobHelper();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addEventListeners(SessionEventListener... listeners) {
        this.session.addEventListeners(listeners);
    }

    // SharedSessionContract methods

    /**
     * {@inheritDoc}
     */
    @Override
    public Transaction beginTransaction() {
        return this.session.beginTransaction();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Criteria createCriteria(Class persistentClass) {
        return this.session.createCriteria(persistentClass);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Criteria createCriteria(Class persistentClass, String alias) {
        return this.session.createCriteria(persistentClass, alias);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Criteria createCriteria(String entityName) {
        return this.session.createCriteria(entityName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Criteria createCriteria(String entityName, String alias) {
        return this.session.createCriteria(entityName, alias);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Query createQuery(String queryString) {
        return this.session.createQuery(queryString);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SQLQuery createSQLQuery(String queryString) {
        return this.session.createSQLQuery(queryString);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProcedureCall createStoredProcedureCall(String procedureName) {
        return this.session.createStoredProcedureCall(procedureName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProcedureCall createStoredProcedureCall(String procedureName, Class... resultClasses) {
        return this.session.createStoredProcedureCall(procedureName, resultClasses);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProcedureCall createStoredProcedureCall(String procedureName, String... resultSetMappings) {
        return this.session.createStoredProcedureCall(procedureName, resultSetMappings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProcedureCall getNamedProcedureCall(String name) {
        return this.session.getNamedProcedureCall(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Query getNamedQuery(String queryName) {
        return this.session.getNamedQuery(queryName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getTenantIdentifier() {
        return this.session.getTenantIdentifier();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Transaction getTransaction() {
        return this.session.getTransaction();
    }

    // SessionImplementor methods

    /**
     * {@inheritDoc}
     */
    @Override
    public JdbcConnectionAccess getJdbcConnectionAccess() {
        return this.sessionImpl.getJdbcConnectionAccess();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntityKey generateEntityKey(Serializable id, EntityPersister persister) {
        return this.sessionImpl.generateEntityKey(id, persister);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Interceptor getInterceptor() {
        return this.sessionImpl.getInterceptor();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setAutoClear(boolean enabled) {
        this.sessionImpl.setAutoClear(enabled);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void disableTransactionAutoJoin() {
        this.sessionImpl.disableTransactionAutoJoin();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isTransactionInProgress() {
        return this.sessionImpl.isTransactionInProgress();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initializeCollection(PersistentCollection collection, boolean writing)
        throws HibernateException {
        this.sessionImpl.initializeCollection(collection, writing);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object internalLoad(String entityName, Serializable id, boolean eager, boolean nullable)
        throws HibernateException {
        return this.sessionImpl.internalLoad(entityName, id, eager, nullable);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object immediateLoad(String entityName, Serializable id) throws HibernateException {
        return this.sessionImpl.immediateLoad(entityName, id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getTimestamp() {
        return this.sessionImpl.getTimestamp();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SessionFactoryImplementor getFactory() {
        return this.sessionImpl.getFactory();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List list(String query, QueryParameters queryParameters) throws HibernateException {
        return this.sessionImpl.list(query, queryParameters);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterator iterate(String query, QueryParameters queryParameters) throws HibernateException {
        return this.sessionImpl.iterate(query, queryParameters);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ScrollableResults scroll(String query, QueryParameters queryParameters) throws HibernateException {
        return this.sessionImpl.scroll(query, queryParameters);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ScrollableResults scroll(Criteria criteria, ScrollMode scrollMode) {
        return this.sessionImpl.scroll(criteria, scrollMode);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List list(Criteria criteria) {
        return this.sessionImpl.list(criteria);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List listFilter(Object collection, String filter, QueryParameters queryParameters)
        throws HibernateException {
        return this.sessionImpl.listFilter(collection, filter, queryParameters);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterator iterateFilter(Object collection, String filter, QueryParameters queryParameters)
        throws HibernateException {
        return this.sessionImpl.iterateFilter(collection, filter, queryParameters);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntityPersister getEntityPersister(String entityName, Object object) throws HibernateException {
        return this.sessionImpl.getEntityPersister(entityName, object);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getEntityUsingInterceptor(EntityKey key) throws HibernateException {
        return this.sessionImpl.getEntityUsingInterceptor(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Serializable getContextEntityIdentifier(Object object) {
        return this.sessionImpl.getContextEntityIdentifier(object);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String bestGuessEntityName(Object object) {
        return this.sessionImpl.bestGuessEntityName(object);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Connection connection() {
        return this.sessionImpl.connection();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String guessEntityName(Object entity) throws HibernateException {
        return this.sessionImpl.guessEntityName(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object instantiate(String entityName, Serializable id) throws HibernateException {
        return this.sessionImpl.instantiate(entityName, id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List listCustomQuery(CustomQuery customQuery, QueryParameters queryParameters)
        throws HibernateException {
        return this.sessionImpl.listCustomQuery(customQuery, queryParameters);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ScrollableResults scrollCustomQuery(CustomQuery customQuery, QueryParameters queryParameters)
        throws HibernateException {
        return this.sessionImpl.scrollCustomQuery(customQuery, queryParameters);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List list(NativeSQLQuerySpecification spec, QueryParameters queryParameters)
        throws HibernateException {
        return this.sessionImpl.list(spec, queryParameters);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ScrollableResults scroll(NativeSQLQuerySpecification spec, QueryParameters queryParameters) {
        return this.sessionImpl.scroll(spec, queryParameters);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getDontFlushFromFind() {
        return this.sessionImpl.getDontFlushFromFind();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PersistenceContext getPersistenceContext() {
        return this.sessionImpl.getPersistenceContext();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int executeUpdate(String query, QueryParameters queryParameters) throws HibernateException {
        return this.sessionImpl.executeUpdate(query, queryParameters);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int executeNativeUpdate(NativeSQLQuerySpecification specification, QueryParameters queryParameters)
        throws HibernateException {
        return this.sessionImpl.executeNativeUpdate(specification, queryParameters);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Query getNamedSQLQuery(String name) {
        return this.sessionImpl.getNamedSQLQuery(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEventSource() {
        return this.sessionImpl.isEventSource();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterScrollOperation() {
        this.sessionImpl.afterScrollOperation();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TransactionCoordinator getTransactionCoordinator() {
        return this.sessionImpl.getTransactionCoordinator();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JdbcCoordinator getJdbcCoordinator() {
        return this.sessionImpl.getJdbcCoordinator();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isClosed() {
        return this.sessionImpl.isClosed();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldAutoClose() {
        return this.sessionImpl.shouldAutoClose();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAutoCloseSessionEnabled() {
        return this.sessionImpl.isAutoCloseSessionEnabled();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LoadQueryInfluencers getLoadQueryInfluencers() {
        return this.sessionImpl.getLoadQueryInfluencers();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Query createQuery(NamedQueryDefinition namedQueryDefinition) {
        return this.sessionImpl.createQuery(namedQueryDefinition);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SQLQuery createSQLQuery(NamedSQLQueryDefinition namedQueryDefinition) {
        return this.sessionImpl.createSQLQuery(namedQueryDefinition);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SessionEventListenerManager getEventListenerManager() {
        return this.sessionImpl.getEventListenerManager();
    }

    // LobCreationContext methods

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T execute(LobCreationContext.Callback<T> callback) {
        return this.sessionImpl.execute(callback);
    }

    // WrapperOptionsContext methods

    /**
     * {@inheritDoc}
     */
    @Override
    public WrapperOptions getWrapperOptions() {
        return this.sessionImpl.getWrapperOptions();
    }
}
