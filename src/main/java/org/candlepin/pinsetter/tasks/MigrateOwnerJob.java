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
package org.candlepin.pinsetter.tasks;

import static org.quartz.JobBuilder.newJob;

import org.candlepin.audit.EventSink;
import org.candlepin.client.CandlepinConnection;
import org.candlepin.client.ConsumerClient;
import org.candlepin.client.OwnerClient;
import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.util.Util;

import com.google.inject.Inject;

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.lang.StringUtils;
import org.jboss.resteasy.client.ClientResponse;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
/**
 * MigrateOwnerJob is an async job that will extract the owner and its data
 * from another Candlepin instance. The job is passed an owner key identifying
 * the owner to be migrated, an URI pointing to the Candlepin instance where
 * the owner to be migrated currently exists, and finally an optional delete
 * flag that indicates whether the original owner should be deleted once
 * migration has occurred.
 */
public class MigrateOwnerJob extends KingpinJob {
    private static Logger log = LoggerFactory.getLogger(MigrateOwnerJob.class);

    private OwnerCurator ownerCurator;
    private PoolCurator poolCurator;
    private EntitlementCurator entCurator;
    private ConsumerCurator consumerCurator;
    private CandlepinConnection conn;
    private Config config;
    private HashMap<String, String> entMap = new HashMap<String, String>();
    private EventSink sink;


    /**
     * Constructs the job with the connection, configuration, and necessary
     * object curators required to persist the objects.
     * @param connection Represents connection to another Candlepin.
     * @param conf Candlepin configuration.
     * @param oc database layer for Owner
     * @param pc database layer for Pool
     * @param ec database layer for Entitlement
     * @param cc database layer for Consumer
     */
    @Inject
    public MigrateOwnerJob(CandlepinConnection connection, Config conf,
        OwnerCurator oc, PoolCurator pc, EntitlementCurator ec,
        ConsumerCurator cc, EventSink es) {
        ownerCurator = oc;
        consumerCurator = cc;
        conn = connection;
        config = conf;
        poolCurator = pc;
        entCurator = ec;
        sink = es;
    }

    private static String buildUri(String uri) {
        if (uri == null || "".equals(uri.trim())) {
            return "";
        }

        String[] parts = uri.split("://");
        if (parts.length > 1) {
            String[] paths = parts[1].split("/");
            StringBuffer buf = new StringBuffer(parts[0]);
            buf.append("://");
            buf.append(paths[0]);
            buf.append("/candlepin");
            uri = buf.toString();
        }
        else {
            StringBuffer buf = new StringBuffer("http://");
            buf.append(parts[0]);
            buf.append("/candlepin");
            uri = buf.toString();
        }

        return uri;
    }

    @Override
    public void toExecute(JobExecutionContext ctx)
        throws JobExecutionException {
        String key = ctx.getMergedJobDataMap().getString("owner_key");
        String uri = buildUri(ctx.getMergedJobDataMap().getString("uri"));
        boolean delete = ctx.getMergedJobDataMap().getBoolean("delete");

        validateInput(key, uri);

        Credentials creds = new UsernamePasswordCredentials(
            config.getString(ConfigProperties.SHARD_USERNAME),
            config.getString(ConfigProperties.SHARD_PASSWORD));
        OwnerClient oclient = conn.connect(OwnerClient.class, creds, uri);

        log.info("Migrating owner [" + key +
            "] from candlepin instance running on [" + uri + "]");
        replicateOwner(key, oclient);


        log.info("Migrating pools for owner [" + key +
            "] from candlepin instance running on [" + uri + "]");
        replicatePools(key, oclient);

        log.info("Migrating entitlements for owner [" + key +
            "] from candlepin instance running on [" + uri + "]");
        replicateEntitlements(key, oclient);

        log.info("Migrating consumers for owner [" + key +
            "] from candlepin instance running on [" + uri + "]");
        replicateConsumers(key, oclient);

        ConsumerClient cclient = conn.connect(ConsumerClient.class, creds, uri);
        log.info("Associating consumers to their entitlements for owner [" +
            key + "]");
        associateConsumersToEntitlements(key, cclient);

        if (delete) {
            log.info("Removing owner [" + key +
                "] from candlepin instance running on [" + uri + "]");
            cleanupOwner(key, oclient);
        }
        else {
            log.info("delete flag was false, owner [" + key +
                "] will not be deleted from candlepin instance running on [" +
                uri + "]");
        }

        log.info("FINISHED - migration of owner [" + key +
            "] from candlepin instance running on [" + uri + "]");
    }

