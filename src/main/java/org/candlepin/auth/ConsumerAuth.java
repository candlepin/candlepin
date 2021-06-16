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
package org.candlepin.auth;

import org.candlepin.exceptions.GoneException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.DeletedConsumerCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;

import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import javax.inject.Provider;

/**
 * ConsumerAuth
 */
public abstract class ConsumerAuth implements AuthProvider {

    private static Logger log = LoggerFactory.getLogger(ConsumerAuth.class);

    protected ConsumerCurator consumerCurator;
    protected OwnerCurator ownerCurator;
    protected DeletedConsumerCurator deletedConsumerCurator;
    private Provider<I18n> i18nProvider;

    @Inject
    ConsumerAuth(ConsumerCurator consumerCurator, OwnerCurator ownerCurator,
        DeletedConsumerCurator deletedConsumerCurator,
        Provider<I18n> i18nProvider) {
        this.consumerCurator = consumerCurator;
        this.ownerCurator = ownerCurator;
        this.deletedConsumerCurator = deletedConsumerCurator;
        this.i18nProvider = i18nProvider;
    }

    /**
     * Creates a principal according to the {@link Consumer} with the given
     * consumerUuid.
     *
     * @param consumerUuid requested consumer
     * @return created principal
     */
    public ConsumerPrincipal createPrincipal(final String consumerUuid) {
        if (consumerUuid == null) {
            return null;
        }

        final Consumer consumer = this.consumerCurator.getConsumer(consumerUuid);
        if (consumer == null) {
            if (wasDeleted(consumerUuid)) {
                throw new GoneException(i18nProvider.get()
                    .tr("Unit {0} has been deleted", consumerUuid), consumerUuid);
            }
            return null;
        }

        final Owner owner = this.ownerCurator.findOwnerById(consumer.getOwnerId());
        final ConsumerPrincipal principal = new ConsumerPrincipal(consumer, owner);
        log.debug("principal created for consumer {}", principal.getConsumer().getUuid());
        return principal;
    }

    private boolean wasDeleted(final String consumerUuid) {
        return deletedConsumerCurator.countByConsumerUuid(consumerUuid) > 0;
    }

}
