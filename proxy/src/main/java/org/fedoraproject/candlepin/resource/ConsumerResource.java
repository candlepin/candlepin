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
package org.fedoraproject.candlepin.resource;

import java.io.File;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.audit.Event;
import org.fedoraproject.candlepin.audit.EventFactory;
import org.fedoraproject.candlepin.audit.EventSink;
import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.auth.Role;
import org.fedoraproject.candlepin.auth.UserPrincipal;
import org.fedoraproject.candlepin.auth.interceptor.AllowRoles;
import org.fedoraproject.candlepin.controller.Entitler;
import org.fedoraproject.candlepin.controller.PoolManager;
import org.fedoraproject.candlepin.exceptions.BadRequestException;
import org.fedoraproject.candlepin.exceptions.CandlepinException;
import org.fedoraproject.candlepin.exceptions.ForbiddenException;
import org.fedoraproject.candlepin.exceptions.IseException;
import org.fedoraproject.candlepin.exceptions.NotFoundException;
import org.fedoraproject.candlepin.model.CertificateSerialDto;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.ConsumerTypeCurator;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementCertificate;
import org.fedoraproject.candlepin.model.EntitlementCurator;
import org.fedoraproject.candlepin.model.EventCurator;
import org.fedoraproject.candlepin.model.IdentityCertificate;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.model.User;
import org.fedoraproject.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.fedoraproject.candlepin.policy.EntitlementRefusedException;
import org.fedoraproject.candlepin.policy.js.consumer.ConsumerDeleteHelper;
import org.fedoraproject.candlepin.policy.js.consumer.ConsumerRules;
import org.fedoraproject.candlepin.service.EntitlementCertServiceAdapter;
import org.fedoraproject.candlepin.service.IdentityCertServiceAdapter;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;
import org.fedoraproject.candlepin.service.SubscriptionServiceAdapter;
import org.fedoraproject.candlepin.service.UserServiceAdapter;
import org.fedoraproject.candlepin.sync.ExportCreationException;
import org.fedoraproject.candlepin.sync.Exporter;
import org.jboss.resteasy.annotations.providers.jaxb.Wrapped;
import org.jboss.resteasy.plugins.providers.atom.Feed;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;
import com.wideplay.warp.persist.Transactional;

/**
 * API Gateway for Consumers
 */
@Path("/consumers")
public class ConsumerResource {
    private static Logger log = Logger.getLogger(ConsumerResource.class);
    private ConsumerCurator consumerCurator;
    private ConsumerTypeCurator consumerTypeCurator;
    private ProductServiceAdapter productAdapter;
    private Entitler entitler;
    private SubscriptionServiceAdapter subAdapter;
    private EntitlementCurator entitlementCurator;
    private IdentityCertServiceAdapter identityCertService;
    private EntitlementCertServiceAdapter entCertService;
    private UserServiceAdapter userService;
    private I18n i18n;
    private EventSink sink;
    private EventFactory eventFactory;
    private EventCurator eventCurator;
    private static final int FEED_LIMIT = 1000;
    private Exporter exporter;
    private PoolManager poolManager;
    private ConsumerRules consumerRules;
    private ConsumerDeleteHelper consumerDeleteHelper;

    @Inject
    public ConsumerResource(ConsumerCurator consumerCurator,
        ConsumerTypeCurator consumerTypeCurator,
        ProductServiceAdapter productAdapter, Entitler entitler,
        SubscriptionServiceAdapter subAdapter,
        EntitlementCurator entitlementCurator,
        IdentityCertServiceAdapter identityCertService,
        EntitlementCertServiceAdapter entCertServiceAdapter, I18n i18n,
        EventSink sink, EventFactory eventFactory, EventCurator eventCurator,
        UserServiceAdapter userService, Exporter exporter,
        PoolManager poolManager, ConsumerRules consumerRules,
        ConsumerDeleteHelper consumerDeleteHelper) {

        this.consumerCurator = consumerCurator;
        this.consumerTypeCurator = consumerTypeCurator;
        this.productAdapter = productAdapter;
        this.subAdapter = subAdapter;
        this.entitler = entitler;
        this.entitlementCurator = entitlementCurator;
        this.identityCertService = identityCertService;
        this.entCertService = entCertServiceAdapter;
        this.i18n = i18n;
        this.sink = sink;
        this.eventFactory = eventFactory;
        this.eventCurator = eventCurator;
        this.userService = userService;
        this.exporter = exporter;
        this.poolManager = poolManager;
        this.consumerRules = consumerRules;
        this.consumerDeleteHelper = consumerDeleteHelper;
    }

