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

import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertThatStatus;

import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.invoker.client.ApiException;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.request.Request;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;

import org.junit.jupiter.api.Test;

@SpecTest
public class LocalizationSpecTest {

    @Test
    public void returnedTranslatedErrorMessage() {
        String expectedMessage = "Ungültige Berechtigungsnachweise";
        Request request = Request.from(ApiClients.basic("admin", "badpass"))
            .setMethod("POST")
            .addHeader("Accept-Language", "de-DE")
            .setPath("/consumers");
        assertThatStatus(request::execute)
            .isUnauthorized()
            .extracting(Throwable::getMessage)
            .matches(s -> s.contains(expectedMessage));
    }

    @Test
    public void returnedTranslatedMessageForDeletedConsumer() throws ApiException {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = createOwner(adminClient);
        ConsumerDTO consumer = createConsumer(adminClient, owner);
        String expectedMessage = "Einheit ".concat(consumer.getUuid()).concat(" wurde gelöscht");
        adminClient.consumers().deleteConsumer(consumer.getUuid());
        Request request = Request.from(adminClient)
            .addHeader("Accept-Language", "de-DE")
            .setPath("/consumers/{consumer_uuid}")
            .setPathParam("consumer_uuid", consumer.getUuid());
        assertThatStatus(request::execute)
            .isGone()
            .extracting(Throwable::getMessage)
            .matches(s -> s.contains(expectedMessage));
    }

    private static ConsumerDTO createConsumer(ApiClient client, OwnerDTO owner) throws ApiException {
        return client.consumers().createConsumer(
            Consumers.random(owner), null, owner.getKey(), null, null);
    }

    private static OwnerDTO createOwner(ApiClient client) throws ApiException {
        return client.owners().createOwner(Owners.random());
    }
}
