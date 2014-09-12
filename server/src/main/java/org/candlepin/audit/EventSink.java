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
package org.candlepin.audit;

import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Rules;
import org.candlepin.model.Subscription;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.policy.js.compliance.ComplianceStatus;

import java.util.Set;

/**
 * EventSink
 */
public interface EventSink {

    void queueEvent(Event event);

    void sendEvents();

    void emitConsumerCreated(Consumer newConsumer);

    void emitOwnerCreated(Owner newOwner);

    void emitOwnerMigrated(Owner owner);

    void emitPoolCreated(Pool newPool);

    void emitExportCreated(Consumer consumer);

    void emitImportCreated(Owner owner);

    void emitSubscriptionCreated(Subscription subscription);

    void emitSubscriptionModified(Subscription old, Subscription newSub);

    void emitActivationKeyCreated(ActivationKey key);

    void emitRulesModified(Rules oldRules, Rules newRules);

    void emitRulesDeleted(Rules rules);

    void emitCompliance(Consumer consumer, Set<Entitlement> entitlements, ComplianceStatus compliance);

    Event createSubscriptionDeleted(Subscription todelete);
}
