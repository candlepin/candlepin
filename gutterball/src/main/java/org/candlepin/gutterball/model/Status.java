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
package org.candlepin.gutterball.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.candlepin.common.config.Configuration;
import org.xnap.commons.i18n.I18n;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.Inject;

/**
 * Status
 */
@XmlRootElement(name = "status")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class Status {

    // TODO can we inject version directly from the configuration?
    private String version;
    private String requestLocale;

    @Inject
    public Status(I18n i18n, Configuration config) {
        version = config.getString("gutterball.version", i18n.tr("Unknown"));
        requestLocale = i18n.getLocale().toString();
    }

    @JsonProperty("gutterball.version")
    public String getVersion() {
        return version;
    }

    @JsonProperty("request_locale")
    public String getRequestLocale() {
        return requestLocale;
    }
}