    /**
     * deletes the owner from the <strong>other</strong> Candlepin instance.
     * @param key owner key to be deleted.
     * @param client Candlepin client.
     * @throws WebApplicationException if the other Candlepin instance returns
     * other than OK, ACCEPTED, or NO_CONTENT.
     */
    private void cleanupOwner(String key, OwnerClient client) {
        Response rsp = client.deleteOwner(key, false);
        if (rsp.getStatus() != Status.OK.getStatusCode() &&
            rsp.getStatus() != Status.ACCEPTED.getStatusCode() &&
            rsp.getStatus() != Status.NO_CONTENT.getStatusCode()) {
            throw new WebApplicationException(rsp);
        }
    }

    /**
     * associates the migrated entitlements to the migrated consumers since
     * the api does not return the associations.
     * @param key owner key to be deleted.
     * @param client Candlepin client.
     * @throws WebApplicationException if the other Candlepin instance returns
     * other than OK.
     */
    private void associateConsumersToEntitlements(String key,
        ConsumerClient client) {

        Owner owner = ownerCurator.lookupByKey(key);
        log.debug("owner [" + owner.getDisplayName() + "] has [" +
            owner.getConsumers().size() + "] consumers");

        List<Consumer> consumers = consumerCurator.listByOwner(owner);
        for (Consumer c : consumers) {

            if (log.isDebugEnabled()) {
                log.debug("Processing consumer [" + c.getUuid() + "]");
            }

            ClientResponse<List<Entitlement>> rsp =
                client.replicateEntitlements(c.getUuid(), null);
            if (rsp.getStatus() != Status.OK.getStatusCode()) {
                throw new WebApplicationException(rsp);
            }

            for (Entitlement e : rsp.getEntity()) {
                Entitlement realent = entCurator.find(e.getId());
                realent.setConsumer(c);
                entCurator.merge(realent);
            }
        }

        sink.emitOwnerMigrated(owner);
    }

    /**
     * Replicates the Owner from the Candlepin pointed to by the client.
     * @param key owner key who should be migrated.
     * @param client Candlepin client.
     * @throws NotFoundException if the other Candlepin instance returns
     * NOT_FOUND.
     */
    private void replicateOwner(String key, OwnerClient client) {
        ClientResponse<Owner> rsp = client.replicateOwner(key);

        log.info("call returned - status: [" + rsp.getStatus() + "] reason [" +
            rsp.getResponseStatus() + "]");

        if (rsp.getStatus() == Status.NOT_FOUND.getStatusCode()) {
            throw new NotFoundException("Can't find owner [" + key + "]");
        }

        Owner owner = rsp.getEntity();
        ownerCurator.replicate(owner);

    }

    /**
     * Replicates the owner's pools from the Candlepin pointed to by the client.
     * @param key owner key whose pools should be migrated.
     * @param client Candlepin client.
     * @throws WebApplicationException if the other Candlepin instance returns
     * other than OK.
     */
    private void replicatePools(String key, OwnerClient client) {
        ClientResponse<List<Pool>> rsp = client.replicatePools(key);
        if (rsp.getStatus() != Status.OK.getStatusCode()) {
            throw new WebApplicationException(rsp);
        }

        List<Pool> pools = rsp.getEntity();

        for (Pool pool : pools) {
            Entitlement ent = pool.getSourceEntitlement();
            poolCurator.replicate(pool);
            if (ent != null) {
                log.info("pool.id" + pool.getId() + " sourceEntitlement " + ent.getId());
                entMap.put(ent.getId(), pool.getId());
            }
        }
    }

