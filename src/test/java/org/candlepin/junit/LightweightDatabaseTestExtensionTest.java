/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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
package org.candlepin.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.Table;


public class LightweightDatabaseTestExtensionTest {

    private static final String PERSISTENCE_UNIT = "testingLightweightDatabaseExtension";

    @Entity
    @Table(name = "lightweight_ext_test_entity")
    static class TestEntity {
        @Id
        private int id;

        @Column
        private String name;

        public TestEntity() {
            /* required by Hibernate */
        }

        TestEntity(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getName() {
            return this.name;
        }
    }

    private static ExtensionContext contextFor(Class<?> testClass) {
        ExtensionContext context = mock(ExtensionContext.class);
        doReturn(testClass).when(context).getRequiredTestClass();
        return context;
    }

    @Nested
    public class JdbcUrlGeneration {

        @Test
        public void testGeneratesDistinctUrlPerTestClass() throws Exception {
            LightweightDatabaseTestExtension extension1 = new LightweightDatabaseTestExtension(PERSISTENCE_UNIT);
            LightweightDatabaseTestExtension extension2 = new LightweightDatabaseTestExtension(PERSISTENCE_UNIT);
            ExtensionContext context1 = contextFor(JdbcUrlGeneration.class);
            ExtensionContext context2 = contextFor(TestEntity.class);

            try {
                extension1.beforeAll(context1);
                extension2.beforeAll(context2);

                assertThat(extension1.getJdbcUrl()).isNotEqualTo(extension2.getJdbcUrl());
            }
            finally {
                extension1.afterAll(context1);
                extension2.afterAll(context2);
            }
        }
    }

    @Nested
    public class SchemaCreationAndExtraProperties {

        @Test
        public void testCreateDropSchemaIsUsableImmediatelyAfterBeforeAll() throws Exception {
            LightweightDatabaseTestExtension extension = new LightweightDatabaseTestExtension(PERSISTENCE_UNIT);
            ExtensionContext context = contextFor(SchemaCreationAndExtraProperties.class);

            extension.beforeAll(context);
            try {
                extension.beforeEach(context);
                EntityManager em = extension.getEntityManager();

                em.getTransaction().begin();
                em.persist(new TestEntity(1, "shea"));
                em.getTransaction().commit();
                em.clear();

                assertThat(em.find(TestEntity.class, 1).getName()).isEqualTo("shea");
            }
            finally {
                extension.afterAll(context);
            }
        }

        @Test
        public void testExtraPropertiesAreMergedIntoEntityManagerFactory() throws Exception {
            LightweightDatabaseTestExtension extension = new LightweightDatabaseTestExtension(
                PERSISTENCE_UNIT,
                Map.of("hibernate.session_factory.interceptor",
                    "org.candlepin.hibernate.EmptyStringInterceptor"));
            ExtensionContext context = contextFor(SchemaCreationAndExtraProperties.class);

            extension.beforeAll(context);
            try {
                extension.beforeEach(context);
                EntityManager em = extension.getEntityManager();

                em.getTransaction().begin();
                em.persist(new TestEntity(1, ""));
                em.getTransaction().commit();
                em.clear();

                assertThat(em.find(TestEntity.class, 1).getName()).isNull();
            }
            finally {
                extension.afterAll(context);
            }
        }
    }

    @Nested
    public class AfterEachCleanup {

        @Test
        public void testRollsBackActiveTransactionClosesEntityManagerAndTruncatesData() throws Exception {
            LightweightDatabaseTestExtension extension = new LightweightDatabaseTestExtension(PERSISTENCE_UNIT);
            ExtensionContext context = contextFor(AfterEachCleanup.class);

            extension.beforeAll(context);
            try {
                extension.beforeEach(context);
                EntityManager committing = extension.getEntityManager();
                committing.getTransaction().begin();
                committing.persist(new TestEntity(1, "committed"));
                committing.getTransaction().commit();
                extension.afterEach(context);

                extension.beforeEach(context);
                EntityManager leavingActive = extension.getEntityManager();
                leavingActive.getTransaction().begin();
                leavingActive.persist(new TestEntity(2, "uncommitted"));
                leavingActive.flush();
                extension.afterEach(context);

                assertThat(committing.isOpen()).isFalse();
                assertThat(leavingActive.isOpen()).isFalse();

                extension.beforeEach(context);
                EntityManager verifying = extension.getEntityManager();
                assertThat(verifying.find(TestEntity.class, 1)).isNull();
                assertThat(verifying.find(TestEntity.class, 2)).isNull();
            }
            finally {
                extension.afterAll(context);
            }
        }
    }

    @Nested
    public class AfterAllCleanup {

        @Test
        public void testClosesEntityManagerFactoryAndRejectsFurtherUsage() throws Exception {
            LightweightDatabaseTestExtension extension = new LightweightDatabaseTestExtension(PERSISTENCE_UNIT);
            ExtensionContext context = contextFor(AfterAllCleanup.class);

            extension.beforeAll(context);
            extension.afterAll(context);

            assertThatThrownBy(() -> extension.beforeEach(context))
                .isInstanceOf(IllegalStateException.class);
        }
    }
}
