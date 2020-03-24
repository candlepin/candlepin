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
import org.candlepin.model.dto.ProductData;
import org.candlepin.model.dto.Subscription;
import org.candlepin.util.Util;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * The SubscriptionTranslator provides translation from {@link Subscription}
 * model objects to {@link SubscriptionDTO}
 */
public class SubscriptionTranslator implements ObjectTranslator<Subscription, SubscriptionDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public SubscriptionDTO translate(Subscription source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SubscriptionDTO translate(ModelTranslator translator, Subscription source) {
        return source != null ? this.populate(translator, source, new SubscriptionDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SubscriptionDTO populate(Subscription source, SubscriptionDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SubscriptionDTO populate(
        ModelTranslator translator, Subscription source, SubscriptionDTO dest) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (dest == null) {
            throw new IllegalArgumentException("dest is null");
        }

        dest.id(source.getId())
            .quantity(source.getQuantity())
            .startDate(Util.toDateTime(source.getStartDate()))
            .endDate(Util.toDateTime(source.getEndDate()))
            .contractNumber(source.getContractNumber())
            .accountNumber(source.getAccountNumber())
            .modified(Util.toDateTime(source.getModified()))
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
            dest.derivedProduct(translateObject(translator,
                translator.getTranslator(ProductData.class, ProductDTO.class),
                source.getDerivedProduct()));
            dest.setProvidedProducts(translateToSet(
                translator.getStreamMapper(ProductData.class, ProductDTO.class),
                source.getProvidedProducts()));
            dest.derivedProvidedProducts(translateToSet(
                translator.getStreamMapper(ProductData.class, ProductDTO.class),
                source.getDerivedProvidedProducts()));
            dest.owner(translateObject(translator,
                translator.getTranslator(Owner.class, NestedOwnerDTO.class),
                source.getOwner()));
            dest.product(translateObject(translator,
                translator.getTranslator(ProductData.class, ProductDTO.class),
                source.getProduct()));
            dest.cert(translateObject(translator,
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

    private <T, R> Set<R> translateToSet(Function<T, R> translator, Collection<T> source) {
        if (source == null) {
            return Collections.emptySet();
        }
        else {
            return source.stream()
                .map(translator)
                .collect(Collectors.toSet());
        }
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

}
