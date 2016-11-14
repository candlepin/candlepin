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
package org.candlepin.model;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;

import org.candlepin.auth.Principal;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.controller.PoolManager;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.service.UniqueIdGenerator;
import org.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

/**
 * UeberCertGeneratorTest
 */
@RunWith(MockitoJUnitRunner.class)
public class UeberCertGeneratorTest {
    @Mock private PoolManager poolManager;
    @Mock private PoolCurator poolCurator;
    @Mock private ProductServiceAdapter prodAdapter;
    @Mock private ContentCurator contentCurator;
    @Mock private UniqueIdGenerator idGenerator;
    @Mock private SubscriptionServiceAdapter subService;
    @Mock private ConsumerTypeCurator consumerTypeCurator;
    @Mock private ConsumerCurator consumerCurator;
    @Mock private EntitlementCurator entitlementCurator;
    @Mock private EntitlementCertificateCurator entitlementCertCurator;
    @Mock private OwnerCurator ownerCurator;
    @Mock private Principal principal;

    private I18n i18n;
    private UeberCertificateGenerator ucg;
    private Owner targetOwner;
    private ConsumerType uConsumerType;

    @Before
    public void before() {
        i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        ucg = new UeberCertificateGenerator(poolManager, poolCurator, prodAdapter, contentCurator,
            idGenerator, subService, consumerTypeCurator, consumerCurator, entitlementCurator,
            entitlementCertCurator, ownerCurator, i18n);
        targetOwner = TestUtil.createOwner();

        uConsumerType = new ConsumerType(ConsumerType.ConsumerTypeEnum.UEBER_CERT);
        when(consumerTypeCurator.lookupByLabel(eq(ConsumerType.ConsumerTypeEnum.UEBER_CERT.toString())))
            .thenReturn(uConsumerType);
    }

    @Test(expected = NotFoundException.class)
    public void testGeneratorThrowsNotFoundExceptionWhenOwnerDoesNotExist() {
        String ownerKey = "UNKNOWN";
        when(ownerCurator.findAndLock(eq(ownerKey))).thenReturn(null);

        ucg.generate(ownerKey, principal);
    }

    @Test
    public void verifyUeberCertIsGeneratedWhenUeberConsumerIsNotFound() throws Exception {
        when(ownerCurator.findAndLock(eq(targetOwner.getKey()))).thenReturn(targetOwner);
        when(consumerCurator.findByName(eq(targetOwner), eq(Consumer.UEBER_CERT_CONSUMER))).thenReturn(null);
        runGenerateTest();
    }

    @Test
    public void verifyUeberCertIsRegeneratedWhenUeberConsumerIsFound() {
        Consumer uConsumer = new Consumer(Consumer.UEBER_CERT_CONSUMER, principal.getUsername(), targetOwner,
                uConsumerType);

        when(ownerCurator.findAndLock(eq(targetOwner.getKey()))).thenReturn(targetOwner);
        when(consumerCurator.findByName(eq(targetOwner), eq(Consumer.UEBER_CERT_CONSUMER)))
            .thenReturn(uConsumer);

        Product uProduct = Product.createUeberProductForOwner(targetOwner);
        Pool uPool = TestUtil.createPool(targetOwner, uProduct);
        EntitlementCertificate uCert = new EntitlementCertificate();
        Entitlement uEnt = TestUtil.createEntitlement(targetOwner, uConsumer, uPool, uCert);
        when(entitlementCurator.listByConsumer(eq(uConsumer))).thenReturn(Arrays.asList(uEnt));

        EntitlementCertificate newGeneratedCert = new EntitlementCertificate();
        when(entitlementCertCurator.listForConsumer(eq(uConsumer)))
            .thenReturn(Arrays.asList(newGeneratedCert));

        EntitlementCertificate cert = ucg.generate(targetOwner.getKey(), principal);
        assertEquals(newGeneratedCert, cert);

        // true, false
        // Verify that the new cert was generated from the old ueber cert.
        verify(poolManager).regenerateCertificatesOf(eq(uEnt), eq(true), eq(false));
        verify(poolManager, never()).createPoolsForSubscription(any(Subscription.class));
    }

    private void runGenerateTest() throws Exception {
        // Create Ueber Product
        Product uProduct = Product.createUeberProductForOwner(targetOwner);
        when(prodAdapter.createProduct(any(Product.class))).thenReturn(uProduct);

        // Create Ueber Content
        Content uContent = Content.createUeberContent(idGenerator, targetOwner, uProduct);
        when(contentCurator.create(any(Content.class))).thenReturn(uContent);

        // Create Ueber Subscription
        Subscription uSub = new Subscription(targetOwner, uProduct, new HashSet<Product>(), 1L, new Date(),
            new Date(), new Date());
        when(subService.createSubscription(any(Subscription.class))).thenReturn(uSub);

        // Create Ueber Consumer
        Consumer uConsumer = new Consumer(Consumer.UEBER_CERT_CONSUMER, principal.getUsername(), targetOwner,
            uConsumerType);
        when(consumerCurator.create(any(Consumer.class))).thenReturn(uConsumer);

        // Create Ueber Pool
        Pool uPool = TestUtil.createPool(targetOwner, uProduct);
        when(poolCurator.findUeberPool(eq(targetOwner))).thenReturn(uPool);

        // Generate ueber entitlement
        EntitlementCertificate uCert = new EntitlementCertificate();
        Entitlement uEnt = TestUtil.createEntitlement(targetOwner, uConsumer, uPool, uCert);
        when(poolManager.ueberCertEntitlement(eq(uConsumer), eq(uPool), eq(1))).thenReturn(uEnt);

        EntitlementCertificate cert = ucg.generate(targetOwner.getKey(), principal);
        assertNotNull(cert);
        assertEquals(uCert, cert);

        // Verify ProductContent is added to product.
        assertFalse(uProduct.getProductContent().isEmpty());

        verify(poolManager).createPoolsForSubscription(eq(uSub));
        verify(poolManager, never()).regenerateCertificatesOf(any(Entitlement.class), any(Boolean.class),
            any(Boolean.class));
    }
}
