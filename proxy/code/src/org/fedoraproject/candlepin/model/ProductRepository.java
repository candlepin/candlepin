package org.fedoraproject.candlepin.model;

import javax.persistence.EntityManager;

import org.hibernate.criterion.Restrictions;

public class ProductRepository extends AbstractHibernateRepository<Product> {
    
    public ProductRepository(EntityManager em) {
        super(Product.class);
    }

    public Product lookupByName(String name) {
        return (Product) currentSession().createCriteria(Product.class)
            .add(Restrictions.like("name", name))
            .uniqueResult();
    }
    
    public Product lookupByLabel(String label) {
        return (Product) currentSession().createCriteria(Product.class)
            .add(Restrictions.like("label", label))
            .uniqueResult();
    }
}
