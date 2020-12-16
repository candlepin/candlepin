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
package org.candlepin.service.impl;

import org.candlepin.common.exceptions.ServiceUnavailableException;
import org.candlepin.dto.manifest.v1.ProductDTO;
import org.candlepin.dto.manifest.v1.SubscriptionDTO;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.service.model.ConsumerInfo;

import com.google.inject.Inject;

import org.xnap.commons.i18n.I18n;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;



/**
 * @author mstead
 */
public class ImportSubscriptionServiceAdapter implements SubscriptionServiceAdapter {

    private List<SubscriptionDTO> subscriptions;
    private Map<String, SubscriptionDTO> subsBySubId = new HashMap<>();
    @Inject private I18n i18n;

    public ImportSubscriptionServiceAdapter() {
        this(new LinkedList<>());
    }

    public ImportSubscriptionServiceAdapter(List<SubscriptionDTO> subs) {
        this.subscriptions = subs;
        for (SubscriptionDTO sub : this.subscriptions) {
            subsBySubId.put(sub.getId(), sub);
        }
    }

    @Override
    public Collection<SubscriptionDTO> getSubscriptions() {
        return subscriptions;
    }

    @Override
    public SubscriptionDTO getSubscription(String subscriptionId) {
        return this.subsBySubId.get(subscriptionId);
    }

    @Override
    public Collection<? extends SubscriptionDTO> getSubscriptions(String ownerKey) {
        return subscriptions;
    }

    @Override
    public Collection<String> getSubscriptionIds(String ownerKey) {
        return new ArrayList<>(subsBySubId.keySet());
    }

    @Override
    public Collection<? extends SubscriptionDTO> getSubscriptionsByProductId(String productId) {
        List<SubscriptionDTO> subs = new LinkedList<>();

        if (productId != null) {
            for (SubscriptionDTO sub : this.subscriptions) {
                ProductDTO product = sub.getProduct();
                if (product == null) {
                    continue;
                }

                if (productId.equals(product.getId())) {
                    subs.add(sub);
                    continue;
                }

                Collection<ProductDTO> providedProducts = product.getProvidedProducts();
                if (providedProducts != null) {
                    for (ProductDTO provided : providedProducts) {
                        if (provided != null && productId.equals(provided.getId())) {
                            subs.add(sub);
                            break;
                        }
                    }
                }
            }
        }

        return subs;
    }

    @Override
    public void activateSubscription(ConsumerInfo consumer, String email, String emailLocale) {
        throw new ServiceUnavailableException(
            i18n.tr("Standalone candlepin does not support redeeming a subscription."));
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public boolean hasUnacceptedSubscriptionTerms(String ownerKey) {
        return false;
    }

    @Override
    public void sendActivationEmail(String subscriptionId) {
        // hosted-only
    }

    @Override
    public boolean canActivateSubscription(ConsumerInfo consumer) {
        return false;
    }

}
