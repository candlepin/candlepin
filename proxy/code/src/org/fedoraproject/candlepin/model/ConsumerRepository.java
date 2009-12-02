package org.fedoraproject.candlepin.model;

import org.hibernate.Session;
import org.hibernate.criterion.Restrictions;

public class ConsumerRepository extends AbstractHibernateRepository<Consumer> {
    
    public ConsumerRepository(Session session) {
        super(session);
    }

    public Consumer lookupByName(String name) {
        return (Consumer) session.createCriteria(Consumer.class)
            .add(Restrictions.like("name", name))
            .uniqueResult();
    }
}
