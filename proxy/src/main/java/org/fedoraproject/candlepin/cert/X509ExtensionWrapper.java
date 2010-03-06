package org.fedoraproject.candlepin.cert;

import org.bouncycastle.asn1.ASN1Encodable;

public class X509ExtensionWrapper {
    private String oid = null;
    private boolean critical;
    private ASN1Encodable asn1Encodable;
    
    public X509ExtensionWrapper(String oid, boolean critical, ASN1Encodable asn1Encodable){
        this.oid = oid;
        this.critical = critical;
        this.asn1Encodable = asn1Encodable;
    }
    
    public String getOid() {
        return oid;
    }

    public boolean isCritical() {
        return critical;
    }

    public ASN1Encodable getAsn1Encodable() {
        return asn1Encodable;
    }   
}