    /**
     * List available Consumers
     * 
     * @return list of available consumers.
     */
    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Wrapped(element = "consumers")
    @AllowRoles(roles = { Role.OWNER_ADMIN })
    public List<Consumer> list() {
        return consumerCurator.listAll();
    }

    /**
     * Return the consumer identified by the given uuid.
     * 
     * @param uuid uuid of the consumer sought.
     * @return the consumer identified by the given uuid.
     */
    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Path("{consumer_uuid}")
    @AllowRoles(roles = { Role.CONSUMER, Role.OWNER_ADMIN })
    public Consumer getConsumer(@PathParam("consumer_uuid") String uuid) {
        return verifyAndLookupConsumer(uuid);
    }

    /**
     * Create a Consumer
     * 
     * @param consumer Consumer metadata
     * @return newly created Consumer
     * @throws BadRequestException generic exception type for web services We
     *         are calling this "registerConsumer" in the api discussions
     */
    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @AllowRoles(roles = { Role.CONSUMER, Role.OWNER_ADMIN })
    public Consumer create(Consumer consumer, @Context Principal principal)
        throws BadRequestException {
        // API:registerConsumer

        ConsumerType type = consumerTypeCurator.lookupByLabel(consumer
            .getType().getLabel());

        if (type == null) {
            throw new BadRequestException(i18n.tr("No such consumer type: {0}",
                consumer.getType().getLabel()));
        }

        User user = getCurrentUsername(principal);

        // TODO: Refactor out type specific checks?
        if (type.isType(ConsumerTypeEnum.PERSON) && user != null) {
            Consumer existing = consumerCurator.lookupUsersConsumer(user);

            if (existing != null &&
                existing.getType().isType(ConsumerTypeEnum.PERSON)) {
                // TODO: This is not the correct error code for this situation!
                throw new BadRequestException(i18n.tr(
                    "User {0} has already registered a personal consumer", user
                        .getUsername()));
            }
            consumer.setName(user.getUsername());
        }

        consumer.setUserName(user.getUsername());
        consumer.setOwner(principal.getOwner());
        consumer.setType(type);

        if (log.isDebugEnabled()) {
            log.debug("Got consumerTypeLabel of: " + type.getLabel());
            log.debug("got metadata: ");
            log.debug(consumer.getFacts());

            for (String key : consumer.getFacts().keySet()) {
                log.debug("   " + key + " = " + consumer.getFact(key));
            }
        }

        try {
            consumer = consumerCurator.create(consumer);
            IdentityCertificate idCert = null;

            // This is pretty bad - I'm still not convinced that
            // the id cert actually needs the username at all
            if (user != null) {
                idCert = identityCertService.generateIdentityCert(consumer,
                    user.getUsername());
            }

            if (log.isDebugEnabled()) {
                log.debug("Generated identity cert: " + idCert);
                log.debug("Created consumer: " + consumer);
            }

            if (idCert == null) {
                throw new RuntimeException(
                    "Error generating identity certificate.");
            }

            sink.emitConsumerCreated(consumer);
            return consumer;
        }
        catch (Exception e) {
            log.error("Problem creating consumer:", e);
            throw new BadRequestException(i18n.tr(
                "Problem creating consumer {0}", consumer));
        }
    }

