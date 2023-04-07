/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Id;
import javax.persistence.Persistence;
import javax.persistence.Table;

/**
 * EmptyStringInterceptorTest
 */
public class EmptyStringInterceptorTest {
    private EntityManagerFactory emf;
    private EntityManager em;
    private Properties props;

    @Entity
    @Table(name = "Person")
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

    @BeforeEach
    public void setUp() {
        props = new Properties();
    }

    @AfterEach
    public void tearDown() {
        em.close();
        emf.close();
    }

    @Test
    public void testNormalEmptyStringPersistence() {
        emf = Persistence.createEntityManagerFactory("testingEmptyStringInterceptor");
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

        emf = Persistence.createEntityManagerFactory("testingEmptyStringInterceptor", props);
        em = emf.createEntityManager();

        Person p = new Person();
        p.setName("");
        p.setId(1);

        persist(p);

        p = em.find(Person.class, 1);
        assertNull(p.getName());
    }

    @Test
    public void testInterceptedNullStringPersistence() {
        props.put("hibernate.ejb.interceptor", "org.candlepin.hibernate.EmptyStringInterceptor");

        emf = Persistence.createEntityManagerFactory("testingEmptyStringInterceptor", props);
        em = emf.createEntityManager();

        Person p = new Person();
        p.setName(null);
        p.setId(1);

        persist(p);

        p = em.find(Person.class, 1);
        assertNull(p.getName());
    }

    private void persist(Person p) {
        em.getTransaction().begin();
        em.persist(p);
        em.flush();
        em.getTransaction().commit();
    }
}
