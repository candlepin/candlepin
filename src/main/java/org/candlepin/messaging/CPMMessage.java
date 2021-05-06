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
 * The CPMMessage interface represents a minimal set of AMQP messaging features used within
 * Candlepin. It is not intended to be feature-complete with all supported AMQP implementations,
 * and may be lacking advanced functionality. Such functionality should be added to this
 * interface as appropriate.
 */
public interface CPMMessage {

    /**
     * Fetches a string representing the provider of this message.
     *
     * @return
     *  The provider of this message
     */
    String getProvider();

    /**
     * Fetches the ID of this message
     *
     * @return
     *  the ID of this message
     */
    String getMessageId();

    /**
     * Acknowledges the receipt of this message.
     *
     * @throws CPMException
     *  if an error occurs while acknowledging the message
     */
    void acknowledge() throws CPMException;

    /**
     * Sets whether or not this message should be sent "durably," indicating that the message should
     * be available to clients that were offline at the time the message was sent.
     *
     * @param durable
     *  Whether or not to flag this message as durable
     *
     * @return
     *  a reference to this message
     */
    CPMMessage setDurable(boolean durable);

    /**
     * Checks if this message is durable, or was sent durably.
     *
     * @return
     *  true if this message is durable; false otherwise
     */
    boolean isDurable();


    // Message details
    /**
     * Fetches the address this message was received from
     *
     * @return
     *  the address of this message
     */
    String getAddress();

    /**
     * Sets the content body of this message.
     *
     * @param body
     *  the body of the content to set for this message
     *
     * @return
     *  a reference to this message
     */
    CPMMessage setBody(String body);

    /**
     * Fetches the body of this message.
     *
     * @return
     *  the body of the content of this message
     */
    String getBody();


    // Message properties
    /**
     * Sets the specified property for this message.
     *
     * @param key
     *  the property name, or key, to set
     *
     * @param value
     *  the value to set
     *
     * @return
     *  a reference to this message
     */
    CPMMessage setProperty(String key, String value);

    /**
     * Fetches the value of the specified property of this message. If the property is not set for
     * this message, this method returns null.
     *
     * @param key
     *  the property name, or key, to read
     *
     * @return
     *  the value associated with the specified property, or null if the property is not set
     */
    String getProperty(String key);

}