    private User getCurrentUsername(Principal principal) {
        if (principal instanceof UserPrincipal) {
            UserPrincipal user = (UserPrincipal) principal;
            return userService.findByLogin(user.getUsername());
        }

        return null;
    }

    @PUT
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Path("{consumer_uuid}")
    @Transactional
    @AllowRoles(roles = { Role.CONSUMER, Role.OWNER_ADMIN })
    public void updateConsumer(@PathParam("consumer_uuid") String uuid,
        Consumer consumer) {
        Consumer toUpdate = verifyAndLookupConsumer(uuid);

        if (!toUpdate.factsAreEqual(consumer)) {
            Event event = eventFactory.consumerModified(toUpdate, consumer);

            // TODO: Just updating the facts for now
            toUpdate.setFacts(consumer.getFacts());
            sink.sendEvent(event);
        }
    }

    /**
     * delete the consumer.
     * 
     * @param uuid uuid of the consumer to delete.
     */
    @DELETE
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Path("{consumer_uuid}")
    @Transactional
    @AllowRoles(roles = { Role.CONSUMER, Role.OWNER_ADMIN })
    public void deleteConsumer(@PathParam("consumer_uuid") String uuid) {
        log.debug("deleting  consumer_uuid" + uuid);
        Consumer toDelete = verifyAndLookupConsumer(uuid);

        consumerRules.onConsumerDelete(consumerDeleteHelper, toDelete);
        unbindAll(uuid);

        Event event = eventFactory.consumerDeleted(toDelete);

        consumerCurator.delete(toDelete);
        identityCertService.deleteIdentityCert(toDelete);
        sink.sendEvent(event);
    }

    /**
     * Return the entitlement certificate for the given consumer.
     * 
     * @param consumerUuid UUID of the consumer
     * @return list of the client certificates for the given consumer.
     */
    @GET
    @Path("{consumer_uuid}/certificates")
    @Produces({ MediaType.APPLICATION_JSON })
    @AllowRoles(roles = { Role.CONSUMER, Role.OWNER_ADMIN })
    public List<EntitlementCertificate> getEntitlementCertificates(
        @PathParam("consumer_uuid") String consumerUuid,
        @QueryParam("serials") String serials) {

        log.debug("Getting client certificates for consumer: " + consumerUuid);
        Consumer consumer = verifyAndLookupConsumer(consumerUuid);

        Set<Long> serialSet = new HashSet<Long>();
        if (serials != null) {
            log.debug("Requested serials: " + serials);
            for (String s : serials.split(",")) {
                log.debug("   " + s);
                serialSet.add(Long.valueOf(s));
            }
        }

        List<EntitlementCertificate> returnCerts = new LinkedList<EntitlementCertificate>();
        List<EntitlementCertificate> allCerts = entCertService
            .listForConsumer(consumer);
        for (EntitlementCertificate cert : allCerts) {
            if (serialSet.size() == 0 ||
                serialSet.contains(cert.getSerial().getId())) {
                returnCerts.add(cert);
            }
        }
        return returnCerts;
    }

    /**
     * Return the client certificate metadata for the given consumer. This is a
     * small subset of data clients can use to determine which certificates they
     * need to update/fetch.
     * 
     * @param consumerUuid UUID of the consumer
     * @return list of the client certificate metadata for the given consumer.
     */
    @GET
    @Path("{consumer_uuid}/certificates/serials")
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Wrapped(element = "serials")
    @AllowRoles(roles = { Role.CONSUMER, Role.OWNER_ADMIN })
    public List<CertificateSerialDto> getEntitlementCertificateSerials(
        @PathParam("consumer_uuid") String consumerUuid) {

        log.debug("Getting client certificate serials for consumer: " +
            consumerUuid);
        Consumer consumer = verifyAndLookupConsumer(consumerUuid);

        List<CertificateSerialDto> allCerts = new LinkedList<CertificateSerialDto>();
        for (EntitlementCertificate cert : entCertService
            .listForConsumer(consumer)) {
            allCerts.add(new CertificateSerialDto(cert.getSerial().getId()));
        }

        return allCerts;
    }

