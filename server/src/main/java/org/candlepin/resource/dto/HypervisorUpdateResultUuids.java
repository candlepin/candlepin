/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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

import org.candlepin.model.Consumer;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * DTO that encapsulates the results from
 * <code>HypervisorResource.hypervisorCheckInAsync</code>.
 *
 * <pre>
 *     created: List of consumer UUIDS that have just been created.
 *     updated: List of host consumers UUIDs that have had their guest IDs updated.
 *     unchanged: List of host consumers UUIDs that have not been changed.
 *     failed: a list of strings formated as '{host_virt_id}: Error message'.
 * </pre>
 */
public class HypervisorUpdateResultUuids implements Serializable {
    // TODO Once we have removed the need to store serialized java objects as the resultData of JobStatus
    // this class (and HypervisorUpdateResult) should be removed and replaced.
    private static final long serialVersionUID = -42133742L;
    private Set<String> created;
    private Set<String> updated;
    private Set<String> unchanged;
    private Set<String> failed;

    public HypervisorUpdateResultUuids() {
        this.created = new HashSet<String>();
        this.updated = new HashSet<String>();
        this.unchanged = new HashSet<String>();
        this.failed = new HashSet<String>();
    }

    public boolean wasCreated(Consumer c) {
        return created.contains(c.getUuid());
    }

    public boolean wasUpdated(Consumer c) {
        return updated.contains(c.getUuid());
    }

    public boolean wasUnchanged(Consumer c) {
        return unchanged.contains(c.getUuid());
    }

    public boolean wasCreated(String uuid) {
        return created.contains(uuid);
    }

    public boolean wasUpdated(String uuid) {
        return updated.contains(uuid);
    }

    public boolean wasUnchanged(String uuid) {
        return unchanged.contains(uuid);
    }

    public void created(Consumer c) {
        this.created.add(c.getUuid());
    }

    public void updated(Consumer c) {
        this.updated.add(c.getUuid());
    }

    public void unchanged(Consumer c) {
        this.unchanged.add(c.getUuid());
    }

    public void created(String uuid) {
        this.created.add(uuid);
    }

    public void updated(String uuid) {
        this.updated.add(uuid);
    }

    public void unchanged(String uuid) {
        this.unchanged.add(uuid);
    }

    public void failed(String hostVirtId, String errorMessage) {
        String error = errorMessage == null ? "" : errorMessage;
        this.failed.add(hostVirtId + ": " + error);
    }

    public Set<String> getCreated() {
        return created;
    }

    public Set<String> getUpdated() {
        return updated;
    }

    public Set<String> getUnchanged() {
        return unchanged;
    }

    public Set<String> getFailedUpdate() {
        return failed;
    }

    @Override
    public String toString() {
        return "Created: " + created.size() + ", Updated: " + updated.size() +
            ", Unchanged:" + unchanged.size() + ", Failed: " + failed.size();
    }

}
