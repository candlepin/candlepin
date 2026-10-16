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
package org.candlepin.hostedtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import org.candlepin.dto.ModelTranslator;
import org.candlepin.service.UniqueIdGenerator;
import org.candlepin.service.exception.product.ProductServiceException;
import org.candlepin.service.exception.subscription.SubscriptionServiceException;
import org.candlepin.testext.hostedtest.HostedTestDataStore;
import org.candlepin.testext.hostedtest.HostedTestResource;
import org.candlepin.util.ObjectMapperFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class HostedTestResourceTest {
    private HostedTestResource resource;

    @BeforeEach
    void setUp() {
        this.resource = new HostedTestResource(new HostedTestDataStore(), mock(UniqueIdGenerator.class),
            mock(ModelTranslator.class), ObjectMapperFactory.getObjectMapper());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"123", "invalid product id"})
    void shouldPropagateProductServiceFailure(String productId) {
        ProductServiceException failure = assertThrows(ProductServiceException.class,
            () -> this.resource.simulateProductServiceFailure(productId));

        assertThat(failure.getProductId()).isEqualTo(productId);
        assertThat(failure.getCause())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("simulated product backend failure");
    }

    @Test
    void shouldPropagateSubscriptionServiceFailure() {
        SubscriptionServiceException failure = assertThrows(SubscriptionServiceException.class,
            () -> this.resource.simulateSubscriptionServiceFailure());

        assertThat(failure).hasMessage("simulated subscription backend failure");
    }
}
