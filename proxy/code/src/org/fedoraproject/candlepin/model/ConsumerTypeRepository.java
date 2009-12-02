package org.fedoraproject.candlepin.model;

import javax.persistence.EntityManager;

public class ConsumerTypeRepository extends AbstractHibernateRepository<ConsumerType> {
    public ConsumerTypeRepository(EntityManager em) {
        super(em);
    }
}
