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
package org.candlepin.gutterball.eventhandler;

import org.candlepin.gutterball.curator.ConsumerCurator;
import org.candlepin.gutterball.model.ProcessedEvent;

import com.google.inject.Inject;

/**
 * ConsumerHandler to properly update the database when
 * a consumer based event is received
 */
@HandlerTarget("CONSUMER")
public class ConsumerHandler extends AbstractBaseHandler<ProcessedEvent, ConsumerCurator> {

    @Inject
    public ConsumerHandler(ConsumerCurator consumerCurator) {
        super(consumerCurator);
    }
}
