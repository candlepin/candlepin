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
package org.candlepin.auth;

import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.DeletedConsumerCurator;
import org.candlepin.model.OwnerCurator;

import com.google.inject.Inject;

import org.jboss.resteasy.spi.HttpRequest;
import org.xnap.commons.i18n.I18n;

import java.util.List;

import javax.inject.Provider;
/**
 * This auth form allows for a consumer id to
 * be passed in a clear http header. This should
 * be used only if the environment is known to be secure
 */
public class TrustedConsumerAuth extends ConsumerAuth {

    public static final String CONSUMER_HEADER = "cp-consumer";

    @Inject
    TrustedConsumerAuth(ConsumerCurator consumerCurator, OwnerCurator ownerCurator,
        DeletedConsumerCurator deletedConsumerCurator,
        Provider<I18n> i18nProvider) {
        super(consumerCurator, ownerCurator, deletedConsumerCurator, i18nProvider);
    }

    public Principal getPrincipal(HttpRequest httpRequest) {
        ConsumerPrincipal principal = null;

        List<String> header = httpRequest.getHttpHeaders().getRequestHeader(CONSUMER_HEADER);
        String consumerUUID = null;

        if (null != header && header.size() > 0) {
            consumerUUID = header.get(0);
        }

        if (consumerUUID != null) {
            principal = createPrincipal(consumerUUID);
        }

        return principal;
    }
}
