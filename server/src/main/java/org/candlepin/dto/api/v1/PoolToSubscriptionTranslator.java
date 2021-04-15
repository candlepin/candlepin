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
package org.candlepin.dto.api.v1;

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.ObjectTranslator;
import org.candlepin.model.Cdn;
import org.candlepin.model.Certificate;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.util.Util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;


/**
 * The PoolToSubscriptionTranslator provides translation from {@link Pool}
 * model objects to {@link SubscriptionDTO}
 */
public class PoolToSubscriptionTranslator implements ObjectTranslator<Pool, SubscriptionDTO> {

    private static Logger log = LoggerFactory.getLogger(PoolToSubscriptionTranslator.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public SubscriptionDTO translate(Pool source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SubscriptionDTO translate(ModelTranslator translator, Pool source) {
        return source != null ? this.populate(translator, source, new SubscriptionDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SubscriptionDTO populate(Pool source, SubscriptionDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SubscriptionDTO populate(
        ModelTranslator translator, Pool source, SubscriptionDTO dest) {

        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (dest == null) {
            throw new IllegalArgumentException("dest is null");
        }

        dest.id(source.getSubscriptionId())
            .quantity(getQuantityFromPool(source))
            .startDate(Util.toDateTime(source.getStartDate()))
            .endDate(Util.toDateTime(source.getEndDate()))
            .contractNumber(source.getContractNumber())
            .accountNumber(source.getAccountNumber())
            .modified(Util.toDateTime(source.getUpdated()))
            .lastModified(Util.toDateTime(source.getLastModified()))
            .created(Util.toDateTime(source.getCreated()))
            .updated(Util.toDateTime(source.getUpdated()))
            .orderNumber(source.getOrderNumber())
            .upstreamConsumerId(source.getUpstreamConsumerId())
            .upstreamEntitlementId(source.getUpstreamEntitlementId())
            .upstreamPoolId(source.getUpstreamPoolId())
            .stacked(source.isStacked())
            .stackId(source.getStackId());

        if (translator != null) {

            ProductDTO mainProduct = translator.translate(source.getProduct(), ProductDTO.class);
            dest.product(mainProduct);

            if (mainProduct != null) {
                ProductDTO derivedProduct = mainProduct.getDerivedProduct();
                dest.derivedProduct(derivedProduct);
                dest.setProvidedProducts(mainProduct.getProvidedProducts());

                if (derivedProduct != null) {
                    dest.setDerivedProvidedProducts(derivedProduct.getProvidedProducts());
                }
            }

            dest.owner(translateObject(translator,
                translator.getTranslator(Owner.class, NestedOwnerDTO.class),
                source.getOwner()));
            dest.certificate(translateObject(translator,
                translator.getTranslator(Certificate.class, CertificateDTO.class),
                source.getCertificate()));
            dest.cdn(translateObject(translator,
                translator.getTranslator(Cdn.class, CdnDTO.class),
                source.getCdn()));
        }
        else {
            dest.providedProducts(Collections.emptySet());
            dest.derivedProvidedProducts(Collections.emptySet());
        }

        return dest;
    }

    private <T, R> R translateObject(ModelTranslator modelTranslator,
        ObjectTranslator<T, R> translator, T source) {
        if (source == null) {
            return null;
        }
        else {
            return translator.translate(modelTranslator, source);
        }
    }

    private Long getQuantityFromPool(Pool pool) {
        Product product = pool.getProduct();
        Long poolQuantity = pool.getQuantity();

        /**
         * The following code reconstructs Subscription quantity from the Pool quantity.
         * To understand it, it is important to understand how pool (the parameter)
         * is created in candlepin from a source subscription.
         * The pool has quantity was computed from
         * source subscription quantity and was multiplied by product.multiplier.
         * To reconstruct subscription, we must therefore divide the quantity of the pool
         * by the product.multiplier.
         * It's not easy to find COMPLETE code related to the conversion of
         * subscription to the pool. There is a method convertToMasterPool in this class,
         * that should do part of that (multiplication is not there).
         * But looking at its javadoc, it directly instructs callers of the
         * convertToMasterPool method to override quantity with method
         * PoolRules.calculateQuantity (when browsing the code that calls convertToMasterPool,
         * the calculateQuantity is usually called after convertToMasterPool).
         * The method PoolRules.calculateQuantity does the actual
         * multiplication of pool.quantity by pool.product.multiplier.
         * It seems that we also need to account account for
         * instance_multiplier (again logic is in calculateQuantity). If the attribute
         * is present, we must further divide the poolQuantity by
         * product.getAttributeValue(Product.Attributes.INSTANCE_MULTIPLIER).
         */
        if (product != null && poolQuantity != null) {
            Long multiplier = pool.getProduct().getMultiplier();

            if (multiplier != null && multiplier != 0) {
                if (poolQuantity % multiplier != 0) {
                    log.error("Unable to calculate subscription quantity from pool; " +
                        "Pool quantity is not divisible by its product's multiplier: {}, {}, {}",
                        pool, poolQuantity, multiplier);
                }
                else {
                    poolQuantity /= multiplier;
                }

                //This is reverse of what part of PoolRules.calculateQuantity does. See that method
                //to understand why we check that upstreamPoolId must be null.
                if (product.hasAttribute(Product.Attributes.INSTANCE_MULTIPLIER) &&
                    pool.getUpstreamPoolId() == null) {

                    String instMultiplier = product.getAttributeValue(Product.Attributes.INSTANCE_MULTIPLIER);
                    if (instMultiplier != null) {
                        try {
                            Integer parsed = Integer.parseInt(instMultiplier);

                            if (parsed != 0 && poolQuantity % parsed == 0) {
                                poolQuantity /= parsed;
                            }
                        }
                        catch (NumberFormatException nfe) {
                            log.error("Malformed instance multiplier value on product: {}", instMultiplier);
                        }
                    }
                }
            }
        }
        else {
            log.warn("Unable to calculate subscription quantity from pool; " +
                "Pool quantity or product is null (quantity: {}, product: {})",
                poolQuantity, product);
        }

        return poolQuantity;
    }

}
