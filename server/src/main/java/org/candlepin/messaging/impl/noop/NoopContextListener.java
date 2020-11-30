/**
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.messaging.impl.noop;

import org.candlepin.messaging.CPMContextListener;
import org.candlepin.messaging.CPMException;



/**
 * CPMContextListener implementation which does nothing. Used for environments in which no provider
 * is configured or available.
 */
//@Singleton
public class NoopContextListener implements CPMContextListener {

    /**
     * {@inheritDoc}
     */
    @Override
    //public void initialize(Injector injector) throws CPMException {
    public void initialize() throws CPMException {
        // Intentionally left empty
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void destroy() throws CPMException {
        // Intentionally left empty
    }

}
