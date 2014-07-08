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
package org.canadianTenPin.service.impl;

import org.canadianTenPin.config.Config;
import org.canadianTenPin.config.ConfigProperties;
import org.canadianTenPin.exceptions.ServiceUnavailableException;
import org.canadianTenPin.model.Consumer;
import org.canadianTenPin.model.Owner;
import org.canadianTenPin.model.Product;
import org.canadianTenPin.model.ProductCurator;
import org.canadianTenPin.model.Subscription;
import org.canadianTenPin.model.SubscriptionCurator;
import org.canadianTenPin.service.SubscriptionServiceAdapter;

import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * default SubscriptionAdapter implementation
 */
public class DefaultSubscriptionServiceAdapter implements
        SubscriptionServiceAdapter {

    private static Logger log =
        LoggerFactory.getLogger(DefaultSubscriptionServiceAdapter.class);
    private SubscriptionCurator subCurator;
    private String activationPrefix;
    private I18n i18n;
    private ProductCurator prodCurator;

    @Inject
    public DefaultSubscriptionServiceAdapter(SubscriptionCurator subCurator,
            Config config, I18n i18n, ProductCurator prodCurator) {
        this.subCurator = subCurator;
        this.i18n = i18n;
        this.prodCurator = prodCurator;

        this.activationPrefix = config.getString(ConfigProperties.ACTIVATION_DEBUG_PREFIX);
        if ("".equals(this.activationPrefix)) {
            this.activationPrefix = null;
        }
    }

    @Override
    public Subscription getSubscription(String subscriptionId) {
        return subCurator.lookupByOwnerAndId(subscriptionId);
    }

    @Override
    public List<Subscription> getSubscriptions(Owner owner) {
        return subCurator.listByOwner(owner);
    }

    @Override
    public List<Subscription> getSubscriptions() {
        List<Subscription> toReturn = subCurator.listAll();
        return toReturn == null ? new LinkedList<Subscription>() : toReturn;
    }

    @Override
    public boolean hasUnacceptedSubscriptionTerms(Owner owner) {
        return false;
    }

    @Override
    public void sendActivationEmail(String subscriptionId) {
        // hosted-only
    }

    @Override
    public boolean canActivateSubscription(Consumer consumer) {
        if (this.activationPrefix != null) {
            return consumer.getName().startsWith(activationPrefix);
        }
        else {
            return false;
        }
    }

    @Override
    public void activateSubscription(Consumer consumer, String email,
        String emailLocale) {
        throw new ServiceUnavailableException(
            i18n.tr("Standalone canadianTenPin does not support redeeming a subscription."));
    }

    @Override
    public Subscription createSubscription(Subscription subscription) {
        subscription.setProduct(prodCurator.find(subscription.getProduct()
            .getId()));
        Set<Product> provided = new HashSet<Product>();
        for (Product incoming : subscription.getProvidedProducts()) {
            provided.add(prodCurator.find(incoming.getId()));
        }
        subscription.setProvidedProducts(provided);
        Subscription s = subCurator.create(subscription);
        return s;
    }

    @Override
    public void deleteSubscription(Subscription s) {
        subCurator.delete(s);
    }

    @Override
    public List<Subscription> getSubscriptions(Product product) {
        return subCurator.listByProduct(product);
    }
}
