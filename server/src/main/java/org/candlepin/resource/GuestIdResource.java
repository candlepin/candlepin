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

import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.auth.Access;
import org.candlepin.auth.Principal;
import org.candlepin.auth.SubResource;
import org.candlepin.auth.Verify;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.ForbiddenException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.common.paging.Page;
import org.candlepin.common.paging.PageRequest;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.GuestId;
import org.candlepin.model.GuestIdCurator;
import org.candlepin.model.VirtConsumerMap;

import com.google.inject.Inject;

import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.ws.rs.Consumes;
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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * API Gateway for registered consumers guests
 */
@Path("/consumers/{consumer_uuid}/guestids")
@Api("consumers")
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

    @ApiOperation(notes = "Retrieves the List of a Consumer's Guests", value = "getGuestIds")
    @ApiResponses({ @ApiResponse(code = 400, message = ""), @ApiResponse(code = 404, message = "") })
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<GuestId> getGuestIds(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid,
        @Context PageRequest pageRequest) {
        Consumer consumer = consumerCurator.findByUuid(consumerUuid);
        Page<List<GuestId>> page = guestIdCurator.listByConsumer(consumer, pageRequest);

        // Store the page for the LinkHeaderResponseFilter
        ResteasyProviderFactory.pushContext(Page.class, page);
        List<GuestId> result = page.getPageData();
        return result;
    }

    @ApiOperation(notes = "Retrieves a single Guest By its consumer and the guest UUID", value = "getGuestId")
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

    @ApiOperation(notes = "Updates the List of Guests on a Consumer This method should work " +
        "just like updating the consumer, except that it only updates GuestIds. " +
        " Eventually we should move All the logic here, and depricate updating guests " +
        "through the consumer update.", value = "updateGuests")
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public void updateGuests(
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid,
        List<GuestId> guestIds) {
        Consumer toUpdate = consumerCurator.findByUuid(consumerUuid);
        List<GuestId> startGuests = toUpdate.getGuestIds();

        // Create a skeleton consumer for consumerResource.performConsumerUpdates
        Consumer consumer = new Consumer();
        consumer.setGuestIds(guestIds);

        Set<String> allGuestIds = new HashSet<String>();
        for (GuestId gid : consumer.getGuestIds()) {
            allGuestIds.add(gid.getGuestId());
        }
        VirtConsumerMap guestConsumerMap = consumerCurator.getGuestConsumersMap(
            toUpdate.getOwner(), allGuestIds);

        if (consumerResource.performConsumerUpdates(consumer, toUpdate, guestConsumerMap)) {
            consumerCurator.update(toUpdate);
            consumerResource.checkForGuestsMigration(toUpdate, startGuests, toUpdate.getGuestIds(),
                guestConsumerMap);
        }
    }

    @ApiOperation(notes = "Updates a single Guest on a Consumer. Allows virt-who to avoid uploading" +
        " an entire list of guests", value = "updateGuest")
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{guest_id}")
    public void updateGuest(
        @ApiParam("consumer who owns or hosts the guest in question")
        @PathParam("consumer_uuid") @Verify(Consumer.class) String consumerUuid,
        @ApiParam("guest virtual uuid")
        @PathParam("guest_id") String guestId,
        @ApiParam("updated guest data to use")
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
        GuestId toUpdate = guestIdCurator.findByGuestIdAndOrg(guestId, consumer.getOwner());
        if (toUpdate != null) {
            updated.setId(toUpdate.getId());
        }
        guestIdCurator.merge(updated);

        Set<String> allGuestIds = new HashSet<String>();
        allGuestIds.add(guestId);
        VirtConsumerMap guestConsumerMap = consumerCurator.getGuestConsumersMap(
            consumer.getOwner(), allGuestIds);
        // we want to remove host-specific entitlements
        if (guestConsumerMap != null && guestConsumerMap.get(guestId) != null) {
            consumerResource.checkForGuestMigration(consumer, guestConsumerMap.get(guestId));
        }
    }

    @ApiOperation(notes = "Removes the Guest from the Consumer", value = "deleteGuest")
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{guest_id}")
    public void deleteGuest(
        @ApiParam("consumer who owns or hosts the guest in question")
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

        sink.queueEvent(eventFactory.guestIdDeleted(toDelete));
        guestIdCurator.delete(toDelete);
    }

    private GuestId validateGuestId(GuestId guest, String guestUuid) {
        if (guest == null) {
            throw new NotFoundException(i18n.tr(
                "Guest with UUID {0} could not be found.", guestUuid));
        }
        return guest;
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
