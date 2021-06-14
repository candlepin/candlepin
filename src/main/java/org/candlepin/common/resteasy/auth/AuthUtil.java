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
package org.candlepin.common.resteasy.auth;

import org.jboss.resteasy.spi.HttpRequest;

import java.util.List;

import javax.ws.rs.core.HttpHeaders;

/**
 * AuthUtil
 */
public class AuthUtil {

    private AuthUtil() {
    }

    /**
     * Retrieve a header, or the empty string if it is not there.
     *
     * @return the header or a blank string (no nils)
     */
    public static String getHeader(HttpRequest request, String name) {
        String headerValue = "";
        if (request != null && name != null) {
            List<String> header = null;
            HttpHeaders headers = request.getHttpHeaders();
            for (String key : headers.getRequestHeaders().keySet()) {
                if (key.equalsIgnoreCase(name)) {
                    header = headers.getRequestHeader(key);
                    break;
                }
            }
            if (null != header && header.size() > 0) {
                headerValue = header.get(0);
            }
        }
        return headerValue;
    }
}
