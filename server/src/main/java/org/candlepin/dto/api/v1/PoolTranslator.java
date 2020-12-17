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
import org.candlepin.dto.TimestampedEntityTranslator;
import org.candlepin.model.Branding;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.SubscriptionsCertificate;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;


/**
 * The PoolTranslator provides translation from Pool model objects to PoolDTOs.
 */
public class PoolTranslator extends TimestampedEntityTranslator<Pool, PoolDTO> {

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
        dest = super.populate(modelTranslator, source, dest);

        dest.setId(source.getId());
        dest.setType(source.getType() != null ? source.getType().name() : null);
        dest.setActiveSubscription(source.getActiveSubscription());
        dest.setQuantity(source.getQuantity());
        dest.setStartDate(source.getStartDate());
        dest.setEndDate(source.getEndDate());
        dest.setAttributes(source.getAttributes());
        dest.setRestrictedToUsername(source.getRestrictedToUsername());
        dest.setContractNumber(source.getContractNumber());
        dest.setAccountNumber(source.getAccountNumber());
        dest.setOrderNumber(source.getOrderNumber());
        dest.setConsumed(source.getConsumed());
        dest.setExported(source.getExported());
        dest.setCalculatedAttributes(source.getCalculatedAttributes());
        dest.setUpstreamPoolId(source.getUpstreamPoolId());
        dest.setUpstreamEntitlementId(source.getUpstreamEntitlementId());
        dest.setUpstreamConsumerId(source.getUpstreamConsumerId());
        dest.setProductName(source.getProductName());
        dest.setProductId(source.getProductId());
        dest.setProductAttributes(source.getProductAttributes());
        dest.setStackId(source.getStackId());
        dest.setStacked(source.isStacked());
        dest.setDevelopmentPool(source.isDevelopmentPool());
        dest.setDerivedProductAttributes(source.getDerivedProductAttributes());
        dest.setDerivedProductId(source.getDerivedProductId());
        dest.setDerivedProductName(source.getDerivedProductName());
        dest.setSourceStackId(source.getSourceStackId());
        dest.setSubscriptionSubKey(source.getSubscriptionSubKey());
        dest.setSubscriptionId(source.getSubscriptionId());
        dest.setLocked(source.isLocked());

        // Process nested objects if we have a model translator to use to the translation...
        if (modelTranslator != null) {
            Owner owner = source.getOwner();
            dest.setOwner(owner != null ? modelTranslator.translate(owner, NestedOwnerDTO.class) : null);

            SubscriptionsCertificate subCertificate = source.getCertificate();
            dest.setCertificate(subCertificate != null ?
                modelTranslator.translate(subCertificate, CertificateDTO.class) : null);

            Entitlement sourceEntitlement = source.getSourceEntitlement();
            dest.setSourceEntitlement(sourceEntitlement != null ?
                modelTranslator.translate(sourceEntitlement, EntitlementDTO.class) : null);

            Collection<Branding> branding = source.getProduct() != null ?
                source.getProduct().getBranding() : null;
            if (branding != null && !branding.isEmpty()) {
                for (Branding brand : branding) {
                    if (brand != null) {
                        dest.addBranding(modelTranslator.translate(brand, BrandingDTO.class));
                    }
                }
            }
            else {
                dest.setBranding(Collections.emptySet());
            }

            Set<Product> products = source.getProvidedProducts();
            if (products != null && !products.isEmpty()) {
                for (Product prod : products) {
                    if (prod != null) {
                        dest.addProvidedProduct(
                            new PoolDTO.ProvidedProductDTO(prod.getId(), prod.getName()));
                    }
                }
            }
            else {
                dest.setProvidedProducts(Collections.<PoolDTO.ProvidedProductDTO>emptySet());
            }

            Set<Product> derivedProducts = source.getDerivedProvidedProducts();
            if (derivedProducts != null && !derivedProducts.isEmpty()) {
                for (Product derivedProd : derivedProducts) {
                    if (derivedProd != null) {
                        dest.addDerivedProvidedProduct(
                            new PoolDTO.ProvidedProductDTO(derivedProd.getId(), derivedProd.getName()));
                    }
                }
            }
            else {
                dest.setDerivedProvidedProducts(Collections.<PoolDTO.ProvidedProductDTO>emptySet());
            }
        }

        return dest;
    }
}
