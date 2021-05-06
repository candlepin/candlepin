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
 * The session represents a connection to a given broker, and is responsible for creating
 * producers and consumers to send and receive messages from that broker, respectively.
 */
public interface CPMSession extends AutoCloseable {

    /**
     * Fetches a string representing the provider of this session.
     *
     * @return
     *  The provider of this session
     */
    String getProvider();

    /**
     * Starts this session if it is currently stopped. If the session is already running,
     * this method does nothing.
     *
     * @throws CPMException
     *  if an error occurs while attempting to start the session
     */
    void start() throws CPMException;

    /**
     * Stops this session, potentially without disconnecting. While a session is stopped, producers
     * it created are unable to send messages, and consumers will not receive messages until the
     * session is restarted. If the session is already stopped, this method does nothing.
     *
     * @throws CPMException
     *  if an error occurs while attempting to stop the session
     */
    void stop() throws CPMException;

    /**
     * Closes this session, and any producers or consumers created from it. Once a session has been
     * closed, it cannot be restarted and must be recreated from the appropriate session factory.
     * If the session has already been closed, this method does nothing.
     *
     * @throws CPMException
     *  if an error occurs while attempting to close the session
     */
    void close() throws CPMException;

    /**
     * Checks if this session has been closed.
     *
     * @return
     *  true if the session has been closed; false otherwise
     */
    boolean isClosed();

    /**
     * Commits pending message transactions on the session. If the session is not a transactional
     * session, or there are no pending transactions, this method does nothing.
     *
     * @throws CPMException
     *  if an error occurs while attempting to commit the current transaction
     */
    void commit() throws CPMException;

    /**
     * Rolls back any pending message transactions on this session. If the session is not a
     * transactional session, or there are no pending transactions, this method does nothing.
     */
    void rollback() throws CPMException;

    /**
     * Creates a new producer configuration instance with the factory-default settings.
     *
     * @return
     *  a new producer configuration instance with the factory-default settings
     */
    CPMProducerConfig createProducerConfig();

    /**
     * Creates a new consumer configuration instance with the factory-default settings.
     *
     * @return
     *  a new consumer configuration instance with the factory-default settings
     */
    CPMConsumerConfig createConsumerConfig();

    /**
     * Creates a new message configuration instance with the factory-default settings.
     *
     * @return
     *  a new message configuration instance with the factory-default settings
     */
    CPMMessageConfig createMessageConfig();

    /**
     * Creates a new producer using the default producer configuration.
     *
     * @throws CPMException
     *  if an error occurs while creating the producer
     *
     * @return
     *  a newly created message producer, backed by this session
     */
    CPMProducer createProducer() throws CPMException;

    /**
     * Creates a new producer using the specified producer configuration. If the provided
     * configuration is null, the default configuration will be used instead.
     *
     * @param config
     *  the configuration to use when creating the producer, or null to use the default
     *  configuration
     *
     * @throws CPMException
     *  if an error occurs while creating the producer
     *
     * @return
     *  a newly created message producer, backed by this session
     */
    CPMProducer createProducer(CPMProducerConfig config) throws CPMException;

    /**
     * Creates a new consumer using the default consumer configuration.
     *
     * @throws CPMException
     *  if an error occurs while creating the consumer
     *
     * @return
     *  a newly created message consumer, backed by this session
     */
    CPMConsumer createConsumer() throws CPMException;

    /**
     * Creates a new consumer using the specified consumer configuration. If the provided
     * configuration is null, the default configuration will be used instead.
     *
     * @param config
     *  the configuration to use when creating the consumer, or null to use the default
     *  configuration
     *
     * @throws CPMException
     *  if an error occurs while creating the consumer
     *
     * @return
     *  a newly created message consumer, backed by this session
     */
    CPMConsumer createConsumer(CPMConsumerConfig config) throws CPMException;

    /**
     * Creates a new message using the default message configuration.
     *
     * @throws CPMException
     *  if an error occurs while creating the message
     *
     * @return
     *  a new message instance, backed by this session
     */
    CPMMessage createMessage() throws CPMException;

    /**
     * Creates a new message using the specified message configuration. If the provided
     * configuration is null, the default configuration will be used instead.
     *
     * @param config
     *  the configuration to use when creating the message, or null to use the default
     *  configuration
     *
     * @throws CPMException
     *  if an error occurs while creating the message
     *
     * @return
     *  a new message instance, backed by this session
     */
    CPMMessage createMessage(CPMMessageConfig config) throws CPMException;

}
