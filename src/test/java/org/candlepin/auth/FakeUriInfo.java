/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import java.net.URI;
import java.util.List;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.PathSegment;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

/**
 * Fake implementation of {@link UriInfo} for easier stubbing of requests.
 */
class FakeUriInfo implements UriInfo {

    private final String path;
    private final MultivaluedMap<String, String> pathParams;
    private final MultivaluedMap<String, String> queryParams;

    FakeUriInfo(String path) {
        this.path = path;
        this.pathParams = new MultivaluedHashMap<>();
        this.queryParams = new MultivaluedHashMap<>();
    }

    @Override
    public String getPath() {
        return this.path;
    }

    @Override
    public String getPath(boolean decode) {
        return null;
    }

    @Override
    public List<PathSegment> getPathSegments() {
        return null;
    }

    @Override
    public List<PathSegment> getPathSegments(boolean decode) {
        return null;
    }

    @Override
    public URI getRequestUri() {
        return null;
    }

    @Override
    public UriBuilder getRequestUriBuilder() {
        return null;
    }

    @Override
    public URI getAbsolutePath() {
        return null;
    }

    @Override
    public UriBuilder getAbsolutePathBuilder() {
        return null;
    }

    @Override
    public URI getBaseUri() {
        return null;
    }

    @Override
    public UriBuilder getBaseUriBuilder() {
        return null;
    }

    @Override
    public MultivaluedMap<String, String> getPathParameters() {
        return this.pathParams;
    }

    @Override
    public MultivaluedMap<String, String> getPathParameters(boolean decode) {
        return this.pathParams;
    }

    @Override
    public MultivaluedMap<String, String> getQueryParameters() {
        return this.queryParams;
    }

    @Override
    public MultivaluedMap<String, String> getQueryParameters(boolean decode) {
        return this.queryParams;
    }

    @Override
    public List<String> getMatchedURIs() {
        return null;
    }

    @Override
    public List<String> getMatchedURIs(boolean decode) {
        return null;
    }

    @Override
    public List<Object> getMatchedResources() {
        return null;
    }

    @Override
    public URI resolve(URI uri) {
        return null;
    }

    @Override
    public URI relativize(URI uri) {
        return null;
    }

    public void addQueryParam(String key, String value) {
        this.queryParams.add(key, value);
    }

    public void addPathParam(String key, String value) {
        this.pathParams.add(key, value);
    }

}
