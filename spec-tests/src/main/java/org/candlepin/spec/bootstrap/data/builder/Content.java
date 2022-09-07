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



/**
 * Class providing factory functions for BrandingDTO instances.
 */
public final class Content {

    /**
     * Throws an UnsupportedOperationException; individual instantiation of this class is not
     * permitted.
     *
     * @throws UnsupportedOperationException
     */
    private Content() {
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
        return random("test_content");
    }

    /**
     * Builds a ContentDTO instance with the required fields populated with semi-random values,
     * using the provided prefix for the ID field.
     *
     * @param idPrefix
     *  a prefix string to use for the ID field
     *
     * @return
     *  a ContentDTO instance with the required fields populated with semi-random values, using the
     *  provided prefix for the ID field.
     */
    public static ContentDTO random(String idPrefix) {
        String cid = StringUtil.random(8, StringUtil.CHARSET_NUMERIC_HEX);

        return new ContentDTO()
            .id(idPrefix + '-' + cid)
            .name("test content " + cid)
            .label("test label " + cid)
            .type("test")
            .vendor("test vendor");

        // perhaps add optional fields here?
    }

}
