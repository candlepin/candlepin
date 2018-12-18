/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.Locale;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;

import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.controller.PoolManager;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Pool;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;



/**
 * SubscriptionResourceTest
 */
@RunWith(MockitoJUnitRunner.class)
public class SubscriptionResourceTest  {
    private SubscriptionResource subResource;

    @Mock private SubscriptionServiceAdapter subService;
    @Mock private ConsumerCurator consumerCurator;
    @Mock private PoolManager poolManager;

    @Mock private HttpServletResponse response;

    @Before
    public void setUp() {
        I18n i18n = I18nFactory.getI18n(
            getClass(),
            Locale.US,
            I18nFactory.READ_PROPERTIES | I18nFactory.FALLBACK
        );

        this.subResource = new SubscriptionResource(subService, consumerCurator, poolManager, i18n);
    }

    @Test(expected = NotFoundException.class)
    public void testInvalidIdOnDelete() throws Exception {
        CandlepinQuery<Pool> cqmock = mock(CandlepinQuery.class);
        when(cqmock.list()).thenReturn(Collections.<Pool>emptyList());
        when(cqmock.iterator()).thenReturn(Collections.<Pool>emptyList().iterator());
        when(poolManager.getPoolsBySubscriptionId(anyString())).thenReturn(cqmock);

        subResource.deleteSubscription("JarJarBinks");
    }

    @Test(expected = BadRequestException.class)
    public void activateNoEmail() {
        subResource.activateSubscription("random", null, "en_us");
    }

    @Test(expected = BadRequestException.class)
    public void activateNoEmailLocale() {
        subResource.activateSubscription("random", "random@somthing.com", null);
    }

    @Test(expected = BadRequestException.class)
    public void activateBadConsumer() {
        subResource.activateSubscription("test_consumer", "email@whatever.net", "en_us");
    }

    @Test
    public void activateSubServiceCalled() {
        Consumer consumer = new Consumer("test_consumer", "alf", null, null);
        when(consumerCurator.findByUuid("ae843603bdc73")).thenReturn(consumer);

        subResource.activateSubscription("ae843603bdc73", "alf@alfnet.com", "en");

        verify(subService).activateSubscription(consumer, "alf@alfnet.com", "en");
    }

    @Test
    public void activateCorrectResponseCode() {
        Consumer consumer = new Consumer("test_consumer", "alf", null, null);
        when(consumerCurator.findByUuid("ae843603bdc73")).thenReturn(consumer);

        Response result = subResource.activateSubscription("ae843603bdc73", "alf@alfnet.com", "en");

        assertEquals(result.getStatus(), 202);
    }

}
