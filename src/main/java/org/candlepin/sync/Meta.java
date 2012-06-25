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
package org.candlepin.sync;

import java.util.Date;

/**
 * Meta
 */
public class Meta {
    private String version;
    private Date created;
    private String principalName;
    private String webAppPrefix;

    public Meta() {
        this("0.0.0", new Date(), "", null);
    }

    public Meta(String version, Date creation,
                String userName, String webAppPrefix) {
        this.version = version;
        this.created = creation;

        if (version == null) {
            this.version = "0.0.0";
        }

        if (creation == null) {
            this.created = new Date();
        }

        this.principalName = userName;
        this.webAppPrefix = webAppPrefix;
    }

    public String getVersion() {
        return version;
    }

    public Date getCreated() {
        return created;
    }

    public String getPrincipalName() {
        return principalName;
    }

    public String getWebAppPrefix() {
        return webAppPrefix;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public void setPrincipalName(String name) {
        this.principalName = name;
    }

    public void setWebAppPrefix(String prefix) {
        this.webAppPrefix = prefix;
    }

}