    /**
     * Entitles the given Consumer to the given Product. Will seek out pools
     * which provide access to this product, either directly or as a child, and
     * select the best one based on a call to the rules engine.
     * 
     * @param productId Product ID.
     * @return Entitlement object.
     */
    private List<Entitlement> bindByProduct(String productId,
        Consumer consumer, Integer quantity) {

        List<Entitlement> entitlementList = new LinkedList<Entitlement>();
        entitlementList.add(createEntitlementByProduct(consumer, productId,
            quantity));
        return entitlementList;
    }

    // TODO: Bleh, very duplicated methods here:
    private Entitlement createEntitlementByProduct(Consumer consumer,
        String productId, Integer quantity) {
        // Attempt to create an entitlement:
        try {
            Entitlement e = entitler.entitleByProduct(consumer, productId,
                quantity);
            log.debug("Created entitlement: " + e);
            return e;
        }
        catch (EntitlementRefusedException e) {
            // Could be multiple errors, but we'll just report the first one for
            // now:
            // TODO: Convert resource key to user friendly string?
            // See below for more TODOS
            String error = e.getResult().getErrors().get(0).getResourceKey();
            if (error.equals("rulefailed.consumer.already.has.product")) {
                throw new ForbiddenException(
                    i18n
                        .tr(
                            "This consumer is already subscribed to the product ''{0}''",
                            productId));
            }

            throw new ForbiddenException(e.getResult().getErrors().get(0)
                .getResourceKey());
        }
    }

    private Entitlement createEntitlementByPool(Consumer consumer, Pool pool,
        Integer quantity) {
        // Attempt to create an entitlement:
        try {
            Entitlement e = entitler.entitleByPool(consumer, pool, quantity);
            log.debug("Created entitlement: " + e);
            return e;
        }
        catch (EntitlementRefusedException e) {
            // Could be multiple errors, but we'll just report the first one for
            // now:
            // TODO: Convert resource key to user friendly string?
            // TODO: multiple checks here for the errors will get ugly, but the
            // returned
            // string is dependent on the caller (ie pool vs product)
            String error = e.getResult().getErrors().get(0).getResourceKey();
            if (error.equals("rulefailed.consumer.already.has.product")) {
                throw new ForbiddenException(i18n.tr(
                    "This consumer is already subscribed to the product matching pool " +
                        "with id ''{0}''", pool.getId().toString()));
            }
            throw new ForbiddenException(e.getResult().getErrors().get(0)
                .getResourceKey());
        }
    }

    /**
     * Grants entitlements based on a registration token.
     * 
     * @param registrationToken registration token.
     * @param consumer Consumer to bind
     * @return token
     */
    private List<Entitlement> bindByToken(String registrationToken,
        Consumer consumer, Integer quantity, String email, String emailLocale) {

        List<Subscription> subs = subAdapter.getSubscriptionForToken(consumer
            .getOwner(), registrationToken, email, emailLocale);
        if ((subs == null) || (subs.isEmpty())) {
            log.debug("token: " + registrationToken);
            throw new BadRequestException(i18n.tr("No such token: {0}",
                registrationToken));
        }

        List<Entitlement> entitlementList = new LinkedList<Entitlement>();
        for (Subscription sub : subs) {

            // Make sure we have created/updated a pool for this subscription:
            Pool pool = poolManager.lookupBySubscriptionId(sub.getId());
            if (pool == null) {
                poolManager.createPoolForSubscription(sub);
            }
            else {
                poolManager.updatePoolForSubscription(pool, sub);
            }

            Product p = sub.getProduct();
            entitlementList.add(createEntitlementByProduct(consumer, p.getId(),
                quantity));
        }
        return entitlementList;
    }

