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

import org.candlepin.messaging.CPMException;
import org.candlepin.messaging.CPMSession;
import org.candlepin.messaging.CPMSessionConfig;
import org.candlepin.messaging.CPMSessionFactory;



/**
 * CPMSessionFactory which does nothing, and will not create sessions. Used for environments in
 * which no provider is configured or available.
 */
//@Singleton
//@Component
public class NoopSessionFactory implements CPMSessionFactory {

    /**
     * {@inheritDoc}
     */
    @Override
    public String getProvider() {
        return "noop";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize() throws CPMException {
        // Intentionally left empty
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isInitialized() {
        // This is technically wrong, but we don't care in this case.
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CPMSessionConfig createSessionConfig() {
        // We don't do anything, so the default configuration is fine.
        return new CPMSessionConfig();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CPMSession createSession() throws CPMException {
        return this.createSession(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CPMSession createSession(CPMSessionConfig config) throws CPMException {
        throw new UnsupportedOperationException("No provider configured");
    }

}
