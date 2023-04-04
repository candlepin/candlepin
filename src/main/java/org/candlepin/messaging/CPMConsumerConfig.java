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
 * The CPMConsumerConfig class provides standardized configuration options to be provided to the
 * session when creating CPM consumers.
 */
public class CPMConsumerConfig {

    private String queue;
    private String messageFilter;


    /**
     * Creates a new CPMConsumerConfig instance with the default consumer configuration.
     */
    public CPMConsumerConfig() {
        // Add defaults here as necessary
    }

    /**
     * Sets the message queue consumers created from this configuration will read.
     *
     * @param queue
     *  the queue consumers created from this configuration will read
     *
     * @return
     *  a reference to this consumer config
     */
    public CPMConsumerConfig setQueue(String queue) {
        this.queue = queue;
        return this;
    }

    /**
     * Fetches the queue consumers created from this configuration will read. If the queue has not
     * yet been set, this method returns null.
     *
     * @return
     *  the queue consumers created from this configuration will read, or null if the queue has not
     *  been set
     */
    public String getQueue() {
        return this.queue;
    }

    /**
     * Sets the message filter to apply to consumers created from this configuration.
     * <p></p>
     * Note that the filter format is implementation-specific, and it may be necessary to examine
     * the provider to choose an appropriate filter string.
     *
     * @param filter
     *  the message filter string to apply to consumers created from this configuration
     *
     * @return
     *  a reference to this consumer config
     */
    public CPMConsumerConfig setMessageFilter(String filter) {
        this.messageFilter = filter;
        return this;
    }

    /**
     * Fetches the message filter string that will be applied to consumers created from this
     * configuration. If the message filter has not been set, this method returns null.
     *
     * @return
     *  the message filter string that will be applied to consumers created from this configuration,
     *  or null if the message filter has not been set
     */
    public String getMessageFilter() {
        return this.messageFilter;
    }

}
