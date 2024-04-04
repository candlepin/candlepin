/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.candlepin.config.ConfigProperties;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.Owner;
import org.candlepin.test.DatabaseTestFixture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

class ConsumerMigrationTest extends DatabaseTestFixture {

    @AfterEach
    void tearDown() {
        // Each test needs to close the transaction so that migration correctly
        // runs each batch of consumers in a separate transaction.
        this.beginTransaction();
    }

    @Test
    void shouldMigrateAll() {
        ConsumerMigration migration = new ConsumerMigration(this.ownerCurator, this.consumerCurator,
            this.identityCertificateCurator, this.caCertCurator, this.certSerialCurator,
            this.i18n, this.config);
        Owner anonOwner = this.ownerCurator.saveOrUpdate(this.createOwner().setAnonymous(true));
        Owner destOwner = this.createOwner();
        this.createConsumerWithCerts(anonOwner);
        this.createConsumerWithCerts(anonOwner);
        this.createConsumerWithCerts(anonOwner);
        this.commitTransaction();

        migration.migrate(anonOwner.getKey(), destOwner.getKey());

        assertThat(this.ownerCurator.getConsumerIds(destOwner)).hasSize(3);
        assertThat(this.ownerCurator.getConsumerIds(anonOwner)).isEmpty();
    }

    @Test
    void shouldSkipFailedBatches() {
        this.config.setProperty(ConfigProperties.CONSUMER_MIGRATION_BATCH_SIZE, "1");
        ConsumerCurator consumerCuratorSpy = spy(this.consumerCurator);
        when(consumerCuratorSpy.lockAndLoadIds(anyCollection()))
            .thenThrow(new IllegalArgumentException())
            .thenReturn(null);
        ConsumerMigration migration = new ConsumerMigration(this.ownerCurator, consumerCuratorSpy,
            this.identityCertificateCurator, this.caCertCurator, this.certSerialCurator,
            this.i18n, this.config);
        Owner anonOwner = this.ownerCurator.saveOrUpdate(this.createOwner().setAnonymous(true));
        Owner destOwner = this.createOwner();
        this.createConsumerWithCerts(anonOwner);
        this.createConsumerWithCerts(anonOwner);
        this.createConsumerWithCerts(anonOwner);
        this.commitTransaction();

        assertThatThrownBy(() -> migration.migrate(anonOwner.getKey(), destOwner.getKey()))
            .isInstanceOf(ConsumerMigrationFailedException.class);

        assertThat(this.ownerCurator.getConsumerIds(destOwner)).hasSize(2);
        assertThat(this.ownerCurator.getConsumerIds(anonOwner)).hasSize(1);
    }

    @Test
    void shouldRevokeConsumerCertificates() {
        ConsumerMigration migration = new ConsumerMigration(this.ownerCurator, this.consumerCurator,
            this.identityCertificateCurator, this.caCertCurator, this.certSerialCurator,
            this.i18n, this.config);
        Owner anonOwner = this.ownerCurator.saveOrUpdate(this.createOwner().setAnonymous(true));
        Owner destOwner = this.createOwner();
        this.createConsumerWithCerts(anonOwner);
        this.createConsumerWithCerts(anonOwner);
        this.createConsumerWithCerts(anonOwner);
        this.commitTransaction();

        migration.migrate(anonOwner.getKey(), destOwner.getKey());
        this.consumerCurator.flush();
        this.consumerCurator.clear();

        List<Consumer> migratedConsumers = this.consumerCurator.listAllByIds(
            this.ownerCurator.getConsumerIds(destOwner));
        assertThat(migratedConsumers)
            .allSatisfy(consumer -> assertThat(consumer)
                .returns(null, Consumer::getIdCert)
                .returns(null, Consumer::getContentAccessCert)
            );
    }

    private void createConsumerWithCerts(Owner anonOwner) {
        CertificateSerial serial = new CertificateSerial();
        this.certSerialCurator.saveOrUpdate(serial);
        IdentityCertificate identCert = new IdentityCertificate();
        identCert.setCert("test_cert");
        identCert.setKey("test_key");
        identCert.setSerial(serial);
        this.identityCertificateCurator.saveOrUpdate(identCert);
        Consumer consumer = this.createConsumer(anonOwner);
        consumer.setIdCert(identCert);
        this.consumerCurator.saveOrUpdate(consumer);
    }

}
