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
package org.candlepin.pinsetter.core;

import com.google.inject.Inject;
import org.candlepin.controller.ModeManager;
import org.candlepin.model.CandlepinModeChange.Mode;
import org.quartz.JobExecutionContext;
import org.quartz.Trigger;
import org.quartz.listeners.TriggerListenerSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This component receives events around job status and performs actions to
 * allow for the job in question to run outside of a request scope, as well as
 * record the status of the job for later retrieval.
 */
public class PinsetterTriggerListener extends TriggerListenerSupport {
    private static Logger log = LoggerFactory.getLogger(PinsetterTriggerListener.class);
    private ModeManager modeManager;

    @Inject
    public PinsetterTriggerListener(ModeManager modeManager) {
        this.modeManager = modeManager;
    }

    @Override
    public String getName() {
        return "Suspend mode trigger listener";
    }

    @Override
    public boolean vetoJobExecution(Trigger trigger, JobExecutionContext context) {
        if (modeManager.getLastCandlepinModeChange().getMode() == Mode.SUSPEND) {
            log.debug("Pinsetter trigger listener detected SUSPEND mode, " +
                "vetoing job to be executed");
            return true;
        }
        return false;
    }
}
