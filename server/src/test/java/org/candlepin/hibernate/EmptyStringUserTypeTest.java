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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.hibernate.annotations.Type;
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
 * EmptyStringUserTypeTest
 */
public class EmptyStringUserTypeTest {
    private EntityManagerFactory emf;
    private EntityManager em;

    @Entity
    static class Thing {
        @Id
        private int id;

        @Column
        @Type(type = "org.candlepin.hibernate.EmptyStringUserType")
        private String typed;

        @Column
        private String notTyped;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getTyped() {
            return typed;
        }

        public void setTyped(String typed) {
            this.typed = typed;
        }

        public String getNotTyped() {
            return notTyped;
        }

        public void setNotTyped(String notTyped) {
            this.notTyped = notTyped;
        }
    }

    @Before
    public void setUp() throws Exception {
        Properties props = new Properties();
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
        props.put("hibernate.ejb.interceptor",
            "org.candlepin.hibernate.EmptyStringInterceptor");

        Ejb3Configuration cfg = new Ejb3Configuration();
        cfg.addAnnotatedClass(Thing.class);
        cfg.addProperties(props);

        emf = cfg.buildEntityManagerFactory();
        em = emf.createEntityManager();
    }

    @After
    public void tearDown() throws Exception {
        em.close();
        emf.close();
    }

    @Test
    public void testUserType() {
        Thing t = new Thing();
        t.setTyped(null);
        t.setNotTyped(null);
        t.setId(1);

        em.getTransaction().begin();
        em.persist(t);
        em.flush();
        em.getTransaction().commit();
        em.clear();

        t = em.find(Thing.class, 1);

        assertEquals(null, t.getNotTyped());
        assertEquals("", t.getTyped());
    }

    @Test
    public void testEqualsNullAndEmptyString() {
        String x = null;
        String y = "";

        EmptyStringUserType ut = new EmptyStringUserType();
        assertTrue(ut.equals(x, y));

        x = "";
        y = null;

        assertTrue(ut.equals(x, y));
    }

    @Test
    public void testEqualsNormal() {
        String x = "Hello";
        String y = "Hello";
        EmptyStringUserType ut = new EmptyStringUserType();
        assertTrue(ut.equals(x, y));

        y = "World";
        assertFalse(ut.equals(x, y));
    }
}
