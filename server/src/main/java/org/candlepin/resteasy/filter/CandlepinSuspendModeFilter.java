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
package org.candlepin.resteasy.filter;

import org.candlepin.common.config.Configuration;
import org.candlepin.common.exceptions.SuspendedException;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.ModeManager;
import org.candlepin.model.CandlepinModeChange;
import org.candlepin.model.CandlepinModeChange.Mode;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.ext.Provider;

/**
 * Filter which check if Candlepin is currently in SUSPEND mode,
 * and if so, returns appropriate error.
 */
@PreMatching
@Provider
public class CandlepinSuspendModeFilter implements ContainerRequestFilter{
    private ModeManager modeManager;
    private Configuration config;

    @Inject
    public CandlepinSuspendModeFilter(ModeManager modeManager, ObjectMapper mapper,
        Configuration config) {
        this.modeManager = modeManager;
        this.config = config;
    }

    /**
     * When Candlepin is in SuspendMode, this filter returns an error to the client.
     * Status resource is still accessible, regardless of mode.
     */
    @Override
    public void filter(ContainerRequestContext requestContext)
        throws JsonProcessingException {

        if (!config.getBoolean(ConfigProperties.SUSPEND_MODE_ENABLED)) {
            return;
        }

        /**
         * Allow status resource
         */
        if (requestContext.getUriInfo().getPath().startsWith("/status")) {
            return;
        }

        CandlepinModeChange mode = modeManager.getLastCandlepinModeChange();
        if (mode.getMode() == Mode.SUSPEND) {
            throw new SuspendedException("Candlepin is in Suspend mode, " +
                "please check /status/mode resource to get more details");
        }
    }

}
