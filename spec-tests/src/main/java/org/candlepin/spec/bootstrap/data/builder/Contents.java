/**
 * Copyright (c) 2009 - 2022 Red Hat, Inc.
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
package org.candlepin.spec.bootstrap.data.builder;

import org.candlepin.dto.api.client.v1.ContentDTO;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * Class providing factory functions for BrandingDTO instances.
 */
public final class Contents {

    /**
     * Throws an UnsupportedOperationException; individual instantiation of this class is not
     * permitted.
     *
     * @throws UnsupportedOperationException
     */
    private Contents() {
        throw new UnsupportedOperationException();
    }

    /**
     * Creates a shallow copy of the specified content DTO
     *
     * @param source
     *  the source ContentDTO instance to copy
     *
     * @throws IllegalArgumentException
     *  if source is null
     *
     * @return
     *  a shallow copy of the specified content DTO
     */
    public static ContentDTO copy(ContentDTO source) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        return new ContentDTO()
            .id(source.getId())
            .name(source.getName())
            .type(source.getType())
            .label(source.getLabel())
            .vendor(source.getVendor())
            .contentUrl(source.getContentUrl())
            .requiredTags(source.getRequiredTags())
            .releaseVer(source.getReleaseVer())
            .gpgUrl(source.getGpgUrl())
            .modifiedProductIds(source.getModifiedProductIds())
            .arches(source.getArches())
            .metadataExpire(source.getMetadataExpire());
    }

    /**
     * Builds a ContentDTO instance with the required fields populated with semi-random values.
     *
     * @return
     *  a ContentDTO instance with the required fields populated with semi-random values
     */
    public static ContentDTO random() {
        String cid = StringUtil.random(18, StringUtil.CHARSET_NUMERIC);

        return new ContentDTO()
            .id(cid)
            .name("test_content" + cid)
            .label("test label " + cid)
            .type("yum")
            .vendor("test vendor")
            .contentUrl("/url_" + cid);

        // perhaps add optional fields here?
    }

    /**
     * Builds a map populated with a provided collection of {@link ContentDTO}s.
     * This map is keyed by the content's id.
     *
     * @param content
     *  a list of content to populate the map with
     *
     * @return
     *  a map of content id to {@link ContentDTO} or an empty map if the content
     *  is either null or empty.
     */
    public static Map<String, ContentDTO> toMap(Collection<ContentDTO> content) {
        if (content == null || content.isEmpty()) {
            return new HashMap<>();
        }

        return content.stream().collect(Collectors.toMap(ContentDTO::getId, Function.identity()));
    }
}
