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

import static org.quartz.JobBuilder.*;

import org.candlepin.auth.Principal;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.GuestId;
import org.candlepin.model.HypervisorId;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.VirtConsumerMap;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.candlepin.resource.ConsumerResource;
import org.candlepin.resource.dto.HypervisorUpdateResult;
import org.candlepin.util.Util;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Asynchronous job for refreshing the entitlement pools for specific
 * {@link Owner}.
 */
public class HypervisorUpdateJob extends UniqueByOwnerJob {

    private static Logger log = LoggerFactory.getLogger(HypervisorUpdateJob.class);
    private OwnerCurator ownerCurator;
    private ConsumerCurator consumerCurator;
    private ConsumerResource consumerResource;

    public static final String CREATE = "create";
    public static final String DATA = "data";
    public static final String PRINCIPAL = "principal";
    protected static String prefix = "hypervisor_update_";

    @Inject
    public HypervisorUpdateJob(OwnerCurator ownerCurator, ConsumerCurator consumerCurator,
            ConsumerResource consumerResource) {
        this.ownerCurator = ownerCurator;
        this.consumerCurator = consumerCurator;
        this.consumerResource = consumerResource;
    }

    /**
     * {@inheritDoc}
     *
     * Executes {@link ConsumerResource#create(org.candlepin.model.Consumer, org.candlepin.auth.Principal,
     *  java.utl.String, java.utl.String, java.utl.String)}
     * Executes (@link ConusmerResource#performConsumerUpdates(java.utl.String, org.candlepin.model.Consumer)}
     * as a pinsetter job.
     *
     * @param context the job's execution context
     */
    @Transactional
    public void toExecute(JobExecutionContext context) throws JobExecutionException {
        try {
            JobDataMap map = context.getMergedJobDataMap();
            String ownerKey = map.getString(JobStatus.TARGET_ID);
            Boolean create = map.getBoolean(CREATE);
            Principal principal = (Principal) map.get(PRINCIPAL);

            HypervisorUpdateResult result = new HypervisorUpdateResult();

            Owner owner = ownerCurator.lookupByKey(ownerKey);
            if (owner == null) {
                context.setResult("Nothing to do. Owner does not exist");
                return;
            }
            byte[] data = (byte[]) map.get(DATA);
            String json = decompress(data);
            HypervisorList hypervisors = (HypervisorList) Util.fromJson(json, HypervisorList.class);
            log.info("Hypervisor consumers for create/update: " + hypervisors.getHypervisors().size());

            Set<String> hosts = new HashSet<String>();
            Set<String> guests = new HashSet<String>();
            Map<String, Consumer> incomingHosts = new HashMap<String, Consumer>();

            for (Consumer hypervisor : hypervisors.getHypervisors()) {
                if (hypervisor.getHypervisorId() != null &&
                        hypervisor.getHypervisorId().getHypervisorId() != null) {
                    incomingHosts.put(hypervisor.getHypervisorId().getHypervisorId(), hypervisor);
                    hosts.add(hypervisor.getHypervisorId().getHypervisorId());
                    if (hypervisor.getGuestIds() != null && !hypervisor.getGuestIds().isEmpty()) {
                        for (GuestId guestId : hypervisor.getGuestIds()) {
                            guests.add(guestId.getGuestId());
                        }
                    }
                }
            }

            // Maps virt hypervisor ID to registered consumer for that hypervisor, should one exist:
            VirtConsumerMap hypervisorConsumersMap =
                    consumerCurator.getHostConsumersMap(owner, hosts);

            // Maps virt guest ID to registered consumer for guest, if one exists:
            VirtConsumerMap guestConsumersMap = consumerCurator.getGuestConsumersMap(
                    owner, guests);

            // Maps virt guest ID to registered consumer for hypervisor, if one exists:
            VirtConsumerMap guestHypervisorConsumers = consumerCurator.
                    getGuestsHostMap(owner, guests);


            for (String hypervisorId : hosts) {
                Consumer knownHost = hypervisorConsumersMap.get(hypervisorId);
                Consumer incoming = incomingHosts.get(hypervisorId);
                if (knownHost == null) {
                    if (!create) {
                        result.failed(hypervisorId, "Unable to find hypervisor with id " +
                                            hypervisorId + " in org " + ownerKey);
                    }
                    else {
                        log.info("Registering new host consumer for hypervisor ID: {}", hypervisorId);
                        Consumer newHost = createConsumerForHypervisorId(hypervisorId, owner, principal);
                        consumerResource.performConsumerUpdates(incoming, newHost, guestConsumersMap,
                                guestHypervisorConsumers, false);
                        consumerResource.create(newHost, principal, null, owner.getKey(), null, false);
                        hypervisorConsumersMap.add(hypervisorId, newHost);
                        result.created(newHost);
                    }
                }
                else if (consumerResource.performConsumerUpdates(incoming, knownHost,
                        guestConsumersMap, guestHypervisorConsumers, false)) {
                    consumerCurator.update(knownHost);
                    result.updated(knownHost);
                }
                else {
                    result.unchanged(knownHost);
                }
            }
            context.setResult(result);
        }
        catch (Exception e) {
            log.error("HypervisorUpdateJob encountered a problem.", e);
            context.setResult(e.getMessage());
            throw new JobExecutionException(e.getMessage(), e, false);
        }
    }

