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
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.dto.Subscription;
import org.candlepin.policy.js.compliance.ComplianceStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

import javax.inject.Inject;

/**
 * NoopEventSinkImpl
 * - Used if configuration candlepin.audit.hornetq.enable = false
 */
public class NoopEventSinkImpl implements EventSink {

    private static Logger log = LoggerFactory.getLogger(NoopEventSinkImpl.class);

    /*
     * needed cause guice needs public scope, default scope is package scope
     */
    @Inject
    public NoopEventSinkImpl() {
        log.debug("NoopEventSinkImpl initialized!");
    }

    @Override
    public void queueEvent(Event event) {
        log.debug("event enqueued, but performing noop:" + event);
    }

    @Override
    public void sendEvents() {
    }

    @Override
    public void emitConsumerCreated(Consumer newConsumer) {
        log.debug("emitConsumerCreated:" + newConsumer);
    }

    public void rollback() {
    }

    @Override
    public void emitOwnerCreated(Owner newOwner) {
        log.debug("emitOwnerCreated:" + newOwner);
    }

    @Override
    public void emitOwnerMigrated(Owner owner) {
        log.debug("emitOwnerMigrated:" + owner);
    }

    @Override
    public void emitPoolCreated(Pool newPool) {
        log.debug("emitPoolCreated:" + newPool);
    }

    @Override
    public void emitExportCreated(Consumer consumer) {
        log.debug("emitExportCreated:" + consumer);
    }

    @Override
    public void emitImportCreated(Owner owner) {
        log.debug("emitImportCreated:" + owner);
    }

    @Override
    public void emitActivationKeyCreated(ActivationKey key) {
        log.debug("emitActivationKeyCreated:" + key);
    }

    @Override
    public void emitSubscriptionExpired(Subscription subscription) {
        log.debug("emitSubscriptionExpired:" + subscription);
    }

    @Override
    public void emitRulesModified(Rules oldRules, Rules newRules) {
        log.debug("emitRulesModified: oldRules:" + oldRules + " newRules:" + oldRules);
    }

    @Override
    public void emitRulesDeleted(Rules rules) {
        log.debug("emitRulesDeleted:" + rules);
    }

    @Override
    public void emitCompliance(Consumer consumer,
            Set<Entitlement> entitlements, ComplianceStatus compliance) {
        log.debug("emitCompliance: entitlements:" + entitlements + " ComplianceStatus:" + compliance);
    }

    @Override
    public void initialize() throws Exception {

    }

    @Override
    public List<QueueStatus> getQueueInfo() {
        return null;
    }

}
