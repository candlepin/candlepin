/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import static java.lang.Thread.sleep;
import static org.assertj.core.api.Assertions.assertThat;

import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.DeletedConsumerDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.UserDTO;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.util.DateUtil;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.IntStream;

@SpecTest
public class DeletedConsumerSpecTest {

    private static ApiClient admin;
    private OwnerDTO owner;
    private ApiClient userClient;

    @BeforeAll
    public static void beforeAll() {
        admin = ApiClients.admin();
    }

    @BeforeEach
    public void setUp() {
        this.owner = admin.owners().createOwner(Owners.random());
        UserDTO user = UserUtil.createUser(admin, this.owner);
        this.userClient = ApiClients.basic(user);
    }

    @Test
    public void shouldFindAllDeletedConsumers() {
        OffsetDateTime fromDate = DateUtil.yesterday();
        ConsumerDTO consumer = this.userClient.consumers().createConsumer(Consumers.random(this.owner));
        admin.consumers().deleteConsumer(consumer.getUuid());

        List<DeletedConsumerDTO> deletedConsumers = admin.deletedConsumers()
            .listByDate(fromDate, 1, 50, "desc", "created");

        assertThat(deletedConsumers)
            .hasSizeGreaterThanOrEqualTo(1)
            .map(DeletedConsumerDTO::getConsumerUuid)
            .contains(consumer.getUuid());
    }

    @Nested
    @Isolated
    @Execution(ExecutionMode.SAME_THREAD)
    class GetPages {
        @Test
        public void shouldFindPageOfDeletedConsumers() {
            OffsetDateTime fromDate = OffsetDateTime.now();

            // Ensure that there is minimum data for this test.
            // Most likely, there will already be data left from other test runs that will show up
            IntStream.range(0, 5).forEach(entry -> {
                ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));
                admin.consumers().deleteConsumer(consumer.getUuid());
                // for timestamp separation
                try {
                    sleep(1000);
                }
                catch (InterruptedException ie) {
                    throw new RuntimeException("Unable to sleep as expected");
                }
            });

            List<DeletedConsumerDTO> deletedConsumers = admin.deletedConsumers()
                .listByDate(fromDate, 1, 4, "asc", "created");
            assertThat(deletedConsumers)
                .isNotNull()
                .hasSize(4);
            assertThat(deletedConsumers.get(0).getCreated().compareTo(deletedConsumers.get(1).getCreated()))
                .isLessThanOrEqualTo(0);
            assertThat(deletedConsumers.get(1).getCreated().compareTo(deletedConsumers.get(2).getCreated()))
                .isLessThanOrEqualTo(0);
            assertThat(deletedConsumers.get(2).getCreated().compareTo(deletedConsumers.get(3).getCreated()))
                .isLessThanOrEqualTo(0);

        }
    }

}
