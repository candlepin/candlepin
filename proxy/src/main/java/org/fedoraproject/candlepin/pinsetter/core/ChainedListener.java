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
package org.fedoraproject.candlepin.pinsetter.core;

import org.quartz.JobExecutionContext;
import org.quartz.Trigger;
import org.quartz.TriggerListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Propagates Quartz trigger events to an internal chain
 * of TriggerListeners
 *
 * @version $Rev$
 */
public class ChainedListener implements TriggerListener {

    public static final String LISTENER_NAME = "ChainedListener";

    private List<TriggerListener> listenerChain = new ArrayList<TriggerListener>();

    /**
     * {@inheritDoc}
     */
    public String getName() {
        return ChainedListener.LISTENER_NAME;
    }

    /**
     * {@inheritDoc}
     */
    public void triggerComplete(Trigger trigger, JobExecutionContext ctx,
            int triggerInstructionCode) {
        for (TriggerListener listener : listenerChain) {
            listener.triggerComplete(trigger, ctx, triggerInstructionCode);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void triggerFired(Trigger trigger, JobExecutionContext ctx) {
        for (TriggerListener listener : listenerChain) {
            listener.triggerFired(trigger, ctx);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void triggerMisfired(Trigger trigger) {
        for (TriggerListener listener : listenerChain) {
            listener.triggerMisfired(trigger);
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean vetoJobExecution(Trigger trigger, JobExecutionContext ctx) {
        boolean retval = false;
        for (TriggerListener listener : listenerChain) {
            boolean tmp = listener.vetoJobExecution(trigger, ctx);
            if (!retval && tmp) {
                retval = true;
            }
        }
        return retval;
    }

    /**
     * Adds a new listener to the listener chain
     * @param listener listener to be added
     */
    public void addListener(TriggerListener listener) {
        if (listenerChain.indexOf(listener) == -1) {
            listenerChain.add(listener);
        }
    }
}
