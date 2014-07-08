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
package org.canadianTenPin.resource;

import javax.ws.rs.Path;

import org.canadianTenPin.model.Consumer;
import org.canadianTenPin.model.ConsumerContentOverride;
import org.canadianTenPin.model.ConsumerContentOverrideCurator;
import org.canadianTenPin.model.ConsumerCurator;
import org.canadianTenPin.util.ContentOverrideValidator;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;

/**
 * API Gateway for Consumers Content Overrides
 */
@Path("/consumers/{consumer_uuid}/content_overrides")
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
