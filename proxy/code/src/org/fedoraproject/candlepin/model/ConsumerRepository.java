package org.fedoraproject.candlepin.model;

import org.hibernate.criterion.Restrictions;

public class ConsumerRepository extends AbstractHibernateRepository<Consumer> {
    
    protected ConsumerRepository() {
        super(Consumer.class);
    }
    
    public Consumer lookupByName(String name) {
        return (Consumer) currentSession().createCriteria(Consumer.class)
        .add(Restrictions.like("name", name))
        .uniqueResult();
    }
    
}
