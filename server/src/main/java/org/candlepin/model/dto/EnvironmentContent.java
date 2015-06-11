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
package org.candlepin.model.dto;


/**
 * The EnvironmentContent class represents the POJO form of the JSON input when promoting content,
 * or updating promoted content.
 */
public class EnvironmentContent {

    private String environmentId;
    private String contentId;
    private boolean enabled;

    /**
     * Creates a new, empty EnvironmentContent instance with the default values. By default, the
     * environment and content IDs will be null, while the enabled flag will be set to true.
     */
    public EnvironmentContent() {
        this.environmentId = null;
        this.contentId = null;
        this.enabled = true;
    }

    /**
     * Creates a new EnvironmentContent instance using the given initial values.
     *
     * @param environmentId
     *  The initial value to use for the environment ID
     *
     * @param contentId
     *  The initial value to use for the content ID
     *
     * @param enabled
     *  The initial value to use for the enabled flag
     */
    public EnvironmentContent(String environmentId, String contentId, boolean enabled) {
        this.environmentId = environmentId;
        this.contentId = contentId;
        this.enabled = enabled;
    }

    /**
     * Retrieves the current environment ID. If an environment ID has not yet been assigned, this
     * method returns null.
     *
     * @return
     *  the current environment ID, or null if an environment ID has not yet been assigned
     */
    public String getEnvironmentId() {
        return this.environmentId;
    }

    /**
     * Assigns a new value for the environment ID.
     *
     * @param environmentId
     *  The new value for the environment ID
     */
    public void setEnvironmentId(String environmentId) {
        this.environmentId = environmentId;
    }

    /**
     * Retrieves the current content ID. If an content ID has not yet been assigned, this method
     * returns null.
     *
     * @return
     *  the current content ID, or null if an content ID has not yet been assigned
     */
    public String getContentId() {
        return this.contentId;
    }

    /**
     * Assigns a new value for the content ID.
     *
     * @param contentId
     *  The new value for the content ID
     */
    public void setContentId(String contentId) {
        this.contentId = contentId;
    }

    /**
     * Retrieves the current state of the enabled flag.
     *
     * @return
     *  The current state of the enabled flag
     */
    public boolean getEnabled() {
        return this.enabled;
    }

    /**
     * Sets the state of the enabled flag
     *
     * @param enabled
     *  The new state for the enabled flag
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

}
