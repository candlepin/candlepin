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
package org.candlepin.model;

import org.candlepin.util.Util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Maintains a mapping of virt UUIDs to Candlepin Consumers.
 *
 * Virt UUID could be either a guest or hypervisor ID. Helps to perform lookups
 * involving endianness discrepancies and account for differences in case of the UUID.
 */
public class VirtConsumerMap {

    private static Logger log = LoggerFactory.getLogger(VirtConsumerMap.class);

    private Map<String, Consumer> virtUuidToConsumerMap;

    public VirtConsumerMap() {
        virtUuidToConsumerMap = new HashMap<String, Consumer>();
    }


    public void add(String virtUuid, Consumer consumer) {
        // Assumes caller has already sorted by updated date (desc), so if we already have
        // this virt UUID, just skip the later one.
        if (virtUuidToConsumerMap.containsKey(virtUuid)) {
            return;
        }
        virtUuidToConsumerMap.put(virtUuid, consumer);
    }

    /**
     * Return the consumer for the given virt UUID, should one exist.
     *
     * Will check both uuid as given, lower case, and lower case with endianness swapped.
     *
     * @param virtUuid
     * @return Consumer mapping to the given virt UUID, or none if none exists.
     */
    public Consumer get(String virtUuid) {
        if (virtUuidToConsumerMap.containsKey(virtUuid)) {
            return virtUuidToConsumerMap.get(virtUuid);
        }

        String virtUuidLower = virtUuid.toLowerCase();
        if (virtUuidToConsumerMap.containsKey(virtUuidLower)) {
            return virtUuidToConsumerMap.get(virtUuidLower);
        }

        String virtUuidLowerEndian = Util.transformUuid(virtUuidLower);
        if (virtUuidToConsumerMap.containsKey(virtUuidLowerEndian)) {
            return virtUuidToConsumerMap.get(virtUuidLowerEndian);
        }

        return null;
    }

    public int size() {
        return virtUuidToConsumerMap.keySet().size();
    }

}
