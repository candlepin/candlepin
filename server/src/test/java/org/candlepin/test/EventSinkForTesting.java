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
package org.candlepin.test;

import org.candlepin.audit.Event;
import org.candlepin.audit.EventSink;
import org.candlepin.model.Consumer;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Rules;
import org.candlepin.model.Subscription;
import org.candlepin.model.activationkeys.ActivationKey;

/**
 * EventSinkForTesting, a no-op class as we don't need hornetq at all.
 */
public class EventSinkForTesting implements EventSink {

    @Override
    public void emitConsumerCreated(Consumer newConsumer) {
    }

    @Override
    public void emitOwnerCreated(Owner newOwner) {
    }

    @Override
    public void emitOwnerMigrated(Owner newOwner) {
    }

    @Override
    public void sendEvent(Event event) {
    }

    @Override
    public void emitPoolCreated(Pool newPool) {
    }

    @Override
    public void emitExportCreated(Consumer consumer) {
    }

    @Override
    public void emitImportCreated(Owner owner) {
    }

    @Override
    public void emitSubscriptionCreated(Subscription subscription) {
    }

    @Override
    public Event createSubscriptionDeleted(Subscription todelete) {
        return null;
    }

    @Override
    public void emitSubscriptionModified(Subscription old, Subscription newSub) {
    }

    @Override
    public void emitActivationKeyCreated(ActivationKey key) {
    }

    @Override
    public void emitRulesModified(Rules oldRules, Rules newRules) {
    }

    @Override
    public void emitRulesDeleted(Rules rules) {
    }
}
