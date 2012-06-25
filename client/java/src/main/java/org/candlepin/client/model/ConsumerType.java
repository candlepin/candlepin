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
package org.candlepin.client.model;

/**
 * ConsumerType
 */
public class ConsumerType extends TimeStampedEntity {
    protected Long id;
    protected String label;

    public ConsumerType() {

    }

    public ConsumerType(String label, Long id) {
        this.label = label;
        this.id = id;
    }

    /**
     * ConsumerType constructor with label
     *
     * @param labelIn to set
     */
    public ConsumerType(String labelIn) {
        this.label = labelIn;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
