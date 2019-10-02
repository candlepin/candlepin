/**
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.candlepin.model.Branding;
import org.candlepin.model.Product;
import org.candlepin.service.model.SubscriptionInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;



@ExtendWith(MockitoExtension.class)
public class ImportedEntityCompilerTest {

    private ImportedEntityCompiler compiler;
    private Product mktProduct;
    @Mock private SubscriptionInfo subscription;

    @BeforeEach
    public void init() {
        compiler = new ImportedEntityCompiler();

        // Create marketing product and derived marketing product for our subscription.
        mktProduct = new Product();
        mktProduct.setId("mkt_prod");
    }

    @Test
    public void shouldAddNoBrandingWhenSubscriptionBrandingIsNull() {
        doReturn(null).when(subscription).getBranding();
        compiler.addBranding(subscription);
        assertEquals(0, compiler.getBranding().size());
    }

    @Test
    public void shouldAddNoBrandingWhenSubscriptionBrandingIsEmpty() {
        doReturn(Collections.emptySet()).when(subscription).getBranding();
        compiler.addBranding(subscription);
        assertEquals(0, compiler.getBranding().size());
    }

    @Test
    public void shouldAddNoBrandingWhenSubscriptionProductIsNull() {
        Branding branding = new Branding("eng_prod_1", "OS", "brand_name_1");
        Set<Branding> brandingInfos = new HashSet<>();
        brandingInfos.add(branding);

        doReturn(brandingInfos).when(subscription).getBranding();
        doReturn(null).when(subscription).getProduct();

        compiler.addBranding(subscription);

        assertEquals(0, compiler.getBranding().size());
    }

    @Test
    public void shouldThrowExceptionWhenSubscriptionProductIdIsNull() {
        Branding branding = new Branding("eng_prod_1", "OS", "brand_name_1");
        Set<Branding> brandingInfos = new HashSet<>();
        brandingInfos.add(branding);

        doReturn(brandingInfos).when(subscription).getBranding();
        Product mktProd = new Product();
        mktProd.setId(null);
        doReturn(mktProd).when(subscription).getProduct();

        assertThrows(IllegalArgumentException.class, () -> compiler.addBranding(subscription));
    }

    @Test
    public void shouldThrowExceptionWhenSubscriptionProductIdIsEmpty() {
        Branding branding = new Branding("eng_prod_1", "OS", "brand_name_1");
        Set<Branding> brandingInfos = new HashSet<>();
        brandingInfos.add(branding);

        doReturn(brandingInfos).when(subscription).getBranding();
        Product mktProd = new Product();
        mktProd.setId("");
        doReturn(mktProd).when(subscription).getProduct();

        assertThrows(IllegalArgumentException.class, () -> compiler.addBranding(subscription));
    }

    @Test
    public void shouldAddAllBrandingWhenSubscriptionProductIsPresentAndComplete() {
        Branding branding = new Branding("eng_prod_1", "OS", "brand_name_1");
        Branding branding2 = new Branding("eng_prod_2", "OS", "brand_name_2");
        Set<Branding> brandingInfos = new HashSet<>();
        brandingInfos.add(branding);
        brandingInfos.add(branding2);

        doReturn(brandingInfos).when(subscription).getBranding();
        doReturn(mktProduct).when(subscription).getProduct();

        compiler.addBranding(subscription);

        assertEquals(1, compiler.getBranding().size());
        assertEquals(2, compiler.getBranding().get("mkt_prod").size());
    }

    /**
     * If we try to add a second set of brandings for the same derived marketing product, then the set of
     * brandings that was added last should replace any previous sets.
     */
    @Test
    public void shouldApplyLatestBrandingWhenExistingDerivedProductHasMultipleBrandingsAppliedToIt() {
        Branding branding = new Branding("eng_prod_1", "OS", "brand_name_1");
        Set<Branding> brandingInfos = new HashSet<>();
        brandingInfos.add(branding);

        doReturn(brandingInfos).when(subscription).getBranding();
        doReturn(mktProduct).when(subscription).getProduct();

        compiler.addBranding(subscription);

        // Add a second subscription to the compiler, which provides the same derived marketing product and
        // derived provided products, but has a different branding for the same derived provided product.
        SubscriptionInfo secondSubscription = mock(SubscriptionInfo.class);
        Branding secondBranding = new Branding("eng_prod_1", "non-OS", "Brand New Name");
        Set<Branding> brandingInfos2 = new HashSet<>();
        brandingInfos2.add(secondBranding);

        doReturn(brandingInfos2).when(secondSubscription).getBranding();
        doReturn(mktProduct).when(secondSubscription).getProduct();

        compiler.addBranding(secondSubscription);

        assertEquals(1, compiler.getBranding().size());
        assertEquals(1, compiler.getBranding().get("mkt_prod").size());

        // Check that the branding that was added last for that marketing derived product is present:
        assertTrue(compiler.getBranding().get("mkt_prod").contains(secondBranding));
    }
}
