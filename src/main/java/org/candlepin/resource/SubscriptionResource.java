/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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
package org.candlepin.resource;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.candlepin.auth.Verify;

import jakarta.ws.rs.core.Response;

import org.candlepin.config.Configuration;
import org.candlepin.controller.ContentAccessManager;
import org.candlepin.controller.PoolManager;
import org.candlepin.controller.PoolService;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.server.v1.SubscriptionDTO;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.exceptions.NotImplementedException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.resource.server.v1.SubscriptionApi;
import org.candlepin.service.SubscriptionServiceAdapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;


/**
 * SubscriptionResource
 */
public class SubscriptionResource implements SubscriptionApi {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionResource.class);

    private final Configuration config;
    private final SubscriptionServiceAdapter subService;
    private final ConsumerCurator consumerCurator;
    private final PoolManager poolManager;
    private final PoolService poolService;
    private final I18n i18n;
    private final ModelTranslator translator;
    private final ContentAccessManager contentAccessManager;

    @Inject
    public SubscriptionResource(Configuration config, SubscriptionServiceAdapter subService,
                                ConsumerCurator consumerCurator, PoolManager poolManager, I18n i18n,
                                ModelTranslator translator, ContentAccessManager contentAccessManager,
                                PoolService poolService) {

        this.config = Objects.requireNonNull(config);
        this.subService = Objects.requireNonNull(subService);
        this.consumerCurator = Objects.requireNonNull(consumerCurator);
        this.poolManager = Objects.requireNonNull(poolManager);
        this.poolService = Objects.requireNonNull(poolService);
        this.i18n = Objects.requireNonNull(i18n);
        this.translator = Objects.requireNonNull(translator);
        this.contentAccessManager = Objects.requireNonNull(contentAccessManager);
    }

    @Override
    @Transactional
    public Stream<SubscriptionDTO> getSubscriptions() {
        return this.poolManager.getPrimaryPools()
            .stream()
            .map(this.translator.getStreamMapper(Pool.class, SubscriptionDTO.class));
    }

    @Override
    @Transactional
    public Response activateSubscription(@Verify(Consumer.class) String consumerUuid, String email,
                                         String emailLocale) {

        throw new NotImplementedException(
            "Subscription activation through 'subscription-manager redeem' is no longer supported, " +
                "please visit https://access.redhat.com/subscriptions/activate/dell to redeem your service tag.");
    }

    @Override
    @Transactional
    public void deleteSubscription(String subscriptionId) {

        Set<Owner> owners = new HashSet<>();
        int count = 0;

        // Lookup pools from subscription ID
        for (Pool pool : this.poolManager.getPoolsBySubscriptionId(subscriptionId)) {
            // Impl note:
            // This shouldn't ever be more than one, but there's nothing in the code that actually
            // prevents this, and it's trivial to not run headlong into a bug here, so we'll just
            // handle it anyhow.
            owners.add(pool.getOwner());

            this.poolService.deletePool(pool);
            ++count;
        }

        if (count == 0) {
            throw new NotFoundException(
                i18n.tr("A subscription with the ID \"{0}\" could not be found.", subscriptionId));
        }

        for (Owner owner : owners) {
            log.debug("Synchronizing last content update for org: {}", owner);
            owner.syncLastContentUpdate();
        }
    }

}
