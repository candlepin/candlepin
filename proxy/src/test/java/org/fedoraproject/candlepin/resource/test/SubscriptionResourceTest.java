/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.resource.test;

import javax.servlet.http.HttpServletResponse;
import org.fedoraproject.candlepin.model.Consumer;
import java.util.Locale;
import org.xnap.commons.i18n.I18nFactory;
import org.fedoraproject.candlepin.exceptions.BadRequestException;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.fedoraproject.candlepin.model.SubscriptionCurator;
import org.fedoraproject.candlepin.resource.SubscriptionResource;
import org.fedoraproject.candlepin.service.SubscriptionServiceAdapter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.xnap.commons.i18n.I18n;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

/**
 * SubscriptionResourceTest
 */
@RunWith(MockitoJUnitRunner.class)
public class SubscriptionResourceTest  {
    private SubscriptionResource subResource;

    @Mock private SubscriptionCurator subCurator;
    @Mock private SubscriptionServiceAdapter subService;
    @Mock private ConsumerCurator consumerCurator;
    @Mock private HttpServletResponse response;

    @Before
    public void setUp() {
        I18n i18n = I18nFactory.getI18n(
            getClass(),
            Locale.US,
            I18nFactory.READ_PROPERTIES | I18nFactory.FALLBACK
        );

        this.subResource = new SubscriptionResource(subCurator, subService,
                consumerCurator, i18n);
    }

    @Test(expected = BadRequestException.class)
    public void testInvalidIdOnDelete() throws Exception {
        subResource.deleteSubscription("JarJarBinks");
    }

    @Test(expected = BadRequestException.class)
    public void activateNoEmail() {
        subResource.activateSubscription("random", null, "en_us", null);
    }

    @Test(expected = BadRequestException.class)
    public void activateNoEmailLocale() {
        subResource.activateSubscription("random", "random@somthing.com",
                null, null);
    }

    @Test(expected = BadRequestException.class)
    public void activateBadConsumer() {
        subResource.activateSubscription("test_consumer", "email@whatever.net",
                "en_us", null);
    }

    @Test
    public void activateSubServiceCalled() {
        Consumer consumer = new Consumer("test_consumer", "alf", null, null);
        when(consumerCurator.findByUuid("ae843603bdc73")).thenReturn(consumer);

        subResource.activateSubscription("ae843603bdc73", "alf@alfnet.com",
                "en", this.response);

        verify(subService).activateSubscription(consumer, "alf@alfnet.com", "en");
    }

    @Test
    public void activateCorrectResponseCode() {
        Consumer consumer = new Consumer("test_consumer", "alf", null, null);
        when(consumerCurator.findByUuid("ae843603bdc73")).thenReturn(consumer);

        subResource.activateSubscription("ae843603bdc73", "alf@alfnet.com",
                "en", this.response);

        verify(response).setStatus(202);
    }

}
