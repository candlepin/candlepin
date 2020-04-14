/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
package org.candlepin.dto.api.v1;

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.ObjectTranslator;
import org.candlepin.model.Branding;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.util.Util;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * The PoolTranslator provides translation from Pool model objects to PoolDTOs.
 */
public class PoolTranslator implements ObjectTranslator<Pool, PoolDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public PoolDTO translate(Pool source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PoolDTO translate(ModelTranslator translator, Pool source) {
        return source != null ? this.populate(translator, source, new PoolDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PoolDTO populate(Pool source, PoolDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("checkstyle:methodlength")
    public PoolDTO populate(ModelTranslator modelTranslator, Pool source, PoolDTO dest) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (dest == null) {
            throw new IllegalArgumentException("destination is null");
        }

        dest.id(source.getId())
            .type(source.getType() != null ? source.getType().name() : null)
            .activeSubscription(source.getActiveSubscription())
            .quantity(source.getQuantity())
            .created(Util.toDateTime(source.getCreated()))
            .updated(Util.toDateTime(source.getUpdated()))
            .startDate(Util.toDateTime(source.getStartDate()))
            .endDate(Util.toDateTime(source.getEndDate()))
            .attributes(createAttributes(source.getAttributes()))
            .restrictedToUsername(source.getRestrictedToUsername())
            .contractNumber(source.getContractNumber())
            .accountNumber(source.getAccountNumber())
            .orderNumber(source.getOrderNumber())
            .consumed(source.getConsumed())
            .exported(source.getExported())
            .calculatedAttributes(source.getCalculatedAttributes())
            .upstreamPoolId(source.getUpstreamPoolId())
            .upstreamEntitlementId(source.getUpstreamEntitlementId())
            .upstreamConsumerId(source.getUpstreamConsumerId())
            .productName(source.getProductName())
            .productId(source.getProductId())
            .productAttributes(createAttributes(source.getProductAttributes()))
            .stackId(source.getStackId())
            .stacked(source.isStacked())
            .developmentPool(source.isDevelopmentPool())
            .derivedProductAttributes(createAttributes(source.getDerivedProductAttributes()))
            .derivedProductId(source.getDerivedProductId())
            .derivedProductName(source.getDerivedProductName())
            .sourceStackId(source.getSourceStackId())
            .subscriptionSubKey(source.getSubscriptionSubKey())
            .subscriptionId(source.getSubscriptionId())
            .href(source.getId() != null ? String.format("/pools/%s", source.getId()) : null)
            .locked(source.isLocked());

        // Process nested objects if we have a model translator to use to the translation...
        if (modelTranslator != null) {
            Owner owner = source.getOwner();
            dest.setOwner(owner != null ? modelTranslator.translate(owner, NestedOwnerDTO.class) : null);

            dest.sourceEntitlement(createEntitlement(source.getSourceEntitlement()));

            Collection<Branding> branding = source.getProduct() != null ?
                source.getProduct().getBranding() : null;
            if (branding != null && !branding.isEmpty()) {
                Set<BrandingDTO> brandingDTOS = new HashSet<>();
                for (Branding brand : branding) {
                    if (brand != null) {
                        brandingDTOS.add(modelTranslator.translate(brand, BrandingDTO.class));
                    }
                }
                dest.setBranding(brandingDTOS);
            }
            else {
                dest.setBranding(Collections.emptySet());
            }

            Set<Product> products = source.getProvidedProducts();
            if (products != null && !products.isEmpty()) {
                Set<ProvidedProductDTO> productDTOS = new HashSet<>();
                for (Product prod : products) {
                    if (prod != null) {
                        productDTOS.add(new ProvidedProductDTO()
                            .productId(prod.getId())
                            .productName(prod.getName()));
                    }
                }
                dest.setProvidedProducts(productDTOS);
            }
            else {
                dest.setProvidedProducts(Collections.emptySet());
            }

            Set<Product> derivedProducts = source.getDerivedProvidedProducts();
            if (derivedProducts != null && !derivedProducts.isEmpty()) {
                Set<ProvidedProductDTO> productDTOS = new HashSet<>();
                for (Product derivedProd : derivedProducts) {
                    if (derivedProd != null) {
                        productDTOS.add(new ProvidedProductDTO()
                            .productId(derivedProd.getId())
                            .productName(derivedProd.getName()));
                    }
                }
                dest.setDerivedProvidedProducts(productDTOS);
            }
            else {
                dest.setDerivedProvidedProducts(Collections.emptySet());
            }
        }

        return dest;
    }

    private NestedEntitlementDTO createEntitlement(Entitlement source) {
        if (source == null) {
            return null;
        }
        return new NestedEntitlementDTO()
            .id(source.getId())
            .href(source.getHref());
    }

    private List<AttributeDTO> createAttributes(Map<String, String> source) {
        if (source == null) {
            return Collections.emptyList();
        }
        return source.entrySet().stream()
            .map(entry -> new AttributeDTO()
                .name(entry.getKey())
                .value(entry.getValue()))
            .collect(Collectors.toList());
    }
}
