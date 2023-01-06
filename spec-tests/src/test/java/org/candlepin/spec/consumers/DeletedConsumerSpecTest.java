/**
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
import org.junit.jupiter.api.Test;

import java.util.List;

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
        String fromDate = DateUtil.yesterday().toString();
        ConsumerDTO consumer = this.userClient.consumers().createConsumer(Consumers.random(this.owner));
        admin.consumers().deleteConsumer(consumer.getUuid());

        List<DeletedConsumerDTO> deletedConsumers = admin.deletedConsumers()
            .listByDate(fromDate);

        assertThat(deletedConsumers)
            .hasSizeGreaterThanOrEqualTo(1)
            .map(DeletedConsumerDTO::getConsumerUuid)
            .contains(consumer.getUuid());
    }

}
