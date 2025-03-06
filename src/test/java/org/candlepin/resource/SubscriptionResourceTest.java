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
package org.candlepin.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.controller.ContentAccessManager;
import org.candlepin.controller.PoolManager;
import org.candlepin.controller.PoolService;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.exceptions.ServiceUnavailableException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.service.SubscriptionServiceAdapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import javax.ws.rs.core.Response;



@ExtendWith(MockitoExtension.class)
public class SubscriptionResourceTest {
    @Mock
    private SubscriptionServiceAdapter subService;
    @Mock
    private ConsumerCurator consumerCurator;
    @Mock
    private PoolManager poolManager;
    @Mock
    private PoolService poolService;
    @Mock
    private ModelTranslator modelTranslator;
    @Mock
    private ContentAccessManager contentAccessManager;

    private DevConfig config;
    private SubscriptionResource subResource;

    @BeforeEach
    public void setUp() {
        this.config = TestConfig.defaults();

        I18n i18n = I18nFactory.getI18n(this.getClass(), Locale.US,
            I18nFactory.READ_PROPERTIES | I18nFactory.FALLBACK);

        this.subResource = new SubscriptionResource(this.config, this.subService, this.consumerCurator,
            this.poolManager, i18n, this.modelTranslator, this.contentAccessManager, this.poolService);
    }

    @Test
    public void testDeleteSubscription() {
        Owner owner = mock(Owner.class);

        Pool pool1 = new Pool()
            .setId("pool-1-id")
            .setOwner(owner);

        Pool pool2 = new Pool()
            .setId("pool-2-id")
            .setOwner(owner);

        String subscriptionId = "sub-id";
        doReturn(List.of(pool1, pool2))
            .when(poolManager)
            .getPoolsBySubscriptionId(subscriptionId);

        subResource.deleteSubscription(subscriptionId);

        verify(poolService).deletePool(pool1);
        verify(poolService).deletePool(pool2);
        verify(owner).syncLastContentUpdate();
    }

    @Test
    public void testInvalidIdOnDelete() {
        when(poolManager.getPoolsBySubscriptionId(anyString())).thenReturn(Collections.emptyList());

        assertThrows(NotFoundException.class,
            () -> subResource.deleteSubscription("JarJarBinks"));
    }

    @Test
    public void activateNoEmail() {
        this.config.setProperty(ConfigProperties.STANDALONE, "false");

        assertThrows(BadRequestException.class,
            () -> subResource.activateSubscription("random", null, "en_us"));
    }

    @Test
    public void activateNoEmailLocale() {
        this.config.setProperty(ConfigProperties.STANDALONE, "false");

        assertThrows(BadRequestException.class,
            () -> subResource.activateSubscription("random", "random@somthing.com", null));
    }

    @Test
    public void activateBadConsumer() {
        this.config.setProperty(ConfigProperties.STANDALONE, "false");

        assertThrows(BadRequestException.class,
            () -> subResource.activateSubscription("test_consumer", "email@whatever.net", "en_us"));
    }

    @Test
    public void activateSubServiceCalled() {
        this.config.setProperty(ConfigProperties.STANDALONE, "false");

        Consumer consumer = new Consumer()
            .setName("test_consumer")
            .setUsername("alf");

        when(consumerCurator.findByUuid("ae843603bdc73")).thenReturn(consumer);

        subResource.activateSubscription("ae843603bdc73", "alf@alfnet.com", "en");

        verify(subService).activateSubscription(consumer, "alf@alfnet.com", "en");
    }

    @Test
    public void activateCorrectResponseCode() {
        this.config.setProperty(ConfigProperties.STANDALONE, "false");

        Consumer consumer = new Consumer()
            .setName("test_consumer")
            .setUsername("alf");

        when(consumerCurator.findByUuid("ae843603bdc73")).thenReturn(consumer);

        Response result = subResource.activateSubscription("ae843603bdc73", "alf@alfnet.com", "en");

        assertEquals(result.getStatus(), 202);
    }

    @Test
    public void testActivateSubscriptionRequiresHostedMode() {
        this.config.setProperty(ConfigProperties.STANDALONE, "true");

        assertThrows(ServiceUnavailableException.class,
            () -> this.subResource.activateSubscription("uuid", "consumer@email.com", "consumer_locale"));
    }

}
