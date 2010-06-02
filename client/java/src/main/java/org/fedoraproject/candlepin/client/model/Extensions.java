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
package org.fedoraproject.candlepin.client.model;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Set;

import org.fedoraproject.candlepin.client.cmds.Utils;

/**
 * Extension
 */
public class Extensions {
    private X509Certificate x509Certificate;
    private String namespace;
    private List<String> extensions = Utils.newList();

    public Extensions(X509Certificate certificate, String namespace) {
        this.x509Certificate = certificate;
        this.namespace = namespace;
        for (String s : x509Certificate.getNonCriticalExtensionOIDs()) {
            if (s.startsWith(namespace)) {
                extensions.add(s);
            }
        }
    }

    public String getValue(String extension) {
        return getAbsoluteValue(this.namespace + "." + extension);
    }
    
    public String getAbsoluteValue(String completeExtension) {
        byte[] value = this.x509Certificate
            .getExtensionValue(completeExtension);
        if (value != null) {
            return new String(value);
        }
        else {
            return null;
        }
    }

    public Extensions branch(String extension) {
        return new Extensions(x509Certificate, namespace + "." + extension);
    }
    
    public Set<String> find(String regex) {
        Set<String> matches = Utils.newSet();
        for (String ext : extensions) {
            if (ext.matches(regex)) {
                matches.add(ext);
            }
        }
        return matches;
    }

    public String getNamespace() {
        return this.namespace;
    }
}
