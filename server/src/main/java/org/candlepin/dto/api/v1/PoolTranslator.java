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
            .stackId(source.getStackId())
            .stacked(source.isStacked())
            .developmentPool(source.isDevelopmentPool())
            .sourceStackId(source.getSourceStackId())
            .subscriptionSubKey(source.getSubscriptionSubKey())
            .subscriptionId(source.getSubscriptionId())
            .href(source.getId() != null ? String.format("/pools/%s", source.getId()) : null)
            .locked(source.isLocked());

        // Set product fields
        Product product = source.getProduct();
        Product derived = null;

        if (product != null) {
            dest.setProductId(product.getId());
            dest.setProductName(product.getName());
            dest.setProductAttributes(createAttributes(product.getAttributes()));
            dest.setProvidedProducts(this.translateProvidedProducts(product.getProvidedProducts()));

            derived = product.getDerivedProduct();
        }
        else {
            dest.setProductId(null);
            dest.setProductName(null);
            dest.setProductAttributes(Collections.emptyList());
            dest.setProvidedProducts(Collections.emptySet());
        }

        if (derived != null) {
            dest.setDerivedProductId(derived.getId());
            dest.setDerivedProductName(derived.getName());
            dest.setDerivedProductAttributes(createAttributes(derived.getAttributes()));
            dest.setDerivedProvidedProducts(this.translateProvidedProducts(derived.getProvidedProducts()));
        }
        else {
            dest.setDerivedProductId(null);
            dest.setDerivedProductName(null);
            dest.setDerivedProductAttributes(Collections.emptyList());
            dest.setDerivedProvidedProducts(Collections.emptySet());
        }

        // Process nested objects if we have a model translator to use to the translation...
        if (modelTranslator != null) {
            Owner owner = source.getOwner();
            dest.setOwner(owner != null ? modelTranslator.translate(owner, NestedOwnerDTO.class) : null);

            dest.sourceEntitlement(createEntitlement(source.getSourceEntitlement()));

            if (product != null && product.getBranding() != null) {
                dest.setBranding(product.getBranding().stream()
                    .filter(productBranding -> productBranding != null)
                    .map(modelTranslator.getStreamMapper(Branding.class, BrandingDTO.class))
                    .collect(Collectors.toSet()));
            }
            else {
                dest.setBranding(Collections.emptySet());
            }
        }

        return dest;
    }

    private Set<ProvidedProductDTO> translateProvidedProducts(Collection<Product> provided) {
        Set<ProvidedProductDTO> output = new HashSet<>();

        if (provided != null) {
            // Impl note: This does not handle n-tier properly. Update this as necessary to add support
            // when we figure out exactly what n-tier means/needs.
            for (Product product : provided) {
                output.add(new ProvidedProductDTO()
                    .productId(product.getId())
                    .productName(product.getName())
                );
            }
        }

        return output;
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