    /**
     * Replicates the owner's consumers from the Candlepin pointed to by the
     * client.
     * @param key owner key whose consumers should be migrated.
     * @param client Candlepin client.
     * @throws WebApplicationException if the other Candlepin instance returns
     * other than OK.
     */
    private void replicateConsumers(String key, OwnerClient client) {
        // track down consumers for the owner
        ClientResponse<List<Consumer>> rsp = client.replicateConsumers(key);

        if (rsp.getStatus() != Status.OK.getStatusCode()) {
            throw new WebApplicationException(rsp);
        }

        for (Consumer consumer : rsp.getEntity()) {
            log.info("importing consumer: " + consumer.toString());

            log.info("consumer.id: " + consumer.getId());
            log.info("consumer.entitlements:  " +
                ((consumer.getEntitlements() != null) ?
                    consumer.getEntitlements().toString() : "null"));
            log.info("consumer.facts: " +
                ((consumer.getFacts() != null) ?
                    consumer.getFacts().toString() : "null"));
            log.info("consumer.keyPair: " + consumer.getKeyPair());
            log.info("consumer.idcert: " + consumer.getIdCert());

            consumerCurator.replicate(consumer);
        }
    }

    /**
     * Replicates the owner's entitlements from the Candlepin pointed to by the
     * client.
     * @param key owner key whose entitlements should be migrated.
     * @param client Candlepin client.
     * @throws WebApplicationException if the other Candlepin instance returns
     * other than OK.
     */
    private void replicateEntitlements(String key, OwnerClient client) {
        ClientResponse<List<Entitlement>> rsp = client.replicateEntitlements(key);

        if (rsp.getStatus() != Status.OK.getStatusCode()) {
            throw new WebApplicationException(rsp);
        }

        Owner owner = ownerCurator.lookupByKey(key);
        List<Entitlement> ents = rsp.getEntity();

        for (Entitlement ent : ents) {
            ent.setOwner(owner);

            entCurator.replicate(ent);
            if (entMap.containsKey(ent.getId())) {
                log.info("entitlement with source pool " +
                    poolCurator.find(entMap.get(ent.getId())));

                Pool entPool = poolCurator.find(entMap.get(ent.getId()));
                entPool.setSourceEntitlement(entCurator.find(ent.getId()));
                poolCurator.merge(entPool);
            }
        }
    }

    /**
     * Creates the JobDetail for this MigrateOwnerJob class. The JobDetail will
     * get passed in through the execute method via the JobExecutionContext.
     * @param key owner key who should be migrated.
     * @param uri URI of the Candlepin instance where owner will be pulled.
     * @param delete true if owner should be deleted from the Candlepin
     * instance pointed by the URI.
     * @return JobDetail containing the information needed by the asynchronous
     * job to handle the migration of the given owner (key).
     */
    public static JobDetail migrateOwner(String key, String uri, boolean delete) {
        uri = buildUri(uri);
        validateInput(key, uri);

        JobDataMap map = new JobDataMap();
        map.put("owner_key", key);
        map.put("uri", uri);
        map.put("delete", delete);

        JobDetail detail = newJob(MigrateOwnerJob.class)
            .withIdentity("migrate_owner_" + Util.generateUUID())
            .requestRecovery(true) // recover the job upon restarts
            .usingJobData(map)
            .build();

        return detail;
    }

    private static void validateInput(String key, String uri) {
        if (StringUtils.isEmpty(key)) {
            throw new BadRequestException("Invalid owner key");
        }

        if (StringUtils.isEmpty(uri)) {
            throw new BadRequestException("Invalid URL [" + uri + "]");
        }

        try {
            new URL(uri);
        }
        catch (MalformedURLException e) {
            throw new BadRequestException("Invalid URL [" + uri + "]", e);
        }

    }
}
