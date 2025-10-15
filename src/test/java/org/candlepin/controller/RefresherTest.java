/*
 * Copyright (c) 2009 - 2025 Red Hat, Inc.
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
package org.candlepin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.dto.ProductData;
import org.candlepin.model.dto.Subscription;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import com.google.inject.Inject;

import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.LockAcquisitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collection;
import java.util.Date;
import java.util.List;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class RefresherTest extends DatabaseTestFixture {
    @Inject
    private PoolManager poolManager;
    @Inject
    private PoolConverter poolConverter;

    @Mock
    private SubscriptionServiceAdapter subAdapter;

    private Refresher refresher;

    @BeforeEach
    public void beforeEach() {
        this.refresher = new Refresher(this.poolManager, this.subAdapter, this.ownerCurator, this.poolCurator,
            this.poolConverter);

        this.refresher.setLazyCertificateRegeneration(false);
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(booleans = {true, false})
    public void testSetLazyCertificateRegeneration(boolean lazy) {
        this.refresher.setLazyCertificateRegeneration(lazy);

        boolean actual = this.refresher.getLazyCertificateRegeneration();

        assertEquals(lazy, actual);
    }

    @Test
    public void testAddOwnerWithNullValue() {
        Owner owner = null;

        assertThrows(IllegalArgumentException.class, () -> this.refresher.add(owner));
    }

    @Test
    public void testAddOwnerWithKeylessOwner() {
        Owner owner = new Owner();

        assertThrows(IllegalArgumentException.class, () -> this.refresher.add(owner));
    }

    @Test
    public void testRunWithNoExistingPoolsForSubs() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();

        Content content1 = this.contentCurator.create(TestUtil.createContent());
        Content content2 = this.contentCurator.create(TestUtil.createContent());
        Content content3 = this.contentCurator.create(TestUtil.createContent());

        Product product1 = this.createProductContent(owner1, true, content1);
        Product product2 = this.createProductContent(owner2, true, content2);
        Product product3 = this.createProductContent(owner2, true, content3);
        Product productWithNoSub = this.createProduct();

        Subscription sub1 = new Subscription()
            .setId(TestUtil.randomString("sub-1-id-"))
            .setOwner(owner1)
            .setProduct(toProductData(product1))
            .setStartDate(new Date())
            .setEndDate(TestUtil.createDateOffset(0, 1, 0));
        doReturn(List.of(sub1))
            .when(subAdapter)
            .getSubscriptionsByProductId(product1.getId());

        Subscription sub2 = new Subscription()
            .setId(TestUtil.randomString("sub-2-id-"))
            .setOwner(owner2)
            .setProduct(toProductData(product2))
            .setStartDate(new Date())
            .setEndDate(TestUtil.createDateOffset(0, 1, 0));
        doReturn(List.of(sub2))
            .when(subAdapter)
            .getSubscriptionsByProductId(product2.getId());

        Subscription sub3 = new Subscription()
            .setId(TestUtil.randomString("sub-3-id-"))
            .setOwner(owner2)
            .setProduct(toProductData(product3))
            .setStartDate(new Date())
            .setEndDate(TestUtil.createDateOffset(0, 1, 0));
        Subscription subWithoutOwner = new Subscription()
            .setId(TestUtil.randomString("id-"));
        doReturn(List.of(sub3, subWithoutOwner))
            .when(subAdapter)
            .getSubscriptionsByProductId(product3.getId());

        this.refresher.add(owner1)
            .add(owner2)
            .add(product1)
            .add(product2)
            .add(product3)
            .add(productWithNoSub);

        this.refresher.run();

        List<Pool> owner1Pools = this.poolCurator.listByOwner(owner1);
        assertThat(owner1Pools)
            .isNotNull()
            .singleElement()
            .returns(product1.getUuid(), Pool::getProductUuid);

        List<Pool> owner2Pools = this.poolCurator.listByOwner(owner2);
        assertThat(owner2Pools)
            .isNotNull()
            .hasSize(2)
            .extracting(Pool::getProductUuid)
            .containsExactlyInAnyOrder(product2.getUuid(), product3.getUuid());
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(classes = {ConstraintViolationException.class, LockAcquisitionException.class})
    public void testRunShouldRetryTransactionForCertainException(Class<? extends Throwable> exception) {
        PoolManager mockPoolManager = Mockito.mock(PoolManager.class);
        refresher = new Refresher(mockPoolManager, this.subAdapter, this.ownerCurator, this.poolCurator,
            this.poolConverter);

        this.refresher.setLazyCertificateRegeneration(false);

        Owner owner = this.createOwner();
        Content content = this.contentCurator.create(TestUtil.createContent());
        Product product = this.createProductContent(owner, true, content);

        Subscription sub = new Subscription()
            .setId(TestUtil.randomString("sub-1-id-"))
            .setOwner(owner)
            .setProduct(toProductData(product))
            .setStartDate(new Date())
            .setEndDate(TestUtil.createDateOffset(0, 1, 0));
        doReturn(List.of(sub))
            .when(subAdapter)
            .getSubscriptionsByProductId(product.getId());

        this.refresher.add(owner)
            .add(product);

        // The transaction should be retried when the exception is thrown.
        // So, throw the exception on the first invocation, but then succeed on the second invocation.
        doThrow(exception)
            .doNothing()
            .when(mockPoolManager)
            .refreshPoolsWithRegeneration(any(SubscriptionServiceAdapter.class), any(Owner.class),
                any(boolean.class));

        this.refresher.run();

        List<Pool> pools = this.poolCurator.listByOwner(owner);
        assertThat(pools)
            .isNotNull()
            .singleElement()
            .returns(product.getUuid(), Pool::getProductUuid);

        verify(mockPoolManager, times(2))
            .refreshPoolsWithRegeneration(any(SubscriptionServiceAdapter.class), any(Owner.class),
                any(boolean.class));
    }

    private ProductData toProductData(Product product) {
        return new ProductData()
            .setId(product.getId())
            .setUuid(product.getUuid())
            .setName(product.getName())
            .setProvidedProducts(toProductData(product.getProvidedProducts()));
    }

    private List<ProductData> toProductData(Collection<Product> products) {
        return products.stream()
            .map(this::toProductData)
            .toList();
    }

}

