/**
 * Copyright (c) 2009 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.fedoraproject.candlepin.util;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

public class EntityManagerUtil {
    
    public static final String DEFAULT_PERSISTENCE_UNIT = "production";
    public static final String PERSISTENCE_UNIT_PROPERTY = "persistenceUnit";
    public static final EntityManagerFactory EMF = buildEntityManagerFactory();

    private EntityManagerUtil() {
        // do nothing
    }
    
    private static EntityManagerFactory buildEntityManagerFactory() {
        // Allow test environments to override the persistence unit to load 
        // with a system property: 
        String persistenceUnit = System.getProperty(PERSISTENCE_UNIT_PROPERTY,
                DEFAULT_PERSISTENCE_UNIT);
        System.out.println("Loading persistence unit: " + persistenceUnit);
        return Persistence.createEntityManagerFactory(persistenceUnit);
    }

    public static EntityManager createEntityManager() {
        return EMF.createEntityManager();
    }
}
