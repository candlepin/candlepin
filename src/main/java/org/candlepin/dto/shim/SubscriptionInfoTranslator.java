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
package org.candlepin.dto.shim;

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.ObjectTranslator;
import org.candlepin.dto.api.server.v1.CdnDTO;
import org.candlepin.dto.api.server.v1.CertificateDTO;
import org.candlepin.dto.api.server.v1.NestedOwnerDTO;
import org.candlepin.dto.api.server.v1.ProductDTO;
import org.candlepin.dto.api.server.v1.SubscriptionDTO;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.service.model.SubscriptionInfo;
import org.candlepin.util.Util;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * The SubscriptionTranslator provides translation from SubscriptionInfo objects to the
 * SubscriptionDTO (hosted test API)
 */
public class SubscriptionInfoTranslator implements ObjectTranslator<SubscriptionInfo, SubscriptionDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public SubscriptionDTO translate(SubscriptionInfo source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SubscriptionDTO translate(ModelTranslator translator, SubscriptionInfo source) {
        return source != null ? this.populate(translator, source, new SubscriptionDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SubscriptionDTO populate(SubscriptionInfo source, SubscriptionDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SubscriptionDTO populate(
        ModelTranslator translator, SubscriptionInfo source, SubscriptionDTO dest) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (dest == null) {
            throw new IllegalArgumentException("dest is null");
        }

        dest.id(source.getId())
            .lastModified(Util.toDateTime(source.getLastModified()))
            .startDate(Util.toDateTime(source.getStartDate()))
            .endDate(Util.toDateTime(source.getEndDate()))
            .accountNumber(source.getAccountNumber())
            .contractNumber(source.getContractNumber())
            .orderNumber(source.getOrderNumber())
            .quantity(source.getQuantity())
            .upstreamConsumerId(source.getUpstreamConsumerId())
            .upstreamEntitlementId(source.getUpstreamEntitlementId())
            .upstreamPoolId(source.getUpstreamPoolId())
            .stackId(null)
            .stacked(null)
            .created(null)
            .updated(null);

        if (translator != null) {
            dest.cdn(translator.translate(source.getCdn(), CdnDTO.class))
                .certificate(translator.translate(source.getCertificate(), CertificateDTO.class))
                .owner(translator.translate(source.getOwner(), NestedOwnerDTO.class));

            ProductInfo product = source.getProduct();

            if (product != null) {
                dest.product(translator.translate(product, ProductDTO.class));

                Collection<? extends ProductInfo> providedProducts = product.getProvidedProducts();
                if (providedProducts != null && !providedProducts.isEmpty()) {
                    dest.providedProducts(translateProducts(translator, providedProducts));
                }
                else {
                    dest.providedProducts(new HashSet<>());
                }

                ProductInfo derivedProduct = product.getDerivedProduct();

                if (derivedProduct != null) {
                    dest.derivedProduct(translator.translate(derivedProduct, ProductDTO.class));
                    dest.derivedProvidedProducts(translateProducts(translator,
                        derivedProduct.getProvidedProducts()));
                }
                else {
                    dest.derivedProduct(null)
                        .derivedProvidedProducts(new HashSet<>());
                }
            }
            else {
                dest.product(null);
            }
        }
        else {
            dest.providedProducts(new HashSet<>())
                .derivedProvidedProducts(new HashSet<>())
                .cdn(null)
                .certificate(null)
                .owner(null);
        }

        return dest;
    }

    private Set<ProductDTO> translateProducts(
        ModelTranslator translator, Collection<? extends ProductInfo> products) {
        if (products == null) {
            return new HashSet<>();
        }

        return products.stream()
            .map(translator.getStreamMapper(ProductInfo.class, ProductDTO.class))
            .collect(Collectors.toSet());
    }

}
