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

import org.candlepin.auth.interceptor.SecurityHole;
import org.candlepin.config.Config;
import org.candlepin.model.RulesCurator;
import org.candlepin.model.Status;
import org.candlepin.util.VersionUtil;

import com.google.inject.Inject;

import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * Status Resource
 */
@Path("/status")
public class StatusResource {

    /**
     * The current version of candlepin
     */
    private String version = "Unknown";

    /**
     * The current git release
     */
    private String release = "Unknown";

    private boolean standalone = true;

    private RulesCurator rulesCurator;

    @Inject
    public StatusResource(RulesCurator rulesCurator,
                          Config config) {
        this.rulesCurator = rulesCurator;

        Map<String, String> map = VersionUtil.getVersionMap();
        version = map.get("version");
        release = map.get("release");

        if (config == null || !config.standalone()) {
            standalone = false;
        }
    }

    /**
     * status to see if a server is up and running
     *
     * @return the running status
     * @httpcode 200
     */
    @GET
    @Produces({ MediaType.APPLICATION_JSON})
    @SecurityHole(noAuth = true, anon = true)
    public Status status() {
        boolean good = true;
        try {
            if (rulesCurator != null) {
                rulesCurator.listAll();
            }
        }
        catch (Exception e) {
            good = false;
        }
        Status status = new Status(good, version, release, standalone);
        return status;
    }
}
