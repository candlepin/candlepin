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

import org.candlepin.junit.DatabaseTestExtension;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Map;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.Id;
import javax.persistence.Table;


public class EmptyStringInterceptorTest {

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

    @Nested
    class WithoutInterceptor {

        @RegisterExtension
        static DatabaseTestExtension db =
            DatabaseTestExtension.lightweight("testingEmptyStringInterceptor");

        private EntityManager em;

        @BeforeEach
        void setUp() {
            this.em = db.getEntityManager();
        }

        @Test
        void testNormalEmptyStringPersistence() {
            Person p = new Person();
            p.setName("");
            p.setId(1);

            em.getTransaction().begin();
            em.persist(p);
            em.flush();
            em.getTransaction().commit();
            em.clear();

            p = em.find(Person.class, 1);
            assertEquals("", p.getName());
        }
    }

    @Nested
    class WithInterceptor {

        @RegisterExtension
        static DatabaseTestExtension db = DatabaseTestExtension.lightweight(
            "testingEmptyStringInterceptor",
            Map.of("hibernate.ejb.interceptor",
                "org.candlepin.hibernate.EmptyStringInterceptor"));

        private EntityManager em;

        @BeforeEach
        void setUp() {
            this.em = db.getEntityManager();
        }

        @Test
        void testInterceptedEmptyStringPersistence() {
            Person p = new Person();
            p.setName("");
            p.setId(1);

            em.getTransaction().begin();
            em.persist(p);
            em.flush();
            em.getTransaction().commit();
            em.clear();

            p = em.find(Person.class, 1);
            assertNull(p.getName());
        }

        @Test
        void testInterceptedNullStringPersistence() {
            Person p = new Person();
            p.setName(null);
            p.setId(1);

            em.getTransaction().begin();
            em.persist(p);
            em.flush();
            em.getTransaction().commit();
            em.clear();

            p = em.find(Person.class, 1);
            assertNull(p.getName());
        }
    }
}
