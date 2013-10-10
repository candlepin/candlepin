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
package org.candlepin.servlet.filter.logging;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

/**
 * A very simple wrapper to store the response status. This avoids the memory overhead
 * of using the LoggingResponseWrapper used for debug logging, but still lets us log
 * the response status.
 */
public class StatusResponseWrapper extends HttpServletResponseWrapper {

    protected int status;

    public StatusResponseWrapper(HttpServletResponse resp) {
        super(resp);
    }

    public void setStatus(int status) {
        super.setStatus(status);
        this.status = status;
    }

    public void setStatus(int status, String sm) {
        super.setStatus(status, sm);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
