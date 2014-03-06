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

import java.util.List;

import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.auth.Access;
import org.candlepin.auth.Principal;
import org.candlepin.auth.SubResource;
import org.candlepin.auth.interceptor.Verify;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.ForbiddenException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.GuestId;
import org.candlepin.model.GuestIdCurator;
import org.candlepin.paging.Page;
import org.candlepin.paging.PageRequest;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;

/**
 * API Gateway for registered consumers guests
 */
@Path("/consumers/{consumer_uuid}/guestids")
public class GuestIdResource {

    private static Logger log = LoggerFactory.getLogger(GuestIdResource.class);

    private GuestIdCurator guestIdCurator;
    private ConsumerCurator consumerCurator;
    private ConsumerResource consumerResource;
    private I18n i18n;
    private EventSink sink;
    private EventFactory eventFactory;

    @Inject
    public GuestIdResource(GuestIdCurator guestIdCurator,
            ConsumerCurator consumerCurator, ConsumerResource consumerResource,
            I18n i18n, EventFactory eventFactory, EventSink sink) {
        this.guestIdCurator = guestIdCurator;
        this.consumerCurator = consumerCurator;
        this.consumerResource = consumerResource;
        this.i18n = i18n;
        this.eventFactory = eventFactory;
        this.sink = sink;
    }

    /**
     * List all of a Consumers Guests
     *
     * @return list of all of a Consumers Guests
     * @httpcode 400
     * @httpcode 404
     * @httpcode 200
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<GuestId> getGuestIds(
            @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid,
            @Context PageRequest pageRequest) {
        Consumer consumer = consumerCurator.findByUuid(consumerUuid);
        Page<List<GuestId>> page = guestIdCurator.listByConsumer(consumer, pageRequest);

        // Store the page for the LinkHeaderPostInterceptor
        ResteasyProviderFactory.pushContext(Page.class, page);
        List<GuestId> result = page.getPageData();
        return result;
    }

    /**
     * Look up a single guest by its consumer and guestuuid
     *
     * @param consumerUuid consumer who owns or hosts the guest in question
     * @param guestId guest virtual uuid
     * @return data on a single guest
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{guest_id}")
    public GuestId getGuestId(
            @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid,
            @PathParam("guest_id") String guestId) {
        Consumer consumer = consumerCurator.findByUuid(consumerUuid);
        GuestId result = validateGuestId(
            guestIdCurator.findByConsumerAndId(consumer, guestId), guestId);
        return result;
    }

    /**
     * This method should work just like updating the consumer, except that it only
     * updates GuestIds.  Eventually we should move All the logic here, and depricate
     * updating guests through the consumer update.
     *
     * @param consumerUuid Id of consumer who owns the guests
     * @param guestIds List of guests from which to update the consumer
     */
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    public void updateGuests(
            @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid,
            List<GuestId> guestIds) {
        Consumer toUpdate = consumerCurator.findByUuid(consumerUuid);

        // Create a skeleton consumer for consumerResource.performConsumerUpdates
        Consumer consumer = new Consumer();
        consumer.setGuestIds(guestIds);

        if (consumerResource.performConsumerUpdates(consumer, toUpdate)) {
            consumerCurator.update(toUpdate);
        }
    }

    /**
     * Update a single guest with new data and attributes.  Allows virt-who
     * to avoid uploading an entire list of guests
     *
     * @param consumerUuid consumer who owns or hosts the guest in question
     * @param guestId guest virtual uuid
     * @param updated updated guest data to use
     */
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{guest_id}")
    public void updateGuest(
            @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid,
            @PathParam("guest_id") String guestId,
            GuestId updated) {

        // I'm not sure this can happen
        if (guestId == null || guestId.isEmpty()) {
            throw new BadRequestException(
                i18n.tr("Please supply a valid guest id"));
        }

        if (updated == null) {
            // If they're not sending attributes, we can get the guestId from the url
            updated = new GuestId(guestId);
        }

        // Allow the id to be left out in this case, we can use the path param
        if (updated.getGuestId() == null) {
            updated.setGuestId(guestId);
        }

        // If the guest uuids do not match, something is wrong
        if (!guestId.equalsIgnoreCase(updated.getGuestId())) {
            throw new BadRequestException(
                i18n.tr("Guest ID in json \"{0}\" does not match path guest ID \"{1}\"",
                    updated.getGuestId(), guestId));
        }

        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        updated.setConsumer(consumer);
        GuestId toUpdate =
            guestIdCurator.findByGuestIdAndOrg(guestId, consumer.getOwner());
        // If this guest has a consumer, we want to remove host-specific entitlements
        if (toUpdate != null) {
            revokeBadHostRestrictedEnts(toUpdate, consumer);
            updated.setId(toUpdate.getId());
        }
        guestIdCurator.merge(updated);
    }

    /**
     * Delete the Guest
     *
     * @param consumerUuid consumer who owns or hosts the guest in question
     * @param guestId guest virtual uuid
     * @param unregister Optionally unregister the guests consumer if it exists
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{guest_id}")
    public void deleteGuest(
            @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid,
            @PathParam("guest_id") String guestId,
            @QueryParam("unregister") @DefaultValue("false") boolean unregister,
            @Context Principal principal) {

        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        GuestId toDelete = validateGuestId(
            guestIdCurator.findByConsumerAndId(consumer, guestId), guestId);

        if (unregister) {
            unregisterConsumer(toDelete, principal);
        }

        sink.sendEvent(eventFactory.guestIdDeleted(consumer, toDelete));
        guestIdCurator.delete(toDelete);
    }

    private GuestId validateGuestId(GuestId guest, String guestUuid) {
        if (guest == null) {
            throw new NotFoundException(i18n.tr(
                "Guest with uuid {0} could not be found.", guestUuid));
        }
        return guest;
    }

    private void revokeBadHostRestrictedEnts(GuestId toUpdate, Consumer newHost) {
        // If there is a registered consumer on this guest
        // we should revoke host specific entitlements
        Consumer guestConsumer = consumerCurator.findByVirtUuid(toUpdate.getGuestId(),
            toUpdate.getConsumer().getOwner().getId());
        if (guestConsumer != null && !guestConsumer.equals(newHost)) {
            // new Consumer has no uuid because we want to
            // remove all host limited subscriptions
            consumerResource.revokeGuestEntitlementsNotMatchingHost(newHost,
                guestConsumer);
        }
    }

    private void unregisterConsumer(GuestId guest, Principal principal) {
        Consumer guestConsumer = consumerCurator.findByVirtUuid(guest.getGuestId(),
            guest.getConsumer().getOwner().getId());
        if (guestConsumer != null) {
            if ((principal == null) ||
                    principal.canAccess(guestConsumer, SubResource.NONE, Access.ALL)) {
                consumerResource.deleteConsumer(guestConsumer.getUuid(), principal);
            }
            else {
                throw new ForbiddenException(i18n.tr(
                    "Cannot unregister {0} {1} because: {2}",
                    guestConsumer.getType().getLabel(), guestConsumer.getName(),
                    i18n.tr("Invalid Credentials")));
            }
        }
    }
}