    /**
     * Creates a {@link JobDetail} that runs this job for the given {@link Owner}.
     *
     * @param owner the owner to refresh
     * @return a {@link JobDetail} that describes the job run
     */
    public static JobDetail forOwner(Owner owner, String data, Boolean create, Principal principal) {
        JobDataMap map = new JobDataMap();
        map.put(JobStatus.TARGET_TYPE, JobStatus.TargetType.OWNER);
        map.put(JobStatus.TARGET_ID, owner.getKey());
        map.put(CREATE, create);
        map.put(DATA, compress(data));
        map.put(PRINCIPAL, principal);

        // Not sure if this is the best way to go:
        // Give each job a UUID to ensure that it is unique
        JobDetail detail = newJob(HypervisorUpdateJob.class)
            .withIdentity(prefix + Util.generateUUID())
            .requestRecovery(true) // recover the job upon restarts
            .usingJobData(map)
            .storeDurably(true) // required if we have to postpone the job
            .build();

        return detail;
    }

    public static byte[] compress(String text) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            OutputStream out = new DeflaterOutputStream(baos);
            out.write(text.getBytes("UTF-8"));
            out.close();
        }
        catch (IOException e) {
            throw new AssertionError(e);
        }
        return baos.toByteArray();
    }

    public static String decompress(byte[] bytes) {
        InputStream in = new InflaterInputStream(new ByteArrayInputStream(bytes));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
            return new String(baos.toByteArray(), "UTF-8");
        }
        catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    /*
     * Create a new hypervisor type consumer to represent the incoming hypervisorId
     */
    private Consumer createConsumerForHypervisorId(String incHypervisorId,
            Owner owner, Principal principal) {
        Consumer consumer = new Consumer();
        consumer.setName(incHypervisorId);
        consumer.setType(new ConsumerType(ConsumerTypeEnum.HYPERVISOR));
        consumer.setFact("uname.machine", "x86_64");
        consumer.setGuestIds(new ArrayList<GuestId>());
        consumer.setOwner(owner);
        // Create HypervisorId
        HypervisorId hypervisorId = new HypervisorId(consumer, incHypervisorId);
        consumer.setHypervisorId(hypervisorId);
        return consumer;
    }

    /**
     * Class for holding the list of consumers in the stored json text
     *
     * @author wpoteat
     *
     */
    public static class HypervisorList{
        private List<Consumer> hypervisors;

        public HypervisorList() {
        }

        public List<Consumer> getHypervisors() {
            return this.hypervisors;
        }
        public void setConsumers(List<Consumer> hypervisors) {
            this.hypervisors = hypervisors;
        }
    }

}
