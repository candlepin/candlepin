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

import org.candlepin.auth.KeycloakConfiguration;
import org.candlepin.cache.CandlepinCache;
import org.candlepin.cache.StatusCache;
import org.candlepin.common.auth.SecurityHole;
import org.candlepin.common.config.Configuration;
import org.candlepin.common.util.VersionUtil;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.mode.CandlepinModeManager;
import org.candlepin.controller.mode.CandlepinModeManager.Mode;
import org.candlepin.controller.mode.ModeChangeReason;
import org.candlepin.dto.api.v1.KeycloakStatusDTO;
import org.candlepin.dto.api.v1.StatusDTO;
import org.candlepin.guice.CandlepinCapabilities;
import org.candlepin.model.Rules.RulesSourceEnum;
import org.candlepin.model.RulesCurator;
import org.candlepin.policy.js.JsRunnerFactory;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import org.keycloak.representations.adapters.config.AdapterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Objects;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;



/**
 * Status Resource
 */
@Component
@Transactional
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
    private boolean keycloakEnabled = true;
    private RulesCurator rulesCurator;
    /* TODO change the instance name to jsRunnerFactory */
    private JsRunnerFactory jsProvider;
    private CandlepinCache candlepinCache;
    private CandlepinModeManager modeManager;
    private KeycloakConfiguration keycloakConfig;

    //@Inject
    @Autowired
    public StatusResource(RulesCurator rulesCurator, Configuration config, JsRunnerFactory jsProvider,
        CandlepinCache candlepinCache, CandlepinModeManager modeManager,
        KeycloakConfiguration keycloakConfig) {

        this.rulesCurator = Objects.requireNonNull(rulesCurator);
        this.jsProvider = Objects.requireNonNull(jsProvider);
        this.candlepinCache = Objects.requireNonNull(candlepinCache);
        this.modeManager = Objects.requireNonNull(modeManager);
        this.keycloakConfig = Objects.requireNonNull(keycloakConfig);

        Map<String, String> map = VersionUtil.getVersionMap();
        version = map.get("version");
        release = map.get("release");

        if (config != null && !config.getBoolean(ConfigProperties.STANDALONE)) {
            this.standalone = false;
        }

        if (config == null || !config.getBoolean(ConfigProperties.KEYCLOAK_AUTHENTICATION)) {
            this.keycloakEnabled = false;
        }
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
    public StatusDTO status() {
        StatusCache statusCache = candlepinCache.getStatusCache();
        StatusDTO cached = statusCache.getStatus();

        if (cached != null) {
            return cached;
        }

        CandlepinCapabilities caps = CandlepinCapabilities.getCapabilities();
        RulesSourceEnum rulesSource = this.jsProvider.getRulesSource();

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

        Mode mode = this.modeManager.getCurrentMode();
        ModeChangeReason mcr = this.getOldestReason(this.modeManager.getModeChangeReasons());

        good = good && (mode == Mode.NORMAL);

        StatusDTO status;
        if (keycloakEnabled) {
            AdapterConfig adapterConfig = keycloakConfig.getAdapterConfig();
            status = new KeycloakStatusDTO()
                .setKeycloakResource(adapterConfig.getResource())
                .setKeycloakAuthUrl(adapterConfig.getAuthServerUrl())
                .setKeycloakRealm(adapterConfig.getRealm());
        }
        else {
            status = new StatusDTO();
        }

        status
            .setResult(good)
            .setVersion(version)
            .setRelease(release)
            .setStandalone(standalone)
            .setRulesVersion(jsProvider.getRulesVersion())
            .setRulesSource(rulesSource != null ? rulesSource.toString() : null)
            .setMode(mode != null ? mode.toString() : null)
            .setModeReason(mcr != null ? mcr.toString() : null)
            .setModeChangeTime(mcr != null ? mcr.getTime() : null)
            .setManagerCapabilities(caps)
            .setTimeUTC(new Date());

        statusCache.setStatus(status);

        return status;
    }

    /**
     * Fetches the oldest reason in the provided collection of reasons. If the collection is empty,
     * or only contains null values, this method returns null.
     *
     * @param reasons
     *  The collection of reasons to evaluate
     *
     * @return
     *  the oldest reason from the provided collection, or null if the collection does not contain
     *  any reasons
     */
    private ModeChangeReason getOldestReason(Collection<ModeChangeReason> reasons) {
        try {
            return reasons.stream()
                .filter(Objects::nonNull)
                .min((lhs, rhs) -> lhs.getTime().compareTo(rhs.getTime()))
                .orElse(null);
        }
        catch (NullPointerException e) {
            return null;
        }
    }
}
