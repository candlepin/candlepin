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
package org.candlepin.controller.util;

import org.candlepin.model.Environment;
import org.candlepin.model.Owner;
import org.candlepin.util.Util;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Class encapsulates the building of full content paths.
 */
public class ContentPathBuilder {
    private static final Logger log = LoggerFactory.getLogger(PromotedContent.class);

    private static final Pattern PROTOCOL_PATTERN = Pattern.compile("^[A-Za-z]+://.*$");

    /**
     * A factory method constructs an instance and prepares prefixes for the given environments.
     *
     * @param owner an owner to be used in construction of content prefixes
     * @param environments environments for which to prepare content prefixes
     * @return content prefix instance
     */
    public static ContentPathBuilder from(Owner owner, List<Environment> environments) {
        ContentPathBuilder prefixes = new ContentPathBuilder(owner);
        if (environments != null) {
            for (Environment environment : environments) {
                prefixes.add(environment);
            }
        }
        return prefixes;
    }

    private final Map<String, String> envPrefixes = new HashMap<>();
    private final Map<String, String> envNames = new HashMap<>();
    private final String ownerPrefix;

    /**
     * A constructor
     *
     * @param owner an owner to be used in construction of content prefixes
     */
    private ContentPathBuilder(Owner owner) {
        String prefix = owner == null ? "" : owner.getContentPrefix();
        this.ownerPrefix = normalizePath(prefix);
    }

    /**
     * Builds a full content path for the given content and environment.
     * <p>
     * Path is constructed in order of content -> environment -> owner. If the
     * path is already a full path (it includes protocol), the rest of the
     * prefixes are ignored. E.G. After including environment prefix
     * 'https://console.redhat.com/template', owner prefix is skipped.
     * <p>
     * Builder also populates environment placeholders. Currently only
     * environment name is supported.
     *
     * @param environmentId an id of environment for which to return a content prefix
     * @param contentPath a content part of the full content path for which to construct the full path.
     * @return full content path
     */
    public String build(String environmentId, String contentPath) {
        if (contentPath == null || contentPath.isBlank()) {
            throw new IllegalArgumentException("Content path cannot be null!");
        }

        String fullPath = buildFullPath(environmentId, contentPath);
        String populatedPath = populatePlaceholders(fullPath, environmentId);
        return encodePath(populatedPath);
    }

    public String buildFullPath(String environmentId, String contentPath) {
        String normalizedContentPath = normalizePath(contentPath);
        if (isFullUri(normalizedContentPath)) {
            return normalizedContentPath;
        }

        String envPrefix = this.envPrefixes.getOrDefault(environmentId, "");
        String contentPathWithEnv = envPrefix + normalizedContentPath;
        if (isFullUri(contentPathWithEnv)) {
            return contentPathWithEnv;
        }

        return this.ownerPrefix + contentPathWithEnv;
    }

    private void add(Environment environment) {
        String envPrefix = normalizePath(environment.getContentPrefix());
        this.envPrefixes.put(environment.getId(), envPrefix);
        // This should be a separate object, but we can extract it later,
        // when we know if there are other placeholders we want to support.
        this.envNames.put(environment.getId(), environment.getName());
    }

    private boolean isFullUri(String path) {
        Matcher matcher = PROTOCOL_PATTERN.matcher(path);
        return matcher.matches();
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }

        String strippedPath = StringUtils.stripStart(StringUtils.stripEnd(path, "/"), "/");
        if (isFullUri(strippedPath)) {
            return strippedPath;
        }

        return "/" + strippedPath;
    }

    private String populatePlaceholders(String path, String envId) {
        return path.replace("$env", this.envNames.getOrDefault(envId, ""));
    }

    private String encodePath(String contentPath) {
        if (!isFullUri(contentPath)) {
            return "/" + encodePathSegments(contentPath);
        }

        try {
            URIBuilder uriBuilder = new URIBuilder(contentPath);

            // Encode the segments of the path... for reasons
            String encodedPath = this.encodePathSegments(uriBuilder.getPath());
            uriBuilder.setPath(encodedPath);

            // Build + return the URI as a string
            return uriBuilder.toString();
        }
        catch (URISyntaxException e) {
            // Uh oh... this is probably bad, but it's not up to us to police URIs, so just return
            // it as-is and issue a very loud warning
            log.warn("Invalid URI constructed for content path; returning path as-is: {}", contentPath, e);
            return contentPath;
        }
    }

    private String encodePathSegments(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }

        return Arrays.stream(path.split("/"))
            .filter(s -> !s.isBlank())
            .map(this::encode)
            .collect(Collectors.joining("/"));
    }

    private String encode(String pathSegment) {
        // Putting $ back to keep unknown macros (such as $basearch) unencoded.
        // We should check if this is still necessary.
        return Util.encodeUrl(pathSegment).replace("%24", "$");
    }

}
