package org.fedoraproject.candlepin.client.model;

import java.security.cert.X509Certificate;

public class ProductCertificate extends AbstractCertificate{

	public ProductCertificate(X509Certificate certificate) {
		super(certificate);
	}

	private EntitlementCertificate entitlementCertificate;

	public boolean isInstalled(){
		return entitlementCertificate != null;
	}

	public EntitlementCertificate getEntitlementCertificate() {
		return entitlementCertificate;
	}

	public void setEntitlementCertificate(
			EntitlementCertificate entitlementCertificate) {
		this.entitlementCertificate = entitlementCertificate;
	}
}
