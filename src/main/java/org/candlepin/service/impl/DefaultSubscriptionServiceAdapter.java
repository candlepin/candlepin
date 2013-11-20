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

import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.exceptions.ServiceUnavailableException;
import org.candlepin.model.Consumer;
import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.Subscription;
import org.candlepin.model.SubscriptionCurator;
import org.candlepin.service.SubscriptionServiceAdapter;

import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.Date;
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
    public List<Subscription> getSubscriptions(Owner owner, String productId) {

        log.debug("Searching for subscriptions providing: " + productId);
        List<Subscription> subs = new LinkedList<Subscription>();

        // We need "fuzzy" product matching, so we need to list all subs for this owner
        // and then filter out products that do not match:
        for (Subscription sub : getSubscriptions(owner)) {

            if (sub.getProduct().getId().equals(productId)) {
                subs.add(sub);
                if (log.isDebugEnabled()) {
                    log.debug("   found: " + sub);
                }
                continue;
            }
            else if (sub.provides(productId)) {
                if (log.isDebugEnabled()) {
                    log.debug("   found provides: " + sub);
                }
                subs.add(sub);
            }
        }
        return subs;
    }

    @Override
    public Subscription getSubscription(String subscriptionId) {
        return subCurator.lookupByOwnerAndId(subscriptionId);
    }

    @Override
    public List<Subscription> getSubscriptionsSince(Owner owner, Date sinceDate) {
        return subCurator.listByOwnerSince(owner, sinceDate);
    }

    @Override
    public List<Subscription> getSubscriptionsSince(Date sinceDate) {
        return subCurator.listSince(sinceDate);
    }

    @Override
    public List<Subscription> getSubscriptions(Owner owner) {
        return subCurator.listByOwner(owner);
    }

    @Override
    public Subscription findUeberSubscription(Owner owner) {
        return subCurator.findUeberSubscription(owner);
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
            i18n.tr("Standalone candlepin does not support redeeming a subscription."));
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
