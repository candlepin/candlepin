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
package org.candlepin.messaging;



/**
 * The CPMProducer interface defines methods for sending messages to an addressable broker.
 */
public interface CPMProducer extends AutoCloseable {

    /**
     * Fetches a string representing the provider of this provider.
     *
     * @return
     *  The provider of this provider
     */
    String getProvider();

    /**
     * Fetches the session from which this producer was created
     *
     * @return
     *  the session that created this producer
     */
    CPMSession getSession();

    /**
     * Closes this producer. If this producer has already been closed, this method does nothing.
     *
     * @throws CPMException
     *  if an error occurs while attempting to close the producer
     */
    void close() throws CPMException;

    /**
     * Checks if this producer has been closed.
     *
     * @return
     *  true if this producer has been closed; false otherwise
     */
    boolean isClosed();

    /**
     * Sends a message to the specified address. If the message cannot be sent, this method throws
     * a CPMException. If the provider has already been closed, this method throws an
     * IllegalStateException.
     *
     * @param address
     *  the address to which the message will be sent
     *
     * @param message
     *  the message to send
     *
     * @throws IllegalStateException
     *  if this producer is closed
     *
     * @throws CPMException
     *  if the message cannot be sent
     */
    void send(String address, CPMMessage message) throws CPMException;

}
