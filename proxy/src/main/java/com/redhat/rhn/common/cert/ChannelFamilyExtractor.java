/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package com.redhat.rhn.common.cert;

import org.jdom.Element;

/**
 * ChannelFamilyExtractor - extracts the channel family information
 * from the field whose name is passed into the ctor.
 *
 * Borrowed from project Spacewalk: http://spacewalk.redhat.com
 */
public class ChannelFamilyExtractor implements FieldExtractor {

    private String fieldName;

    /**
     * Constructs a new extractor
     * @param fieldName0 name of field used to extract the channel family data
     */
    public ChannelFamilyExtractor(String fieldName0) {
        fieldName = fieldName0;
    }

    /**
     * {@inheritDoc}
     */
    public void extract(Certificate target, Element field) {
        String quantity = field.getAttributeValue("quantity");
        String family = field.getAttributeValue("family");
        ChannelFamilyDescriptor cf = new ChannelFamilyDescriptor(family, quantity);
        target.addChannelFamily(cf);
    }

    /**
     * Returns FALSE, as this is not REQUIRED.
     * @return FALSE, as this is not REQUIRED.
     */
    public boolean isRequired() {
        return false;
    }

    /**
     * Returns the name of the field being worked on.
     * @return the name of the field being worked on.
     */
    public String getFieldName() {
        return fieldName;
    }

}