    private List<Entitlement> bindByPool(Long poolId, Consumer consumer,
        Integer quantity) {
        Pool pool = poolManager.find(poolId);
        List<Entitlement> entitlementList = new LinkedList<Entitlement>();
        if (pool == null) {
            throw new BadRequestException(i18n.tr(
                "No such entitlement pool: {0}", poolId));
        }

        // Attempt to create an entitlement:
        entitlementList.add(createEntitlementByPool(consumer, pool, quantity));
        return entitlementList;
    }

    /**
     * Request an entitlement.
     * 
     * @param consumerUuid Consumer identifier to be entitled
     * @param poolIdString Entitlement pool id.
     * @param email TODO
     * @param emailLocale TODO
     * @return Entitlement.
     */
    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Path("/{consumer_uuid}/entitlements")
    @AllowRoles(roles = { Role.CONSUMER, Role.OWNER_ADMIN })
    public List<Entitlement> bind(
        @PathParam("consumer_uuid") String consumerUuid,
        @QueryParam("pool") String poolIdString,
        @QueryParam("token") String token,
        @QueryParam("product") String productId,
        @QueryParam("quantity") @DefaultValue("1") Integer quantity,
        @QueryParam("email") String email,
        @QueryParam("emailLocale") String emailLocale) {

        // Verify that the poolId is a Long if provided
        Long poolId = null;
        if (poolIdString != null) {
            try {
                poolId = Long.parseLong(poolIdString);
            }
            catch (NumberFormatException e) {
                throw new BadRequestException(i18n
                    .tr("Pool ID should be numeric"));
            }
        }

        // Check that only one query param was set:
        if ((poolId != null && token != null) ||
            (poolId != null && productId != null) ||
            (token != null && productId != null)) {
            throw new BadRequestException(i18n
                .tr("Cannot bind by multiple parameters."));
        }

        // Verify consumer exists:
        Consumer consumer = verifyAndLookupConsumer(consumerUuid);
        List<Entitlement> entitlements = null;
        try {
            if (!subAdapter.hasUnacceptedSubscriptionTerms(consumer.getOwner())) {

                if (token != null) {
                    entitlements = bindByToken(token, consumer, quantity,
                        email, emailLocale);
                }
                else if (productId != null) {
                    entitlements = bindByProduct(productId, consumer, quantity);
                }
                else {
                    entitlements = bindByPool(poolId, consumer, quantity);
                }
            }
        }
        catch (CandlepinException e) {
            log.debug(e.getMessage());
            throw e;
        }

        // Trigger events:
        for (Entitlement e : entitlements) {
            Event event = eventFactory.entitlementCreated(e);
            sink.sendEvent(event);
        }

        return entitlements;
    }

    private Consumer verifyAndLookupConsumer(String consumerUuid) {
        Consumer consumer = consumerCurator.lookupByUuid(consumerUuid);
        if (consumer == null) {

            throw new NotFoundException(i18n.tr("No such consumer: {0}",
                consumerUuid));
        }
        return consumer;
    }

    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Path("/{consumer_uuid}/entitlements")
    @AllowRoles(roles = { Role.CONSUMER, Role.OWNER_ADMIN })
    public List<Entitlement> listEntitlements(
        @PathParam("consumer_uuid") String consumerUuid,
        @QueryParam("product") String productId) {

        Consumer consumer = verifyAndLookupConsumer(consumerUuid);
        if (productId != null) {
            Product p = productAdapter.getProductById(productId);
            if (p == null) {
                throw new BadRequestException(i18n.tr("No such product: {0}",
                    productId));
            }
            return entitlementCurator.listByConsumerAndProduct(consumer,
                productId);
        }

        return entitlementCurator.listByConsumer(consumer);

    }

