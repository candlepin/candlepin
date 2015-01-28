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

import com.fasterxml.jackson.annotation.JsonProperty;

import org.xnap.commons.i18n.I18n;

import javax.inject.Provider;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Status
 */
@XmlRootElement(name = "status")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class Status {

    private String version;
    private String release;
    private String requestLocale;

    public Status(Provider<I18n> i18nProvider, String ver, String rel) {
        requestLocale = i18nProvider.get().getLocale().toString();
        version = ver;
        release = rel;
    }

    public String getVersion() {
        return version;
    }

    public String getRelease() {
        return release;
    }

    @JsonProperty("request_locale")
    public String getRequestLocale() {
        return requestLocale;
    }
}
