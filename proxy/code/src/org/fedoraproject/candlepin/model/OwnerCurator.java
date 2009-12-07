package org.fedoraproject.candlepin.model;

import org.hibernate.criterion.Restrictions;

public class OwnerCurator extends AbstractHibernateRepository<Owner> {
    
    protected OwnerCurator() {
        super(Owner.class);
    }

    public Owner lookupByName(String name) {
        return (Owner) currentSession().createCriteria(Owner.class)
        .add(Restrictions.like("name", name))
        .uniqueResult();
    }
}
