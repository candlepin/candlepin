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
 * The CPMConsumer interface defines methods for receiving messages from a message queue.
 */
public interface CPMConsumer extends AutoCloseable {

    /**
     * Fetches a string representing the provider of this consumer.
     *
     * @return
     *  The provider of this consumer
     */
    String getProvider();

    /**
     * Fetches the session from which this consumer was created
     *
     * @return
     *  the session that created this consumer
     */
    CPMSession getSession();

    /**
     * Closes this consumer. If this consumer has already been closed, this method does nothing.
     *
     * @throws CPMException
     *  if an error occurs while attempting to close the consumer
     */
    void close() throws CPMException;

    /**
     * Checks if this consumer has been closed.
     *
     * @return
     *  true if this consumer has been closed; false otherwise
     */
    boolean isClosed();

    /**
     * Fetches a message if one is available on the queue. If no message is available, this method
     * immediately returns null.
     *
     * @throws CPMException
     *  if an error occurs while fetching a message
     *
     * @return
     *  the message fetched from the queue, or null if no message is available
     */
    CPMMessage fetchMessage() throws CPMException;

    /**
     * Fetches a message from the queue. If a message is not available at the time this method is
     * called, it will wait indefinitely for a message to be received.
     *
     * @throws CPMException
     *  if an error occurs while fetching a message
     *
     * @return
     *  the message fetched from the queue
     */
    CPMMessage waitForMessage() throws CPMException;

    /**
     * Fetches a message from the queue. If a message is not available at the time this method is
     * called, it will wait the specified amount of time for a message to be received. If a message
     * is not received in the allotted time, this method returns null.
     *
     * @param timeout
     *  the maximum amount of time to wait for a message, in milliseconds
     *
     * @throws CPMException
     *  if an error occurs while fetching a message
     *
     * @return
     *  the message fetched from the queue, or null if a message was not received in the alloted
     *  time
     */
    CPMMessage waitForMessage(long timeout) throws CPMException;

    /**
     * Sets an asynchronous message listener to receive messages for this consumer. This method is
     * non-blocking, and further messages received by this consumer will be passed to the provided
     * listener as they are received.
     *
     * @param listener
     *  the listener to process messages received by this consumer
     *
     * @throws CPMException
     *  if an error occurs while setting the message listener
     *
     * @return
     *  a reference to this consumer
     */
    CPMConsumer setMessageListener(CPMMessageListener listener) throws CPMException;

    /**
     * Fetches the message listener assigned to this consumer. If a message listener has not been
     * set, this method returns null.
     *
     * @return
     *  the message listener assigned to this consumer, or null if a listener has not been set
     */
    CPMMessageListener getMessageListener();

}
