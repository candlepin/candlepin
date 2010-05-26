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

import java.math.BigInteger;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.fedoraproject.candlepin.client.ClientException;
import org.fedoraproject.candlepin.client.PemUtil;

/**
 * Simple Entitlement Certificate Model
 */
@XmlRootElement(name = "cert")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class EntitlementCertificate extends TimeStampedEntity{
    protected String key;
    protected String cert;
    protected BigInteger serial;
    protected Entitlement entitlement;
    private X509Certificate x509Certificate;
    
    public EntitlementCertificate() {}

    public EntitlementCertificate(X509Certificate cert, PrivateKey privateKey) {
        try {
            this.cert = PemUtil.getPemEncoded(cert);
            this.x509Certificate = cert;
            this.serial = cert.getSerialNumber();
            this.key = PemUtil.getPemEncoded(privateKey);
            System.out.println(getProductID());
            /*List<String> extensions = new ArrayList<String>(cert.getNonCriticalExtensionOIDs());
            Collections.sort(extensions);
            for (String s : extensions) {
    			System.out.println(s + ": " + new String(cert.getExtensionValue(s)));
    		}
            System.out.println("\n");*/
        }
        catch (Exception e) {
            throw new ClientException(e);
        }
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
    }

    public BigInteger getSerial() {
        return serial;
    }

    public void setSerial(BigInteger serial) {
        this.serial = serial;
    }

    public X509Certificate getX509Cert() {
        return PemUtil.createCert(cert);
    }

    public PrivateKey getPrivateKey() {
        return PemUtil.createPrivateKey(key);
    }

    public String getProductName() {
        return PemUtil.getExtensionValue(getX509Cert(),
            "1.3.6.1.4.1.2312.9.4.1", "Unknown");
    }

    public Date getStartDate() {
        return PemUtil.getExtensionDate(getX509Cert(),
            "1.3.6.1.4.1.2312.9.4.6", null);        
    }

    public Date getEndDate() {
        return PemUtil.getExtensionDate(getX509Cert(),
            "1.3.6.1.4.1.2312.9.4.7", null);        
    }

	public Entitlement getEntitlement() {
		return entitlement;
	}

	public void setEntitlement(Entitlement entitlement) {
		this.entitlement = entitlement;
	}
	
	private static final String PROD_ID_BEGIN = "1.3.6.1.4.1.2312.9.1";
	public int getProductID(){
        System.out.println("\n");
        Set<String> extensions = this.x509Certificate.getNonCriticalExtensionOIDs();
        for (String s : extensions) {
        	int index = s.indexOf(PROD_ID_BEGIN); 
        	if(index != -1){
        		String value = s.substring(index + PROD_ID_BEGIN.length()+1, s.indexOf(".", index + PROD_ID_BEGIN.length()+1));
        		return Integer.parseInt(value); //System.out.println(value);
        	}
			//System.out.println(s + ": " + new String(this.x509Certificate.getExtensionValue(s)));
		}
        return -1;
	}
}
