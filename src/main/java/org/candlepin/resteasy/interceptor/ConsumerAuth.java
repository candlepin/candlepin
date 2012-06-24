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
package org.candlepin.resteasy.interceptor;

import org.apache.log4j.Logger;
import org.candlepin.auth.ConsumerPrincipal;
import org.candlepin.exceptions.GoneException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.DeletedConsumerCurator;

import com.google.inject.Inject;

/**
 * ConsumerAuth
 */
public abstract class ConsumerAuth implements AuthProvider {

    private static Logger log = Logger.getLogger(ConsumerAuth.class);

    protected ConsumerCurator consumerCurator;
    protected DeletedConsumerCurator deletedConsumerCurator;

    @Inject
    ConsumerAuth(ConsumerCurator consumerCurator,
        DeletedConsumerCurator deletedConsumerCurator) {
        this.consumerCurator = consumerCurator;
        this.deletedConsumerCurator = deletedConsumerCurator;
    }

    public ConsumerPrincipal createPrincipal(String consumerUuid) {
        ConsumerPrincipal principal = null;

        if (consumerUuid != null) {
            // If this UUID has been deleted, return a 410.
            if (deletedConsumerCurator.countByConsumerUuid(consumerUuid) > 0) {
                log.debug("Key " + consumerUuid + " is deleted, throwing GoneException");
                throw new GoneException("Consumer " + consumerUuid +
                    " has been deleted", consumerUuid);
            }

            Consumer consumer = this.consumerCurator.getConsumer(consumerUuid);

            if (consumer != null) {
                principal = new ConsumerPrincipal(consumer);

                if (log.isDebugEnabled() && principal != null) {
                    log.debug("principal created for consumer '" +
                            principal.getConsumer().getUuid());
                }
            }
        }

        return principal;
    }

}
