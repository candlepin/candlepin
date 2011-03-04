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
package org.fedoraproject.candlepin.pinsetter.tasks;

import org.fedoraproject.candlepin.client.CandlepinConnection;
import org.fedoraproject.candlepin.client.OwnerClient;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.config.ConfigProperties;
import org.fedoraproject.candlepin.exceptions.BadRequestException;
import org.fedoraproject.candlepin.exceptions.NotFoundException;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.OwnerCurator;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.PoolCurator;
import org.fedoraproject.candlepin.model.EntitlementCurator;
import org.fedoraproject.candlepin.util.Util;

import com.google.inject.Inject;

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.log4j.Logger;
import org.hibernate.tool.hbm2x.StringUtils;
import org.jboss.resteasy.client.ClientResponse;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

/**
 * MigrateOwnerJob
 */
public class MigrateOwnerJob implements Job {
    private static Logger log = Logger.getLogger(MigrateOwnerJob.class);

    private OwnerCurator ownerCurator;
    private PoolCurator poolCurator;
    private EntitlementCurator entCurator;
    private CandlepinConnection conn;
    private Config config;
    
    @Inject
    public MigrateOwnerJob(OwnerCurator oc, CandlepinConnection connection,
        Config conf, PoolCurator pc, EntitlementCurator ec) {

        ownerCurator = oc;
        conn = connection;
        config = conf;
        poolCurator = pc;
        entCurator = ec;
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
    public void execute(JobExecutionContext ctx)
        throws JobExecutionException {
        String key = ctx.getMergedJobDataMap().getString("owner_key");
        String uri = buildUri(ctx.getMergedJobDataMap().getString("uri"));

        validateInput(key, uri);
        
        log.info("Migrating owner [" + key +
            "] from candlepin instance running on [" + uri + "]");
        
        Credentials creds = new UsernamePasswordCredentials(
            config.getString(ConfigProperties.SHARD_USERNAME),
            config.getString(ConfigProperties.SHARD_PASSWORD));
        OwnerClient client = conn.connect(creds, uri);
        ClientResponse<Owner> rsp = client.exportOwner(key);
        
        log.info("call returned - status: [" + rsp.getStatus() + "] reason [" +
            rsp.getResponseStatus() + "]");

        // TODO: do we want specific errors or just a general one
        if (rsp.getStatus() == Status.NOT_FOUND.getStatusCode()) {
            throw new NotFoundException("Can't find owner [" + key + "]");
        }

        if (rsp.getStatus() == Status.OK.getStatusCode()) {
            Owner owner = rsp.getEntity();

            ownerCurator.importOwner(owner);
        }
        else {
            throw new WebApplicationException(rsp);
        }

        log.info("Migrating pools for owner [" + key +
            "] from candlepin instance running on [" + uri + "]");
        exportPools(key, client);
    }

    private void exportPools(String ownerkey, OwnerClient client) {
        ClientResponse<List<Pool>> rsp = client.exportPools(ownerkey);
        if (rsp.getStatus() == Status.OK.getStatusCode()) {
            List<Pool> pools = rsp.getEntity();

            for (Pool pool : pools) {
                poolCurator.importPool(pool);
            }
        }
        else {
            throw new WebApplicationException(rsp);
        }
    }

    public static JobDetail migrateOwner(String key, String uri) {
        uri = buildUri(uri);
        validateInput(key, uri);

        JobDetail detail = new JobDetail("migrate_owner_" + Util.generateUUID(),
            MigrateOwnerJob.class);
        JobDataMap map = new JobDataMap();
        map.put("owner_key", key);
        map.put("uri", uri);
        
        detail.setJobDataMap(map);
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
