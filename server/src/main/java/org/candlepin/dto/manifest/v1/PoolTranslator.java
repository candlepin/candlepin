/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.ObjectTranslator;
import org.candlepin.model.Branding;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.SubscriptionsCertificate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;



/**
 * The PoolTranslator provides translation from Pool model objects to PoolDTOs
 * as used by the manifest import/export framework.
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
    public PoolDTO populate(ModelTranslator modelTranslator, Pool source, PoolDTO dest) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (dest == null) {
            throw new IllegalArgumentException("destination is null");
        }

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
        dest.setStackId(source.getStackId());
        dest.setStacked(source.isStacked());
        dest.setDevelopmentPool(source.isDevelopmentPool());
        dest.setSourceStackId(source.getSourceStackId());
        dest.setSubscriptionSubKey(source.getSubscriptionSubKey());
        dest.setSubscriptionId(source.getSubscriptionId());

        // Set product fields
        Product product = source.getProduct();
        Product derived = null;

        if (product != null) {
            dest.setProductId(product.getId());
            dest.setProductName(product.getName());
            dest.setProductAttributes(product.getAttributes());
            dest.setProvidedProducts(this.translateProvidedProducts(product.getProvidedProducts()));

            derived = product.getDerivedProduct();
        }
        else {
            dest.setProductId(null);
            dest.setProductName(null);
            dest.setProductAttributes(Collections.emptyMap());
            dest.setProvidedProducts(Collections.emptySet());
        }

        if (derived != null) {
            dest.setDerivedProductId(derived.getId());
            dest.setDerivedProductName(derived.getName());
            dest.setDerivedProductAttributes(derived.getAttributes());
            dest.setDerivedProvidedProducts(this.translateProvidedProducts(derived.getProvidedProducts()));
        }
        else {
            dest.setDerivedProductId(null);
            dest.setDerivedProductName(null);
            dest.setDerivedProductAttributes(Collections.emptyMap());
            dest.setDerivedProvidedProducts(Collections.emptySet());
        }

        // Process nested objects if we have a model translator to use to the translation...
        if (modelTranslator != null) {
            Owner owner = source.getOwner();
            dest.setOwner(owner != null ? modelTranslator.translate(owner, OwnerDTO.class) : null);

            SubscriptionsCertificate subCertificate = source.getCertificate();
            dest.setCertificate(subCertificate != null ?
                modelTranslator.translate(subCertificate, CertificateDTO.class) : null);

            Entitlement sourceEntitlement = source.getSourceEntitlement();
            dest.setSourceEntitlement(sourceEntitlement != null ?
                modelTranslator.translate(sourceEntitlement, EntitlementDTO.class) : null);

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

    private Collection<PoolDTO.ProvidedProductDTO> translateProvidedProducts(Collection<Product> provided) {
        Collection<PoolDTO.ProvidedProductDTO> output = new ArrayList<>();

        if (provided != null) {
            // Impl note: This does not handle n-tier properly. Update this as necessary to add support
            // when we figure out exactly what n-tier means/needs.
            for (Product product : provided) {
                output.add(new PoolDTO.ProvidedProductDTO(product.getId(), product.getName()));
            }
        }

        return output;
    }
}
