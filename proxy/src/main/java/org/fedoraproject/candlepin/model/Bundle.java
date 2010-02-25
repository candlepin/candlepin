package org.fedoraproject.candlepin.model;

public class Bundle {

    private byte[] privateKey;
    private byte[] entitlementCert;

    public byte[] getPrivateKey() {
        return privateKey;
    }
    public void setPrivateKey(byte[] privateKey) {
        this.privateKey = privateKey;
    }
    public byte[] getEntitlementCert() {
        return entitlementCert;
    }
    public void setEntitlementCert(byte[] entitlementCert) {
        this.entitlementCert = entitlementCert;
    }
}
