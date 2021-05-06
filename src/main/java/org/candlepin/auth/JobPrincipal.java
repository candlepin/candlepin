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
package org.candlepin.auth;



/**
 * The JobPrincipal is used during the execution of a job to provide system-level access for the job
 * operation, while inheriting the name of the principal which triggered the job.
 */
public class JobPrincipal extends SystemPrincipal {
    private static final long serialVersionUID = 1L;

    private final String name;

    public JobPrincipal(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("principal name is null or empty");
        }

        this.name = name;
    }

    @Override
    public String getName() {
        return this.name;
    }
}
