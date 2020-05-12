/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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
package org.candlepin.service.model;

import java.util.Collection;
import java.util.Date;



/**
 * The ContentInfo represents a minimal set of owner/organization information used by the service
 * adapters.
 *
 * Data which is not set or does not change should be represented by null values. To explicitly
 * clear a value, an empty string or non-null "empty" value should be used instead.
 */
public interface ContentInfo extends ServiceAdapterModel {

    /**
     * Fetches the Red Hat ID of this content. If the ID has not yet been set, this method returns
     * null.
     *
     * @return
     *  the Red Hat ID of this content, or null if the ID has not been set
     */
    String getId();

    /**
     * Fetches the type of this content. If the type has not yet been set, this method returns null.
     *
     * @return
     *  the type of this content, or null if the type has not been set
     */
    String getType();

    /**
     * Fetches the label of this content. If the label has not yet been set, this method returns
     * null.
     *
     * @return
     *  the label of this content, or null if the label has not been set
     */
    String getLabel();

    /**
     * Fetches the name of this content. If the name has not yet been set, this method returns null.
     *
     * @return
     *  the name of this content, or null if the name has not been set
     */
    String getName();

    /**
     * Fetches the vendor of this content. If the vendor has not yet been set, this method returns
     * null.
     *
     * @return
     *  the vendor of this content, or null if the vendor has not been set
     */
    String getVendor();

    /**
     * Fetches the name of this content. If the name has not yet been set, this method returns null.
     *
     * @return
     *  the name of this content, or null if the name has not been set
     */
    String getContentUrl();

    /**
     * Fetches the required tags of this content. If the tags have not yet been set, this method
     * returns null.
     *
     * @return
     *  the required tags of this content, or null if the tags have not been set
     */
    String getRequiredTags();

    /**
     * Fetches the release version of this content. If the version has not yet been set, this
     * method returns null.
     *
     * @return
     *  the release version of this content, or null if the version has not been set
     */
    String getReleaseVersion();

    /**
     * Fetches the URL of the GPG key for this content. If the URL has not yet been set, this method
     * returns null.
     *
     * @return
     *  the URL of the GPG key for this content, or null if the URL has not been set
     */
    String getGpgUrl();

    /**
     * Fetches the supported architectures for this content. If the arches have not yet been set,
     * this method returns null.
     *
     * @return
     *  the supported architectures of this content, or null if the arches have not been set
     */
    String getArches();

    /**
     * Fetches the expiration date of this content's metadata as a timestamp from the Unix epoch. If
     * the metadata expiration has not yet been set, this method returns null.
     *
     * @return
     *  the expiration date of this content's metadata, or null if the metadata expiration has not
     *  been set
     */
    Long getMetadataExpiration();

    /**
     * Fetches a collection of product IDs required by this content. If the required products have
     * not yet been set, this method returns null. If this content has no required products, this
     * method returns an empty collection.
     *
     * @return
     *  a collection of product IDs required by this content, or null if the required products have
     *  not been set
     */
    Collection<String> getRequiredProductIds();

    /**
     * Fetches the date this content was created. If the creation date has not been set, this method
     * returns null.
     *
     * @return
     *  the creation date for this content, or null if the creation date has not been set
     */
    Date getCreated();

    /**
     * Fetches the date this content was last updated. If the update date has not been set, this
     * method returns null.
     *
     * @return
     *  the last update date for this content, or null if the last update date has not been set
     */
    Date getUpdated();
}
