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

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerContentOverride;
import org.candlepin.model.ConsumerContentOverrideCurator;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.util.ContentOverrideValidator;

import com.google.inject.Inject;

import org.xnap.commons.i18n.I18n;

import javax.ws.rs.Path;

import io.swagger.annotations.Api;

/**
 * API Gateway for Consumers Content Overrides
 */
@Path("/consumers/{consumer_uuid}/content_overrides")
@Api("consumers")
public class ConsumerContentOverrideResource extends
    ContentOverrideResource<ConsumerContentOverride,
    ConsumerContentOverrideCurator, Consumer> {

    private ConsumerCurator consumerCurator;

    @Inject
    public ConsumerContentOverrideResource(
        ConsumerContentOverrideCurator consumerContentOverrideCurator,
        ConsumerCurator consumerCurator,
        ContentOverrideValidator contentOverrideValidator, I18n i18n) {
        super(consumerContentOverrideCurator, contentOverrideValidator,
            i18n, "consumer_uuid");
        this.consumerCurator = consumerCurator;
    }

    @Override
    protected Consumer findParentById(String parentId) {
        return this.consumerCurator.verifyAndLookupConsumer(parentId);
    }
}
