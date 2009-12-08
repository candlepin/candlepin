package org.fedoraproject.candlepin.model;

import java.util.LinkedList;
import java.util.List;

import javax.persistence.EntityManager;

import org.hibernate.criterion.Restrictions;

public class ProductCurator extends AbstractHibernateCurator<Product> {

    public ProductCurator() {
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
    
    public List<Product> listAll() {
        List<Product> results = (List<Product>) currentSession()
            .createCriteria(Product.class).list();
        if (results == null) {
            return new LinkedList<Product>();
        }
        else {
            return results;
        }
    }

}
