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
package org.candlepin.resource.dto;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ContentAccessListing class dto for return data for content access API
 */
public class ContentAccessListing {
    private Date lastUpdate;
    private Map<Long, List<String>> content = new HashMap<>();

    public ContentAccessListing setLastUpdate(Date lastUpdate) {
        this.lastUpdate = lastUpdate;
        return this;
    }

    public Date getLastUpdate() {
        return this.lastUpdate;
    }

    public ContentAccessListing setContentListing(Long serial, List<String> contentListing) {
        this.content.put(serial, contentListing);
        return this;
    }

    public Map<Long, List<String>> getContentListing() {
        return this.content;
    }
}
