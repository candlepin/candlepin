package org.fedoraproject.candlepin.model;

import javax.persistence.EntityManager;

import org.hibernate.criterion.Restrictions;

public class ConsumerRepository extends AbstractHibernateRepository<Consumer> {
    
    public ConsumerRepository(EntityManager em) {
        super(em);
    }

    public Consumer lookupByName(String name) {
        return (Consumer) currentSession().createCriteria(Consumer.class)
            .add(Restrictions.like("name", name))
            .uniqueResult();
    }
}
