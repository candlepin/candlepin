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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang.StringUtils;
import org.candlepin.auth.interceptor.Verify;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerContentOverride;
import org.candlepin.model.ConsumerContentOverrideCurator;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.policy.js.override.OverrideRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

/**
 * API Gateway for Consumers Content Overrides
 */
@Path("/consumers/{consumer_uuid}/content_overrides")
public class ConsumerContentOverrideResource {

    private static Logger log = LoggerFactory.getLogger(
        ConsumerContentOverrideResource.class);

    private ConsumerContentOverrideCurator consumerContentOverrideCurator;
    private ConsumerCurator consumerCurator;
    private OverrideRules overrideRules;
    private I18n i18n;

    @Inject
    public ConsumerContentOverrideResource(
            ConsumerContentOverrideCurator consumerContentOverrideCurator,
            ConsumerCurator consumerCurator,
            OverrideRules overrideRules,
            I18n i18n) {
        this.consumerContentOverrideCurator = consumerContentOverrideCurator;
        this.consumerCurator = consumerCurator;
        this.overrideRules = overrideRules;
        this.i18n = i18n;
    }

    /**
     * Add override for content set
     *
     * @param uuid
     *
     * @return list of active overrides
     * @httpcode 404
     * @httpcode 200
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public List<ConsumerContentOverride> addContentOverrides(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid,
        List<ConsumerContentOverride> entries) {
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        Set<String> invalidOverrides = new HashSet<String>();
        for (ConsumerContentOverride entry : entries) {
            if (overrideRules.canOverrideForConsumer(consumer, entry.getName())) {
                ConsumerContentOverride cco = consumerContentOverrideCurator.retrieve(
                    consumer, entry.getContentLabel(), entry.getName());
                // Make sure we aren't overflowing columns
                validateLength(entry);
                if (cco != null) {
                    cco.setValue(entry.getValue());
                    cco.setUpdated(null);
                    consumerContentOverrideCurator.merge(cco);
                }
                else {
                    entry.setConsumer(consumer);
                    consumerContentOverrideCurator.create(entry);
                }
            }
            else {
                invalidOverrides.add(entry.getName());
            }
        }
        if (!invalidOverrides.isEmpty()) {
            String error = i18n.tr("Not allowed to override values for: {0}",
                StringUtils.join(invalidOverrides, ", "));
            throw new BadRequestException(error);
        }
        return consumerContentOverrideCurator.getList(consumer);
    }

    /**
     * Remove override based on included criteria
     *
     * @param uuid
     *
     * @return list of active overrides
     * @httpcode 404
     * @httpcode 200
     */
    @DELETE
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public List<ConsumerContentOverride> deleteContentOverrides(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid,
        List<ConsumerContentOverride> entries) {

        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        if (entries.size() == 0) {
            consumerContentOverrideCurator.removeByConsumer(consumer);
        }
        else {
            for (ConsumerContentOverride entry : entries) {
                String label = entry.getContentLabel();
                if (StringUtils.isBlank(label)) {
                    consumerContentOverrideCurator.removeByConsumer(consumer);
                }
                else {
                    String name = entry.getName();
                    if (StringUtils.isBlank(name)) {
                        consumerContentOverrideCurator.removeByContentLabel(
                            consumer, entry.getContentLabel());
                    }
                    else {
                        consumerContentOverrideCurator.removeByName(consumer,
                            entry.getContentLabel(), name);
                    }
                }
            }
        }
        return consumerContentOverrideCurator.getList(consumer);
    }

    /**
     * Get the list of content set overrides for this consumer
     *
     * @param uuid
     *
     * @return list of active overrides
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<ConsumerContentOverride> getContentOverrideList(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid) {
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        return consumerContentOverrideCurator.getList(consumer);
    }

    /*
     * If the name/value is longer than 255 characters, the database will throw
     * exceptions.  There is no reason that we should need overrides with lengths
     * this long.
     *
     * TODO: Can we read the column name from the database?  That would be
     * a bit more futureproof.
     */
    private void validateLength(ConsumerContentOverride entry) {
        int colLength = 255;
        if (entry.getName().length() > colLength ||
                entry.getValue().length() > colLength) {
            throw new BadRequestException(i18n.tr(
                "Name and value of the override must not exceed {0} characters.",
                colLength));
        }
    }
}
