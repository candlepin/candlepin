/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.hibernate;

import static org.junit.Assert.assertEquals;

import org.hibernate.ejb.Ejb3Configuration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Properties;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Id;

/**
 * EmptyStringInterceptorTest
 */
public class EmptyStringInterceptorTest {
    private EntityManagerFactory emf;
    private EntityManager em;
    private Ejb3Configuration cfg;
    private Properties props;

    @Entity
    static class Person {
        @Id
        private int id;

        @Column
        private String name;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
        props = new Properties();
        props.put("javax.persistence.provider", "org.hibernate.ejb.HibernatePersistence");
        props.put("javax.persistence.transactionType", "RESOURCE_LOCAL");
        props.put("hibernate.dialect", "org.hibernate.dialect.HSQLDialect");
        props.put("hibernate.connection.driver_class", "org.hsqldb.jdbcDriver");
        props.put("hibernate.connection.url",
            "jdbc:hsqldb:mem:unit-testing-jpa;sql.enforce_strict_size=true");
        props.put("hibernate.hbm2ddl.auto", "create-drop");
        props.put("hibernate.connection.username", "sa");
        props.put("hibernate.connection.password", "");
        props.put("hibernate.show_sql", "false");

        cfg = new Ejb3Configuration();
        cfg.addAnnotatedClass(Person.class);
    }

    @After
    public void tearDown() {
        em.close();
        emf.close();
    }

    @Test
    public void testNormalEmptyStringPersistence() {
        cfg.addProperties(props);
        emf = cfg.buildEntityManagerFactory();
        em = emf.createEntityManager();

        Person p = new Person();
        p.setName("");
        p.setId(1);

        persist(p);

        p = em.find(Person.class, 1);
        assertEquals("", p.getName());
    }

    @Test
    public void testInterceptedEmptyStringPersistence() {
        props.put("hibernate.ejb.interceptor",
            "org.candlepin.hibernate.EmptyStringInterceptor");

        cfg.addProperties(props);
        emf = cfg.buildEntityManagerFactory();
        em = emf.createEntityManager();

        Person p = new Person();
        p.setName("");
        p.setId(1);

        persist(p);

        p = em.find(Person.class, 1);
        assertEquals(null, p.getName());
    }

    @Test
    public void testInterceptedNullStringPersistence() {
        props.put("hibernate.ejb.interceptor",
            "org.candlepin.hibernate.EmptyStringInterceptor");

        cfg.addProperties(props);
        emf = cfg.buildEntityManagerFactory();
        em = emf.createEntityManager();

        Person p = new Person();
        p.setName(null);
        p.setId(1);

        persist(p);

        p = em.find(Person.class, 1);
        assertEquals(null, p.getName());
    }

    private void persist(Person p) {
        em.getTransaction().begin();
        em.persist(p);
        em.flush();
        em.getTransaction().commit();
    }
}
