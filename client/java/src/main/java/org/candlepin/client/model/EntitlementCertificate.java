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

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.candlepin.client.ClientException;
import org.candlepin.client.Constants;
import org.candlepin.client.PemUtil;
import org.candlepin.client.cmds.Utils;

/**
 * Simple Entitlement Certificate Model
 */
@XmlRootElement(name = "cert")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class EntitlementCertificate extends ProductCertificate {
    protected String key;
    protected String cert;

    public EntitlementCertificate(X509Certificate cert, PrivateKey privateKey) {
        super(cert);
        try {
            this.cert = PemUtil.getPemEncoded(cert);
            this.key = PemUtil.getPemEncoded(privateKey);
        }
        catch (Exception e) {
            throw new ClientException(e);
        }
    }

    public EntitlementCertificate() {
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getCert() {
        return cert;
    }

    public void setCert(String cert) {
        this.cert = cert;
        this.setX509Certificate(PemUtil.createCert(cert));
    }

    public PrivateKey getPrivateKey() {
        return PemUtil.createPrivateKey(key);
    }

    public List<Entitlement> getEntitlements() {
        List<Entitlement> entitlements = Utils.newList();
        entitlements.addAll(getContentEntitlements());
        entitlements.addAll(getRoleEntitlements());
        return entitlements;
    }

    public List<Content> getContentEntitlements() {
        List<Content> contents = Utils.newList();
        Extensions extensions = new Extensions(getX509Certificate(),
            Constants.CONTENT_NAMESPACE);
        for (String hash : findUniqueHashes(extensions,
            Constants.CONTENT_NAMESPACE)) {
            Content content = new Content(extensions.branch(hash), hash);
            content.setEntitlementCertificate(this);
            contents.add(content);
        }
        return contents;
    }

    public List<Role> getRoleEntitlements() {
        List<Role> roles = Utils.newList();
        Extensions extensions = new Extensions(getX509Certificate(),
            Constants.ROLE_ENTITLEMENT_NAMESPACE);
        for (String subBranch : findUniqueHashes(extensions,
            Constants.ROLE_ENTITLEMENT_NAMESPACE)) {
            Role role = new Role(extensions.branch(subBranch), subBranch);
            role.setEntitlementCertificate(this);
            roles.add(role);
        }
        return roles;
    }

    public Order getOrder() {
        Extensions extensions = new Extensions(getX509Certificate(),
            Constants.ORDER_NAMESPACE);
        if (extensions.getValue("1") == null) {
            return null;
        }
        else {
            return new Order(extensions);
        }
    }

    @JsonIgnore
    public void setEntitlement(Entitlement entitlement) {

    }

    public Date getStartDate() {
        return getOrder().getStartDate();
    }

    public Date getEndDate() {
        return getOrder().getEndDate();
    }

    public boolean isValidWithGracePeriod() {
        Date currentDate = new Date();
        return currentDate.after(super.getStartDate()) &&
            currentDate.before(super.getEndDate());
    }
}
