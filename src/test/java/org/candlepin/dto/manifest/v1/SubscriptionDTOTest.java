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
package org.candlepin.dto.manifest.v1;

import org.candlepin.dto.AbstractDTOTest;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;



/**
 * Test suite for the SubscriptionDTO class
 */
public class SubscriptionDTOTest extends AbstractDTOTest<SubscriptionDTO> {

    protected Map<String, Object> values;

    public SubscriptionDTOTest() {
        super(SubscriptionDTO.class);

        OwnerDTO owner = new OwnerDTO();
        owner.setId("owner_id");
        owner.setKey("owner_key");

        ProductDTO product = new ProductDTO();
        product.setId("product_id");
        product.setName("product_name");

        ProductDTO derivedProduct = new ProductDTO();
        derivedProduct.setId("derived_product_id");
        derivedProduct.setName("derived_product_name");

        List<ProductDTO> providedProducts = new ArrayList<>();
        for (int i = 0; i < 5; ++i) {
            ProductDTO pp = new ProductDTO();
            pp.setId("provided_product_id-" + i);
            pp.setName("provided_product_name-" + i);

            providedProducts.add(pp);
        }

        List<ProductDTO> derivedProvidedProducts = new ArrayList<>();
        for (int i = 0; i < 5; ++i) {
            ProductDTO dpp = new ProductDTO();
            dpp.setId("derived_provided_product_id-" + i);
            dpp.setName("derived_provided_product_name-" + i);

            derivedProvidedProducts.add(dpp);
        }

        CdnDTO cdn = new CdnDTO();
        cdn.setId("cdn_id");
        cdn.setName("cdn_name");

        List<BrandingDTO> branding = new ArrayList<>();
        for (int i = 0; i < 5; ++i) {
            BrandingDTO brand = new BrandingDTO();
            brand.setId("branding_id-" + i);
            brand.setProductId("branded_product_id-" + i);
            brand.setName("branding_name-" + i);

            branding.add(brand);
        }

        CertificateDTO cert = new CertificateDTO();
        cert.setId("certificate_id");


        this.values = new HashMap<>();
        this.values.put("Id", "test-id");
        this.values.put("Owner", owner);

        this.values.put("Product", product);
        this.values.put("ProvidedProducts", providedProducts);
        this.values.put("DerivedProduct", derivedProduct);
        this.values.put("DerivedProvidedProducts", derivedProvidedProducts);

        this.values.put("Quantity", 50L);

        this.values.put("StartDate", new Date(System.currentTimeMillis() - 100000));
        this.values.put("EndDate", new Date(System.currentTimeMillis() + 100000));
        this.values.put("LastModified", new Date(System.currentTimeMillis() - 40000));

        this.values.put("ContractNumber", "contract_number");
        this.values.put("AccountNumber", "account_number");
        this.values.put("OrderNumber", "order_number");
        this.values.put("UpstreamPoolId", "upstream_pool_id");
        this.values.put("UpstreamEntitlementId", "upstream_entitlement_id");
        this.values.put("UpstreamConsumerId", "upstream_consumer_id");

        this.values.put("Cdn", cdn);
        this.values.put("Branding", branding);
        this.values.put("Certificate", cert);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Object getInputValueForMutator(String field) {
        return this.values.get(field);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Object getOutputValueForAccessor(String field, Object input) {
        // Nothing to do here
        return input;
    }
}
