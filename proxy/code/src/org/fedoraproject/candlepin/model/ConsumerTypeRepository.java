package org.fedoraproject.candlepin.model;



public class ConsumerTypeRepository extends AbstractHibernateRepository<ConsumerType> {
    
    protected ConsumerTypeRepository() {
        super(ConsumerType.class);
    }

}
