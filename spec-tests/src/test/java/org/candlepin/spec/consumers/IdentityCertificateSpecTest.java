/**
 * Copyright (c) 2009 - 2022 Red Hat, Inc.
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

package org.candlepin.spec.consumers;

import static org.assertj.core.api.Assertions.assertThat;

import org.candlepin.ApiException;
import org.candlepin.dto.api.v1.ConsumerDTO;
import org.candlepin.dto.api.v1.OwnerDTO;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.cert.X509Cert;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

@SpecTest
class IdentityCertificateSpecTest {

    private static OwnerDTO owner;
    private static ConsumerDTO consumer;
    private static X509Cert idCert;

    @BeforeAll
    static void beforeAll() throws ApiException {
        ApiClient client = ApiClients.admin();
        owner = client.owners().createOwner(Owners.random());
        consumer = client.consumers()
            .register(Consumers.random(owner));
        idCert = X509Cert.from(consumer.getIdCert());
    }

    @Test
    @DisplayName("should create identity certificate during registration")
    void shouldExistAfterRegistration() {
        assertThat(idCert).isNotNull();
    }

    @Test
    @DisplayName("should have the same CN and O as the consumer UUID & Owner Key")
    void shouldHaveCorrectCnAndO() {
        assertThat(idCert.subject())
            .contains("CN=" + consumer.getUuid())
            .contains("O=" + owner.getKey());
    }

    @Test
    @DisplayName("should contain consumer name and uuid in subject alternative names")
    void altNamesShouldContainConsumerNameAndUuid() {
        assertThat(idCert.subjectAltNames())
            .contains("CN=" + consumer.getName())
            .contains("CN=" + consumer.getUuid());
    }

    @Test
    @DisplayName("should be pre-dated")
    void shouldBePreDated() {
        LocalDateTime hourAgo = LocalDateTime.now().minus(Duration.ofMinutes(59));
        assertThat(idCert.notBefore()).isBefore(hourAgo);
    }

}
