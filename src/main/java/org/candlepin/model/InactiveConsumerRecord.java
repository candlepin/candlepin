/*
 * Copyright (c) 2009 - 2025 Red Hat, Inc.
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
package org.candlepin.model;



/**
 * A record containing the consumer UUID and owner key, representing an "inactive" consumer.
 *
 * @param consumerId
 *  The ID of the inactive consumer
 *
 * @param consumerUuid
 *  The UUID of the inactive consumer
 *
 * @param ownerKey
 *  The key of the owner the inactive consumer belongs to
 *
 * @param isOwnerAnonymous
 *  The anonymous state of the owner that the inactive consumer belongs to
 */
public record InactiveConsumerRecord(String consumerId, String consumerUuid, String ownerKey,
    Boolean isOwnerAnonymous) {

    // intentionally left empty
}
