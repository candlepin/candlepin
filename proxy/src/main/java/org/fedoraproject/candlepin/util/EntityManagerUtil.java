package org.fedoraproject.candlepin.util;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

public class EntityManagerUtil {
    
    public static final String DEFAULT_PERSISTENCE_UNIT = "production";
    public static final String PERSISTENCE_UNIT_PROPERTY = "persistenceUnit";
    public static final EntityManagerFactory emf = buildEntityManagerFactory();
    
    private static EntityManagerFactory buildEntityManagerFactory() {
        // Allow test environments to override the persistence unit to load 
        // with a system property: 
        String persistenceUnit = System.getProperty(PERSISTENCE_UNIT_PROPERTY, 
                DEFAULT_PERSISTENCE_UNIT);
        System.out.println("Loading persistence unit: " + persistenceUnit);
        EntityManagerFactory emf = 
            Persistence.createEntityManagerFactory(persistenceUnit);
        return emf;
    }
    
    public static EntityManager createEntityManager() {
        return emf.createEntityManager();
    }

}
