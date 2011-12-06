/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.candlepin.resource.dto;

import java.util.HashSet;
import java.util.Set;

import org.candlepin.model.Consumer;

/**
 * DTO that encapsulates the results from
 * <code>HypervisorResource.hypervisorCheckIn</code>.
 *
 * <pre>
 *     created: the consumers that have been created from the host ids.
 *     updated: the host's virt ids of host consumers that have been updated.
 *     failed: a list of strings formated as '{host_virt_id}: Error message'.
 * </pre>
 */
public class HypervisorCheckInResult {

    private Set<Consumer> created;
    private Set<Consumer> updated;
    private Set<String> failed;

    public HypervisorCheckInResult() {
        this.created = new HashSet<Consumer>();
        this.updated = new HashSet<Consumer>();
        this.failed = new HashSet<String>();
    }

    public void created(Consumer c) {
        this.created.add(c);
    }

    public void updated(Consumer c) {
        this.updated.add(c);
    }

    public void failed(String hostVirtId, String errorMessage) {
        String error = errorMessage == null ? "" : errorMessage;
        this.failed.add(hostVirtId + ": " + error);
    }

    public Set<Consumer> getCreated() {
        return created;
    }

    public Set<Consumer> getUpdated() {
        return updated;
    }

    public Set<String> getFailedUpdate() {
        return failed;
    }
}
