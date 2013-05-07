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


import java.util.Arrays;
import java.util.List;

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

    private static String[] x86LabelStrs = {"i386", "i486", "i586", "i686"};
    private static List<String> x86Labels = Arrays.asList(x86LabelStrs);
    private static String[] ppcLabelStrs = {"ppc", "ppc64"};
    private static List<String> ppcLabels = Arrays.asList(ppcLabelStrs);

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

    @Override
    public String toString() {
        return "Arch [id = " + id + ", label = " + label + "]";
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

        String inLabel = incoming.getLabel();
        String ourLabel = this.getLabel();

        // handle "ALL" arch, sigh
        if (inLabel.equals("ALL")) {
            return true;
        }

        // Exact arch match
        if (ourLabel.equals(inLabel)) {
            return true;
        }

        // x86_64 can use content for i386 etc
        if (ourLabel.equals("x86_64")) {
            if (x86Labels.contains(inLabel)) {
                return true;
            }
        }

        // i686 can run all x86 arches
        if (ourLabel.equals("i686")) {
            if (x86Labels.contains(inLabel)) {
                return true;
            }
        }

        // ppc64 can run ppc. Mostly...
        if (ourLabel.equals("ppc64")) {
            if (ppcLabels.contains(inLabel)) {
                return true;
            }
        }

        /* In theory, ia64 can run x86 and x86_64 content.
         * I think s390x can use s390 content as well.
         * ppc only runs ppc
         *
         * But for now, we only except exact matches.
         */

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
