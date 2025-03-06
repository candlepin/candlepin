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
package org.candlepin.model;

/**
 * The CloudProfileFacts enum is a collection of consumer facts which are critical for maintaining
 * a cloud profile in some client applications such as Subscription Watch.
 *
 * The facts in this enum come from the consumer facts, and are primarily used to determine whether
 * or not to update a given consumer's cloud profile "last update" timestamp.
 */
public enum CloudProfileFacts {

    CPU_CORES_PER_SOCKET(Consumer.Facts.CPU_CORES_PER_SOCKET),
    CPU_SOCKETS(Consumer.Facts.CPU_SOCKETS),
    DMI_BIOS_VENDOR(Consumer.Facts.DMI_BIOS_VENDOR),
    DMI_BIOS_VERSION(Consumer.Facts.DMI_BIOS_VERSION),
    DMI_CHASSIS_ASSET_TAG(Consumer.Facts.DMI_CHASSIS_ASSET_TAG),
    DMI_SYSTEM_MANUFACTURER(Consumer.Facts.DMI_SYSTEM_MANUFACTURER),
    DMI_SYSTEM_UUID(Consumer.Facts.DMI_SYSTEM_UUID),
    MEMORY_MEMTOTAL(Consumer.Facts.MEMORY_MEMTOTAL),
    OCM_UNITS(Consumer.Facts.OCM_UNITS),
    ARCHITECTURE(Consumer.Facts.ARCHITECTURE),
    VIRT_IS_GUEST(Consumer.Facts.VIRT_IS_GUEST);

    private final String fact;

    CloudProfileFacts(String fact) {
        this.fact = fact;
    }

    public String getFact() {
        return this.fact;
    }

    @Override
    public String toString() {
        return this.fact;
    }

    public static boolean containsFact(String incomingFact) {
        for (CloudProfileFacts fact : CloudProfileFacts.values()) {
            if (fact.getFact().equals(incomingFact)) {
                return true;
            }
        }

        return false;
    }
}
