/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import org.candlepin.config.Configuration;
import org.candlepin.controller.mode.CandlepinModeManager;
import org.candlepin.controller.mode.CandlepinModeManager.Mode;
import org.candlepin.exceptions.SuspendedException;

import org.xnap.commons.i18n.I18n;

import tools.jackson.databind.ObjectMapper;

import javax.inject.Inject;
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
public class CandlepinSuspendModeFilter implements ContainerRequestFilter {
    private CandlepinModeManager modeManager;
    private Configuration config;
    private I18n i18n;

    @Inject
    public CandlepinSuspendModeFilter(CandlepinModeManager modeManager, ObjectMapper mapper,
        Configuration config, I18n i18n) {

        this.modeManager = modeManager;
        this.config = config;
        this.i18n = i18n;
    }

    /**
     * When Candlepin is in SuspendMode, this filter returns an error to the client.
     * Part of status resource is still accessible, regardless of mode.
     */
    @Override
    public void filter(ContainerRequestContext requestContext) {

        // Allow status every time
        if (requestContext.getUriInfo().getPath().startsWith("/status")) {
            return;
        }

        if (this.modeManager.getCurrentMode() == Mode.SUSPEND) {
            throw new SuspendedException(i18n.tr("Candlepin is in Suspend mode, " +
                "please check /status resource to get more details"));
        }
    }

}
