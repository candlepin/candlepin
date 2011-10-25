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

import org.fedoraproject.candlepin.auth.interceptor.SecurityHole;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.model.RulesCurator;
import org.fedoraproject.candlepin.model.Status;

import com.google.inject.Inject;

import org.apache.log4j.Logger;

import java.io.InputStream;
import java.util.Properties;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * Status Resource
 */
@Path("/status")
public class StatusResource {

    private static Logger log = Logger.getLogger(StatusResource.class);

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
    private Config config;

    @Inject
    public StatusResource(RulesCurator rulesCurator,
                          Config config) {
        this.rulesCurator = rulesCurator;
        try {
            InputStream in = this.getClass().getClassLoader().
                getResourceAsStream("candlepin_info.properties");
            Properties props = new Properties();
            props.load(in);
            if (props.containsKey("version")) {
                version = props.getProperty("version");
            }

            if (props.containsKey("release")) {
                release = props.getProperty("release");
            }
            in.close();
            if (!config.standalone()) {
                standalone = false;
            }
        }
        catch (Exception e) {
            log.error("Can not load candlepin_info.properties", e);
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
    @SecurityHole(noAuth = true)
    public Status status() {
        boolean good = true;
        try {
            rulesCurator.listAll();
        }
        catch (Exception e) {
            good = false;
        }
        Status status = new Status(good, version, release, standalone);
        return status;
    }
}
