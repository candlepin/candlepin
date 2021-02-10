/**
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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
package org.candlepin.controller.refresher;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.candlepin.controller.refresher.RefreshResult.EntityState;
import org.candlepin.model.AbstractHibernateObject;
import org.candlepin.model.Content;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;



/**
 * Test suite for the RefreshResult class
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class RefreshResultTest {

    private interface EntityGenerator<T extends AbstractHibernateObject> {
        Class<T> getEntityClass();

        T generate(String suffix);
    }

    public static Stream<Arguments> getEntityArgumentProvider() {
        Product product = new Product();
        product.setId("test_product");

        Content content = new Content();
        content.setId("test_content");

        Pool pool = new Pool();
        pool.setId("test_pool");

        return Stream.of(
            Arguments.of(product, Product.class, product.getId(), EntityState.CREATED),
            Arguments.of(product, Product.class, product.getId(), EntityState.UPDATED),
            Arguments.of(product, Product.class, product.getId(), EntityState.UNCHANGED),
            Arguments.of(product, Product.class, product.getId(), EntityState.DELETED),

            Arguments.of(content, Content.class, content.getId(), EntityState.CREATED),
            Arguments.of(content, Content.class, content.getId(), EntityState.UPDATED),
            Arguments.of(content, Content.class, content.getId(), EntityState.UNCHANGED),
            Arguments.of(content, Content.class, content.getId(), EntityState.DELETED),

            Arguments.of(pool, Pool.class, pool.getId(), EntityState.CREATED),
            Arguments.of(pool, Pool.class, pool.getId(), EntityState.UPDATED),
            Arguments.of(pool, Pool.class, pool.getId(), EntityState.UNCHANGED),
            Arguments.of(pool, Pool.class, pool.getId(), EntityState.DELETED));
    }


    @ParameterizedTest(name = "{displayName} [{index}]: {1}, {4}")
    @MethodSource("getEntityArgumentProvider")
    public <T extends AbstractHibernateObject> void testAddAndGetEntity(T entity, Class<T> cls,
        String entityId, EntityState state) {

        RefreshResult refreshResult = new RefreshResult();

        RefreshResult output = refreshResult.addEntity(cls, entity, state);
        assertSame(refreshResult, output);

        List<Class> classes = Arrays.asList(Product.class, Content.class, Pool.class);

        for (Class testClass : classes) {
            AbstractHibernateObject result = refreshResult.getEntity(testClass, entityId);

            if (testClass.equals(cls)) {
                assertSame(entity, result);
            }
            else {
                assertNull(result);
            }
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}]: {1}, {4}")
    @MethodSource("getEntityArgumentProvider")
    public <T extends AbstractHibernateObject> void testGetEntityFiltersByState(T entity, Class<T> cls,
        String entityId, EntityState state) {

        RefreshResult refreshResult = new RefreshResult();

        RefreshResult output = refreshResult.addEntity(cls, entity, state);
        assertSame(refreshResult, output);

        List<Class> classes = Arrays.asList(Product.class, Content.class, Pool.class);

        for (Class testClass : classes) {
            for (EntityState testState : EntityState.values()) {
                AbstractHibernateObject result = refreshResult.getEntity(testClass, entityId, testState);

                if (testClass.equals(cls) && testState == state) {
                    assertSame(entity, result);
                }
                else {
                    assertNull(result);
                }
            }
        }
    }

    @Test
    public void testGetEntityRequiresCorrectClass() {
        String productId = "test_id";

        Product product = new Product();
        product.setId(productId);

        RefreshResult refreshResult = new RefreshResult()
            .addEntity(Product.class, product, EntityState.CREATED);


        AbstractHibernateObject output = refreshResult.getEntity(Product.class, productId);
        assertSame(product, output);

        output = refreshResult.getEntity(Content.class, productId);
        assertNull(output);
    }

    @Test
    public void testGetEntityRequiresCorrectId() {
        String productId = "test_id";
        String badProductId = "bad_product_id";

        Product product = new Product();
        product.setId(productId);

        RefreshResult refreshResult = new RefreshResult()
            .addEntity(Product.class, product, EntityState.CREATED);


        Product output = refreshResult.getEntity(Product.class, productId);
        assertSame(product, output);

        output = refreshResult.getEntity(Product.class, badProductId);
        assertNull(output);
    }

    @Test
    public void testGetEntityDoesNotFetchWrongClass() {
        String entityId = "test_id";

        Product product = new Product();
        product.setId(entityId);

        Content content = new Content();
        content.setId(entityId);

        RefreshResult refreshResult = new RefreshResult()
            .addEntity(Product.class, product, EntityState.CREATED)
            .addEntity(Content.class, content, EntityState.CREATED);

        AbstractHibernateObject output = refreshResult.getEntity(Product.class, entityId);
        assertSame(product, output);

        output = refreshResult.getEntity(Content.class, entityId);
        assertSame(content, output);

        output = refreshResult.getEntity(Pool.class, entityId);
        assertNull(output);
    }

    @Test
    public void testGetEntityDoesNotFetchWrongId() {
        Product product1 = new Product();
        product1.setId("test_id-1");

        Product product2 = new Product();
        product2.setId("test_id-2");

        RefreshResult refreshResult = new RefreshResult()
            .addEntity(Product.class, product1, EntityState.CREATED)
            .addEntity(Product.class, product2, EntityState.CREATED);

        AbstractHibernateObject output = refreshResult.getEntity(Product.class, product1.getId());
        assertSame(product1, output);

        output = refreshResult.getEntity(Product.class, product2.getId());
        assertSame(product2, output);

        output = refreshResult.getEntity(Product.class, "bad_id");
        assertNull(output);
    }

    private RefreshResult buildRefreshResult(int perState) {
        RefreshResult refreshResult = new RefreshResult();

        List<EntityGenerator> generators = new LinkedList<>();

        generators.add(new EntityGenerator<Product>() {
            public Class<Product> getEntityClass() {
                return Product.class;
            }

            public Product generate(String suffix) {
                Product entity = new Product();
                entity.setId("test_product-" + suffix);

                return entity;
            }
        });

        generators.add(new EntityGenerator<Content>() {
            public Class<Content> getEntityClass() {
                return Content.class;
            }

            public Content generate(String suffix) {
                Content entity = new Content();
                entity.setId("test_content-" + suffix);

                return entity;
            }
        });

        generators.add(new EntityGenerator<Pool>() {
            public Class<Pool> getEntityClass() {
                return Pool.class;
            }

            public Pool generate(String suffix) {
                Pool entity = new Pool();
                entity.setId("test_pool-" + suffix);

                return entity;
            }
        });

        for (EntityGenerator generator : generators) {
            int idSuffix = 1;

            for (EntityState state : EntityState.values()) {
                for (int i = 0; i < perState; ++i) {
                    AbstractHibernateObject entity = generator.generate(String.valueOf(idSuffix++));
                    refreshResult.addEntity(generator.getEntityClass(), entity, state);
                }
            }
        }

        return refreshResult;
    }

    private <T extends AbstractHibernateObject> void validateEntityMap(RefreshResult refreshResult,
        Class<T> cls, int expectedSize, Collection<EntityState> stateFilter) {

        Map<String, T> entities = refreshResult.getEntities(cls, stateFilter);

        assertNotNull(entities);
        assertEquals(expectedSize, entities.size());

        Set<T> retrieved = new HashSet<>();

        for (T entity : entities.values()) {
            assertNotNull(entity);
            assertThat(retrieved, not(hasItem(entity)));

            if (stateFilter != null) {
                assertThat(stateFilter,
                    hasItem(refreshResult.getEntityState(entity.getClass(), (String) entity.getId())));
            }

            retrieved.add(entity);
        }
    }

    @Test
    public void testGetEntities() {
        int perState = 3;

        RefreshResult refreshResult = this.buildRefreshResult(perState);

        int expectedSize = perState * EntityState.values().length;

        this.validateEntityMap(refreshResult, Product.class, expectedSize, null);
        this.validateEntityMap(refreshResult, Content.class, expectedSize, null);
        this.validateEntityMap(refreshResult, Pool.class, expectedSize, null);
    }

    public static Stream<Arguments> stateFilterProvider() {
        return Stream.of(
            Arguments.of(3, Arrays.asList(EntityState.CREATED), 3),
            Arguments.of(3, Arrays.asList(EntityState.UPDATED), 3),
            Arguments.of(3, Arrays.asList(EntityState.UNCHANGED), 3),
            Arguments.of(3, Arrays.asList(EntityState.DELETED), 3),

            Arguments.of(3, Arrays.asList(EntityState.CREATED, EntityState.UPDATED), 6),
            Arguments.of(3, Arrays.asList(EntityState.UPDATED, EntityState.UNCHANGED), 6),
            Arguments.of(3, Arrays.asList(EntityState.UNCHANGED, EntityState.DELETED), 6),

            Arguments.of(3, Arrays.asList(EntityState.values()), 3 * EntityState.values().length));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: {1}")
    @MethodSource("stateFilterProvider")
    public void testGetEntitiesFiltersByState(int perState, Collection<EntityState> stateFilter,
        int expectedSize) {

        RefreshResult refreshResult = this.buildRefreshResult(perState);

        this.validateEntityMap(refreshResult, Product.class, expectedSize, stateFilter);
        this.validateEntityMap(refreshResult, Content.class, expectedSize, stateFilter);
        this.validateEntityMap(refreshResult, Pool.class, expectedSize, stateFilter);
    }

    @Test
    public void testGetEntitiesRequiresCorrectClass() {
        Product product1 = new Product();
        product1.setId("test_product-1");

        Product product2 = new Product();
        product2.setId("test_product-2");

        Product product3 = new Product();
        product3.setId("test_product-3");

        Content content1 = new Content();
        content1.setId("test_content-1");

        Content content2 = new Content();
        content2.setId("test_content-2");

        Content content3 = new Content();
        content3.setId("test_content-3");

        RefreshResult refreshResult = new RefreshResult()
            .addEntity(Product.class, product1, EntityState.CREATED)
            .addEntity(Product.class, product2, EntityState.UPDATED)
            .addEntity(Product.class, product3, EntityState.UNCHANGED)
            .addEntity(Content.class, content1, EntityState.CREATED)
            .addEntity(Content.class, content2, EntityState.UPDATED)
            .addEntity(Content.class, content3, EntityState.UNCHANGED);

        Map<String, ? extends AbstractHibernateObject> output = refreshResult.getEntities(Product.class);
        assertNotNull(output);
        assertEquals(3, output.size());
        assertThat(output, hasEntry(product1.getId(), product1));
        assertThat(output, hasEntry(product2.getId(), product2));
        assertThat(output, hasEntry(product3.getId(), product3));

        output = refreshResult.getEntities(Content.class);
        assertNotNull(output);
        assertEquals(3, output.size());
        assertThat(output, hasEntry(content1.getId(), content1));
        assertThat(output, hasEntry(content2.getId(), content2));
        assertThat(output, hasEntry(content3.getId(), content3));

        output = refreshResult.getEntities(Pool.class);
        assertNotNull(output);
        assertEquals(0, output.size());
    }



}
