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
package org.candlepin.controller;

import org.candlepin.model.CandlepinModeChange;
import org.candlepin.model.CandlepinModeChange.Mode;
import org.candlepin.model.CandlepinModeChange.Reason;

/**
 * Class responsible for storing and managing the current Candlepin mode.
 */
public interface ModeManager {
    /**
     * Last mode change that happended. It is guaranteed that there
     * will always be some last mode change (at least on startup the
     * Candlepin is starting NORMAL mode)
     * @return Information about the last mode transition
     */
    CandlepinModeChange getLastCandlepinModeChange();
    /**
     * Enters a mode m. Reason should be a machine readable enumeration
     * @param m new Mode into which Candlepin should enter
     * @param reason why is Candlepin entering this mode?
     */
    void enterMode(Mode m, Reason reason);
    /**
     * Checks if Candlepin is in suspend mode. In case it is, this method
     * will throw an error. This method is useful to quickly fail requests
     * that are running while Candlepin changes mode.
     */
    void throwRestEasyExceptionIfInSuspendMode();
    /**
     * Register a listener that will be notified when Candlepin mode changes
     * @param list
     */
    void registerModeChangeListener(ModeChangeListener list);
}
