/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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
package org.candlepin.spec;

import static org.assertj.core.api.Assertions.assertThat;

import org.candlepin.spec.bootstrap.assertions.OnlyInHosted;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.request.Request;
import org.candlepin.spec.bootstrap.client.request.Response;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import org.junit.jupiter.api.Test;

/**
 * Legacy HTTP contracts for uncaught service failures from hosted extension endpoints
 */
@SpecTest
@OnlyInHosted
class HostedServiceExceptionSpecTest {

    @Test
    void shouldTranslateUncaughtProductServiceFailure() {
        String productId = StringUtil.random("product-");
        Response response = Request.from(ApiClients.admin())
            .setPath("/hostedtest/service-exceptions/products/{product_id}")
            .setPathParam("product_id", productId)
            .addHeader("Accept", "application/json")
            .execute();

        assertThat(response.getCode())
            .as("Product service failure response: %s", response.getBodyAsString())
            .isEqualTo(400);
        assertThat(response.deserialize().get("displayMessage").asString())
            .contains("Unable to retrieve product \"" + productId + "\" from ProductService")
            .doesNotContain("simulated product backend failure");
    }

    @Test
    void shouldTranslateUncaughtSubscriptionServiceFailure() {
        Response response = Request.from(ApiClients.admin())
            .setPath("/hostedtest/service-exceptions/subscriptions")
            .addHeader("Accept", "application/json")
            .execute();

        assertThat(response.getCode())
            .as("Subscription service failure response: %s", response.getBodyAsString())
            .isEqualTo(417);
        assertThat(response.deserialize().get("displayMessage").asString())
            .contains("Unexpected error from Subscription Service:");
    }
}
