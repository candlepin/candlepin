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
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertBadRequest;

import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.UserDTO;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.ConsumerTypes;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SpecTest
public class PersonConsumerSpecTest {

    private static ApiClient admin;
    private OwnerDTO owner;
    private UserDTO user;
    private ApiClient userClient;

    @BeforeAll
    public static void beforeAll() {
        admin = ApiClients.admin();
    }

    @BeforeEach
    public void setUp() {
        this.owner = admin.owners().createOwner(Owners.random());
        this.user = UserUtil.createUser(admin, this.owner);
        this.userClient = ApiClients.basic(this.user);
    }

    @Test
    public void shouldCreatePersonConsumer() {
        ConsumerDTO consumer = this.userClient.consumers()
            .createConsumer(Consumers.random(this.owner, ConsumerTypes.Person));

        assertThat(consumer)
            .extracting(ConsumerDTO::getName)
            .isEqualTo(this.user.getUsername());
    }

    @Test
    public void shouldAllowOnlyOnePersonConsumerPerUser() {
        this.userClient.consumers()
            .createConsumer(Consumers.random(this.owner, ConsumerTypes.Person));

        assertBadRequest(() -> this.userClient.consumers()
            .createConsumer(Consumers.random(this.owner, ConsumerTypes.Person)));
    }

}
