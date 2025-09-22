/*
 * Copyright (c) 2009 - 2025 Red Hat, Inc.
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

package org.candlepin.controller;

import org.candlepin.audit.Event;
import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.model.CertSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ContentAccessCertificateCurator;
import org.candlepin.model.IdentityCertificateCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.util.Transactional;

import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.List;
import java.util.Objects;

import javax.inject.Inject;

/**
 * Class responsible for migration of consumers from anonymous owners.
 */
public class ConsumerMigration {

    private static final Logger log = LoggerFactory.getLogger(ConsumerMigration.class);

    private final OwnerCurator ownerCurator;
    private final ConsumerCurator consumerCurator;
    private final IdentityCertificateCurator identityCertificateCurator;
    private final ContentAccessCertificateCurator contentAccessCertificateCurator;
    private final CertificateSerialCurator serialCurator;
    private final EventFactory eventFactory;
    private final EventSink eventSink;
    private final I18n i18n;
    private final int batchSize;

    @Inject
    public ConsumerMigration(OwnerCurator ownerCurator, ConsumerCurator consumerCurator,
        IdentityCertificateCurator identityCertificateCurator,
        ContentAccessCertificateCurator contentAccessCertificateCurator,
        CertificateSerialCurator serialCurator, EventFactory eventFactory, EventSink eventSink, I18n i18n,
        Configuration config) {
        this.ownerCurator = Objects.requireNonNull(ownerCurator);
        this.consumerCurator = Objects.requireNonNull(consumerCurator);
        this.identityCertificateCurator = Objects.requireNonNull(identityCertificateCurator);
        this.contentAccessCertificateCurator = Objects.requireNonNull(contentAccessCertificateCurator);
        this.serialCurator = Objects.requireNonNull(serialCurator);
        this.eventFactory = Objects.requireNonNull(eventFactory);
        this.eventSink = Objects.requireNonNull(eventSink);
        this.i18n = Objects.requireNonNull(i18n);
        this.batchSize = config.getInt(ConfigProperties.CONSUMER_MIGRATION_BATCH_SIZE);
    }

    /**
     * Migrates all consumers of the given anonymous owner to the target owner.
     *
     * @param originOwnerKey
     *  key of the source anonymous owner
     *
     * @param destinationOwnerKey
     *  key of the destination owner
     *
     * @throws ConsumerMigrationFailedException
     *  if there is a failure to migrate a batch of consumers
     */
    public void migrate(String originOwnerKey, String destinationOwnerKey) {
        Owner originOwner = this.ownerCurator.getByKey(originOwnerKey);
        Owner destinationOwner = this.ownerCurator.getByKey(destinationOwnerKey);
        List<String> consumerUuids = this.ownerCurator.getConsumerUuids(originOwner);

        Transactional transaction = this.ownerCurator.transactional()
            .onCommit(status -> this.eventSink.sendEvents())
            .onRollback(status -> this.eventSink.rollback());

        boolean failed = false;
        for (List<String> consumerUuidsBlock : Iterables.partition(consumerUuids, this.batchSize)) {
            try {
                transaction.execute(() -> {
                    this.migrateBatch(consumerUuidsBlock, destinationOwner);

                    Event event = this.eventFactory
                        .bulkConsumerMigration(consumerUuidsBlock, originOwner, destinationOwner);

                    this.eventSink.queueEvent(event);
                });
            }
            catch (Exception e) {
                log.warn("Failed to migrate a batch of consumers.", e);
                failed = true;
            }
        }

        if (failed) {
            throw new ConsumerMigrationFailedException(i18n.tr("Failed to migrate consumers"));
        }
    }

    private void migrateBatch(List<String> consumerUuids, Owner destination) {
        this.consumerCurator.lockAndLoadUuids(consumerUuids);

        List<CertSerial> idCertSerials = this.identityCertificateCurator.listCertSerials(consumerUuids);
        List<CertSerial> caCertSerials = this.contentAccessCertificateCurator.listCertSerials(consumerUuids);
        List<String> idCerts = idCertSerials.stream().map(CertSerial::certId).toList();
        List<String> caCerts = caCertSerials.stream().map(CertSerial::certId).toList();
        List<Long> serials = Streams.concat(idCertSerials.stream(), caCertSerials.stream())
            .map(CertSerial::serial).toList();

        this.consumerCurator.unlinkIdCertificates(idCerts);
        this.consumerCurator.unlinkCaCertificates(caCerts);
        this.identityCertificateCurator.deleteByIds(idCerts);
        this.contentAccessCertificateCurator.deleteByIds(caCerts);
        this.serialCurator.revokeByIds(serials);

        this.consumerCurator.bulkUpdateOwner(consumerUuids, destination);
    }

}
