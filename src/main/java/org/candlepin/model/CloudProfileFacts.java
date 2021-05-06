/**
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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
 * The subscription profile time stamp should also get updated
 * on the specific Facts updates.
 * This Enum helps to configure more facts if required in the future.
 */
public enum CloudProfileFacts {

    CPU_CORES_PERSOCKET("cpu.core(s)_per_socket"),
    CPU_SOCKETS("cpu.cpu_socket(s)"),
    DMI_BIOS_VENDOR("dmi.bios.vendor"),
    DMI_BIOS_VERSION("dmi.bios.version"),
    DMI_CHASSIS_ASSET_TAG("dmi.chassis.asset_tag"),
    DMI_SYSTEM_MANUFACTURER("dmi.system.manufacturer"),
    DMI_SYSTEM_UUID("dmi.system.uuid"),
    MEMORY_MEMTOTAL("memory.memtotal"),
    OCM_UNITS("ocm.units"),
    UNAME_MACHINE("uname.machine"),
    VIRT_IS_GUEST("virt.is_guest");

    private final String fact;

    CloudProfileFacts(String fact) {
        this.fact = fact;
    }

    public String getFact() {
        return this.fact;
    }

    @Override
    public String toString() {
        return fact;
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
