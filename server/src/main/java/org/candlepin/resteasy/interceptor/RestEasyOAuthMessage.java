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
package org.candlepin.resteasy.interceptor;

import net.oauth.OAuth;
import net.oauth.OAuthMessage;

import org.jboss.resteasy.spi.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.MediaType;


/**
 * Creates a valid OAuth message off of the fake
 * HttpHeader which RestEasy provides
 */
public class RestEasyOAuthMessage extends OAuthMessage{

    private static Logger log = LoggerFactory.getLogger(RestEasyOAuthMessage.class);

    public RestEasyOAuthMessage(HttpRequest request) {
        super(request.getHttpMethod(),
            request.getUri().getRequestUri().toString(), getParameters(request));
        copyHeaders(request, getHeaders());
    }

    private static void copyHeaders(HttpRequest request,
                                    Collection<Map.Entry<String, String>> into) {
        Iterator<String> names = request.getHttpHeaders()
                                    .getRequestHeaders().keySet().iterator();
        if (names != null) {
            while (names.hasNext()) {
                String name = names.next();
                Iterator<String> values = request.getHttpHeaders()
                    .getRequestHeader(name).iterator();
                while (values.hasNext()) {
                    into.add(new OAuth.Parameter(name, values.next()));
                }
            }
        }
    }

    public static List<OAuth.Parameter> getParameters(HttpRequest request) {
        List<OAuth.Parameter> list = new ArrayList<OAuth.Parameter>();
        java.util.List<java.lang.String> headers =
            request.getHttpHeaders().getRequestHeader("Authorization");
        if (headers != null) {
            Iterator<String> itor = headers.iterator();
            while (itor.hasNext()) {
                String header = itor.next();
                for (OAuth.Parameter parameter : OAuthMessage
                    .decodeAuthorization(header)) {
                    log.debug(parameter.getKey() + ":" + parameter.getValue());
                    if (!"realm".equalsIgnoreCase(parameter.getKey())) {
                        list.add(parameter);
                    }
                }
            }
        }

        // we can't call getFormParameters when it's a PUT and not a form.
        if (request.getHttpMethod().equals("PUT") &&
            !request.getHttpHeaders().getMediaType().isCompatible(
                MediaType.valueOf("application/x-www-form-urlencoded"))) {
            return list;
        }

        for (Map.Entry<String, List<String>> entry :
                request.getFormParameters().entrySet()) {
            String name = entry.getKey();
            for (String value : entry.getValue()) {
                list.add(new OAuth.Parameter(name, value));
            }
        }

        return list;
    }
}
