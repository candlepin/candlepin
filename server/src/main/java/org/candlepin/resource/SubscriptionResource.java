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
package org.candlepin.resource;

import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.controller.ContentAccessManager;
import org.candlepin.controller.PoolManager;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.v1.SubscriptionDTO;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.service.SubscriptionServiceAdapter;

import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;


/**
 * SubscriptionResource
 */
public class SubscriptionResource implements SubscriptionsApi {
    private static Logger log = LoggerFactory.getLogger(SubscriptionResource.class);

    private final SubscriptionServiceAdapter subService;
    private final ConsumerCurator consumerCurator;
    private final PoolManager poolManager;
    private final I18n i18n;
    private final ModelTranslator translator;
    private ContentAccessManager contentAccessManager;

    @Inject
    public SubscriptionResource(SubscriptionServiceAdapter subService,
        ConsumerCurator consumerCurator, PoolManager poolManager, I18n i18n,
        ModelTranslator translator, ContentAccessManager contentAccessManager) {

        this.subService = Objects.requireNonNull(subService);
        this.consumerCurator = Objects.requireNonNull(consumerCurator);
        this.poolManager = Objects.requireNonNull(poolManager);
        this.i18n = Objects.requireNonNull(i18n);
        this.translator = Objects.requireNonNull(translator);
        this.contentAccessManager = Objects.requireNonNull(contentAccessManager);
    }

    @Override
    public List<SubscriptionDTO> getSubscriptions() {
        List<SubscriptionDTO> subscriptions = new LinkedList<>();

        for (Pool pool : this.poolManager.getMasterPools()) {
            subscriptions.add(this.translator.translate(
                this.poolManager.fabricateSubscriptionFromPool(pool),
                SubscriptionDTO.class));
        }

        return subscriptions;
    }

    @Override
    public Response activateSubscription(String consumerUuid, String email, String emailLocale) {

        if (email == null) {
            throw new BadRequestException(i18n.tr("email is required for notification"));
        }

        if (emailLocale == null) {
            throw new BadRequestException(i18n.tr("email locale is required for notification"));
        }

        Consumer consumer = consumerCurator.findByUuid(consumerUuid);

        if (consumer == null) {
            throw new BadRequestException(i18n.tr("No such unit: {0}", consumerUuid));
        }

        this.subService.activateSubscription(consumer, email, emailLocale);

        // setting response status to 202 because subscription does not
        // exist yet, but is currently being processed
        return Response.status(Status.ACCEPTED).build();
    }

    @Override
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

            this.poolManager.deletePool(pool);
            ++count;
        }

        if (count == 0) {
            throw new NotFoundException(
                i18n.tr("A subscription with the ID \"{0}\" could not be found.", subscriptionId));
        }

        for (Owner owner : owners) {
            log.debug("Synchronizing last content update for org: {}", owner);
            this.contentAccessManager.syncOwnerLastContentUpdate(owner);
        }
    }

}
