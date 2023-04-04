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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hibernate.annotations.Type;
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



public class EmptyStringUserTypeTest {
    private EntityManagerFactory emf;
    private EntityManager em;

    @Entity
    @Table(name = "Thing")
    public static class Thing {
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

    @BeforeEach
    public void setUp() throws Exception {
        Properties props = new Properties();

        props.put("hibernate.ejb.interceptor",
            "org.candlepin.hibernate.EmptyStringInterceptor");

        emf =  Persistence.createEntityManagerFactory("testingUserType");
        em = emf.createEntityManager();
    }

    @AfterEach
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

        assertNull(t.getNotTyped());
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
