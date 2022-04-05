/**
 * Copyright (c) 2009 - 2021 Red Hat, Inc.
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
import org.candlepin.model.EnvironmentContent;
import org.candlepin.model.ProductContent;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;


/**
 * A collection of content promoted to environments.
 *
 * Stored content from the added environments is de-duplicated based on the environment priority order.
 */
public class PromotedContent {

    private static final Logger log = LoggerFactory.getLogger(PromotedContent.class);

    private final Map<String, EnvironmentContent> contents;
    private final ContentPrefix prefix;

    /**
     * A constructor
     *
     * @param prefix prefixes to be used in path construction
     */
    public PromotedContent(ContentPrefix prefix) {
        this.prefix = Objects.requireNonNull(prefix);
        this.contents = new HashMap<>();
    }

    /**
     * Adds content of the given environment to the promoted content.
     *
     * It is important for the environments for come ordered by priority from
     * highest to lowest as content collected in earlier calls takes precedence.
     *
     * @param environment an environment from which to collect content
     * @return this instance of {@link PromotedContent}
     */
    public PromotedContent with(Environment environment) {
        if (environment == null) {
            return this;
        }

        log.debug("Checking for promoted content in environment: {}", environment.getName());
        for (EnvironmentContent envContent : environment.getEnvironmentContent()) {
            log.debug("  promoted content: {}", envContent.getContent());

            this.contents.putIfAbsent(envContent.getContent().getId(), envContent);
        }

        return this;
    }

    /**
     * Collects promoted content from all given environments.
     *
     * Expects environments to be sorted by priority (from highest to lowest) in
     * order to deduplicate the promoted content.
     *
     * @param environments environments from which to collect promoted content
     * @return an instance of {@link PromotedContent}
     */
    public PromotedContent withAll(List<Environment> environments) {
        if (environments == null || environments.isEmpty()) {
            log.debug("No environments to check for promoted content.");
            return this;
        }

        for (Environment environment : environments) {
            this.with(environment);
        }

        return this;
    }

    /**
     * Returns an id of environment to which is the given {@link ProductContent} promoted.
     *
     * @param pc a product content for which to find environment
     * @return an environment id
     */
    public String environmentIdOf(ProductContent pc) {
        if (pc == null || pc.getContent() == null) {
            return null;
        }
        String contentId = pc.getContent().getId();
        EnvironmentContent environmentContent = this.contents.get(contentId);
        if (environmentContent == null) {
            log.debug("Environment not found for content: {}", contentId);
            return null;
        }
        return environmentContent.getEnvironment().getId();
    }

    /**
     * Checks if the given {@link ProductContent} is promoted into any environment.
     *
     * @param pc a product content to check
     * @return true if the product content is promoted into any environment
     */
    public boolean contains(ProductContent pc) {
        if (pc == null || pc.getContent() == null) {
            return false;
        }
        return this.contents.containsKey(pc.getContent().getId());
    }

    /**
     * Checks if the given product content is enabled.
     *
     * @param pc a product content to check
     * @return true if the given product content is enabled
     */
    public Boolean isEnabled(ProductContent pc) {
        if (!contains(pc)) {
            return false;
        }
        return this.contents.get(pc.getContent().getId()).getEnabled();
    }

    /**
     * Constructs a content path from environment specific prefix and given content.
     *
     * The format of the resulting content path will depend on the prefix class
     * used. For the details of prefix construction refer to the {@link ContentPrefix}
     * and its implementations.
     *
     * @param pc content for which to create a content path
     * @return full content path
     */
    public String getPath(ProductContent pc) {
        String contentPath = getContentUrl(pc);
        String contentPrefix = this.prefix.get(this.environmentIdOf(pc));
        String fullContentPath = createFullContentPath(contentPrefix, contentPath);
        log.trace("Full content path for product content is: {}", fullContentPath);
        return fullContentPath;
    }

    private String getContentUrl(ProductContent pc) {
        if (pc == null || pc.getContent() == null) {
            return null;
        }
        return pc.getContent().getContentUrl();
    }

    private String createFullContentPath(String contentPrefix, String contentPath) {
        // Allow for the case where the content URL is a true URL.
        // If that is true, then return it as is.
        if (contentPath != null && (contentPath.startsWith("http://") ||
            contentPath.startsWith("file://") ||
            contentPath.startsWith("https://") ||
            contentPath.startsWith("ftp://"))) {

            return contentPath;
        }

        String prefix = "/";
        if (!StringUtils.isEmpty(contentPrefix)) {
            // Ensure there is no double // in the URL. See BZ952735
            // remove them all except one.
            prefix = StringUtils.stripEnd(contentPrefix, "/") + prefix;
        }

        contentPath = StringUtils.stripStart(contentPath, "/");
        return prefix + contentPath;
    }

}
