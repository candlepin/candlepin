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

import org.candlepin.audit.QpidConnection;
import org.candlepin.audit.QpidQmf;
import org.candlepin.cache.CandlepinCache;
import org.candlepin.common.auth.SecurityHole;
import org.candlepin.common.config.Configuration;
import org.candlepin.common.util.VersionUtil;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.RulesCurator;
import org.candlepin.model.Status;
import org.candlepin.policy.js.JsRunnerProvider;

import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import javax.cache.Cache;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/**
 * Status Resource
 */

@Path("/status")
@Api("status")
public class StatusResource {
    private static Logger log = LoggerFactory.getLogger(StatusResource.class);

    /**
     * The current version of candlepin
     */
    private String version = "Unknown";

    /**
     * The current git release
     */
    private String release = "Unknown";

    private boolean standalone = true;
    private QpidConnection sender;
    private RulesCurator rulesCurator;
    private JsRunnerProvider jsProvider;
    private CandlepinCache candlepinCache;
    private QpidQmf qmf;

    @Inject
    public StatusResource(RulesCurator rulesCurator, Configuration config, JsRunnerProvider jsProvider,
        CandlepinCache candlepinCache, QpidConnection sender, QpidQmf qmf) {
        this.sender = sender;
        this.rulesCurator = rulesCurator;
        this.candlepinCache = candlepinCache;
        Map<String, String> map = VersionUtil.getVersionMap();
        version = map.get("version");
        release = map.get("release");

        if (config == null || !config.getBoolean(ConfigProperties.STANDALONE)) {
            standalone = false;
        }
        this.jsProvider = jsProvider;
        this.qmf = qmf;
    }

    /**
     * Retrieves the Status of the System
     * <p>
     * <pre>
     * {
     *   "result" : true,
     *   "version" : "0.9.10",
     *   "rulesVersion" : "5.8",
     *   "release" : "1",
     *   "standalone" : true,
     *   "timeUTC" : [date],
     *   "managerCapabilities" : [ "cores", "ram", "instance_multiplier" ],
     *   "rulesSource" : "DEFAULT"
     * }
     * </pre>
     * <p>
     * Status to see if a server is up and running
     *
     * @return a Status object
     * @httpcode 200
     */
    @GET
    @ApiOperation(value = "Status", notes = "Returns status of the server", authorizations = {})
    @Produces({ MediaType.APPLICATION_JSON})
    @SecurityHole(noAuth = true, anon = true)
    public Status status() {
        Cache<String, Status> statusCache = candlepinCache.getStatusCache();

        Status cached = statusCache.get(CandlepinCache.STATUS_KEY);

        if (cached != null) {
            return cached;
        }

        /*
         * Originally this was used to indicate database connectivity being good/bad.
         * In reality it could never be false, the request would fail. This check has
         * been moved to GET /status/db.
         */
        boolean good = true;
        try {
            rulesCurator.getUpdatedFromDB();
        }
        catch (Exception e) {
            log.error("Error checking database connection", e);
            good = false;
        }

        Status status = new Status(good, version, release, standalone,
            jsProvider.getRulesVersion(), jsProvider.getRulesSource());

        statusCache.put(CandlepinCache.STATUS_KEY, status);
        return status;
    }

    //TODO this must be removed before merging the devel branch
    //into master. By the time the dev branch is ready to hit
    //master, the reconnection will be tested using other means
    //then manually this way
    @GET
    @ApiOperation(value = "Reconnect", notes = "Reconnects to the Qpid broker", authorizations = {})
    @Path("/recon")
    @SecurityHole(noAuth = true, anon = true)
    public String recon() throws Exception {
        sender.connect();
        return "{\"status\":\"OK reconnected\"}";
    }

    // TODO remove this later before merging to master.
    @GET
    @ApiOperation(value = "BrokerStatus", notes = "QpidStatus", authorizations = {})
    @Path("/brokerStatus")
    @SecurityHole(noAuth = true, anon = true)
    public String brokerStatus() throws Exception {
        return "{\"status\":\"" + qmf.getStatus() + "\"}";
    }

}
