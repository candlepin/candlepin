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
package org.candlepin.model;


import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Represents a cpu or system architecture. Content sets can
 * include software that runs on a particular architecture.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "cp_arch")
public class Arch extends AbstractHibernateObject {

    @Id
    private String id;

    private String label;

    public Arch() {

    }

    public Arch(String id, String label) {
        this.setLabel(label);
        this.setId(id);
    }


    public void setId(String id) {
        this.id = id;
    }

    /** {@inheritDoc} */
    public String getId() {
        return id;

    }

    /**
     * @return the label
     */
    public String getLabel() {
        return label;
    }

    /**
     * @param label the label to set
     */
    public void setLabel(String label) {
        this.label = label;
    }

    /**
     *
     * @param incoming
     * @return If the two arches are compatible
     *
     * Compare two Arch objects to see if they are considered
     * compatible for the sake of running package content for
     * one Arch
     */
    // I'm sure there is a better name here, but "isCompatible"
    // isn't quite right.
    public boolean usesContentFor(Arch incoming) {
        boolean compatible = false;
        // FIXME: hardcode exact matches on label
        //        only atm
        compatible = this.label.equals(incoming.label);

        // FIXME: we may end up needing to compare to "ALL"
        // as well.

        // This could be some fancy db magic if someone were
        // so included, but more than likely will just be
        // some map look ups from a constant map.

        // Use consumerArch.usesContentFor(contentArch), so
        // i686Arch.usesContentFor(i386Arch) would return true, but
        // i386Arch.usesContentFor(i686Arch) would return false.
        return compatible;
    }

}
