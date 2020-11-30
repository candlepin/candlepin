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
package org.candlepin.messaging;

/**
 * The CPMContextListener interface provides a messaging implementation a standardized means of
 * hooking into the startup and shutdown processes of Candlepin. Any background services or
 * functions it needs to create should be done in the startup and shutdown methods as appropriate.
 */
public interface CPMContextListener {

//    /**
//     * Called when the Candlepin context is initialized, allowing for the backing messaging
//     * implementation to create any required services or functionality.
//     *
//     * @param injector
//     *  an injector to use for fetching object instances
//     *
//     * @throws CPMException
//     *  if
//     */
    //void initialize(Injector injector) throws CPMException;
    void initialize() throws CPMException;
    /**
     * Called when the Candlepin context is destroyed, indicating the messaging implementation
     * should shut down any backing services or functionality associated with it.
     */
    void destroy() throws CPMException;

}