    /**
     * Unbind all entitlements.
     * 
     * @param consumerUuid Unique id for the Consumer.
     */
    @DELETE
    @Path("/{consumer_uuid}/entitlements")
    @AllowRoles(roles = { Role.CONSUMER, Role.OWNER_ADMIN })
    public void unbindAll(@PathParam("consumer_uuid") String consumerUuid) {

        // FIXME: just a stub, needs CertifcateService (and/or a
        // CertificateCurator) to lookup by serialNumber
        Consumer consumer = verifyAndLookupConsumer(consumerUuid);

        if (consumer == null) {
            throw new NotFoundException(i18n.tr("Consumer with ID " +
                consumerUuid + " could not be found."));
        }

        entitler.revokeAllEntitlements(consumer);

        // Need to parse off the value of subscriptionNumberArgs, probably
        // use comma separated see IntergerList in sparklines example in
        // jersey examples find all entitlements for this consumer and
        // subscription numbers delete all of those (and/or return them to
        // entitlement pool)

    }

    /**
     * Remove an entitlement by ID.
     * 
     * @param dbid the entitlement to delete.
     */
    @DELETE
    @Path("/{consumer_uuid}/entitlements/{dbid}")
    @AllowRoles(roles = { Role.CONSUMER, Role.OWNER_ADMIN })
    public void unbind(@PathParam("consumer_uuid") String consumerUuid,
        @PathParam("dbid") Long dbid) {

        verifyAndLookupConsumer(consumerUuid);

        Entitlement toDelete = entitlementCurator.find(dbid);
        if (toDelete != null) {
            entitler.revokeEntitlement(toDelete);
            return;
        }
        throw new NotFoundException(i18n.tr(
            "Entitlement with ID '{0}' could not be found.", dbid));
    }

    @DELETE
    @Path("/{consumer_uuid}/certificates/{serial}")
    @AllowRoles(roles = { Role.CONSUMER, Role.OWNER_ADMIN })
    public void unbindBySerial(@PathParam("consumer_uuid") String consumerUuid,
        @PathParam("serial") Long serial) {

        verifyAndLookupConsumer(consumerUuid);
        Entitlement toDelete = entitlementCurator
            .findByCertificateSerial(BigInteger.valueOf(serial));

        if (toDelete != null) {
            entitler.revokeEntitlement(toDelete);
            return;
        }
        throw new NotFoundException(
            i18n
                .tr(
                    "Entitlement Certificate with serial number {0} could not be found.",
                    serial));
    }

    @GET
    @Produces("application/atom+xml")
    @Path("{consumer_uuid}/atom")
    @AllowRoles(roles = { Role.OWNER_ADMIN })
    public Feed getConsumerAtomFeed(
        @PathParam("consumer_uuid") String consumerUuid) {
        Consumer consumer = verifyAndLookupConsumer(consumerUuid);
        Feed feed = this.eventCurator.toFeed(this.eventCurator.listMostRecent(
            FEED_LIMIT, consumer));
        feed.setTitle("Event feed for consumer " + consumer.getUuid());
        return feed;
    }

    @PUT
    @AllowRoles(roles = { Role.CONSUMER, Role.OWNER_ADMIN })
    @Path("/{consumer_uuid}/certificates/")
    public void regenerateEntitlementCertificates(
        @PathParam("consumer_uuid") String consumerUuid) {
        Consumer c = verifyAndLookupConsumer(consumerUuid);
        this.entitler.regenerateEntitlementCertificates(c);
    }

    @GET
    @Produces("application/zip")
    @Path("{consumer_uuid}/export")
    @AllowRoles(roles = { Role.CONSUMER, Role.OWNER_ADMIN })
    public File exportData(@Context HttpServletResponse response,
        @PathParam("consumer_uuid") String consumerUuid) {

        Consumer consumer = verifyAndLookupConsumer(consumerUuid);
        if (!consumer.getType().isType(ConsumerTypeEnum.CANDLEPIN)) {
            throw new ForbiddenException(
                i18n
                    .tr(
                        "Consumer {0} cannot be exported, as it's of wrong consumer type.",
                        consumerUuid));
        }

        File archive;
        try {
            archive = exporter.getExport(consumer);
            response.addHeader("Content-Disposition", "attachment; filename=" +
                archive.getName());
            return archive;
        }
        catch (ExportCreationException e) {
            throw new IseException(i18n.tr("Unable to create export archive"));
        }
    }
}
