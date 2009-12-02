package org.fedoraproject.candlepin.model;

import org.hibernate.Session;

public class ConsumerTypeRepository extends AbstractHibernateRepository<ConsumerType> {
    public ConsumerTypeRepository(Session session) {
        super(session);
    }
}
