package org.fedoraproject.candlepin.client.model;

import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Set;

import org.fedoraproject.candlepin.client.Constants;
import org.fedoraproject.candlepin.client.PemUtil;

public class AbstractCertificate extends TimeStampedEntity{
	private X509Certificate x509Certificate;
	
	public AbstractCertificate(X509Certificate certificate){
		this.x509Certificate = certificate;
	}
	
    public String getProductName() {
        return PemUtil.getExtensionValue(x509Certificate,
            Constants.PROD_NAME_EXTN_VAL, "Unknown");
    }

    public Date getStartDate() {
    	return this.x509Certificate.getNotBefore();
        /*return PemUtil.getExtensionDate(x509Certificate,
            Constants.START_DATE, null);*/        
    }

    public Date getEndDate() {
    	return this.x509Certificate.getNotAfter();
  /*      return PemUtil.getExtensionDate(x509Certificate,
            Constants.END_DATE, null);*/        
    }
    
    public int getProductID(){
        Set<String> extensions = this.x509Certificate.getNonCriticalExtensionOIDs();
        for (String s : extensions) {
        	int index = s.indexOf(Constants.PROD_ID_BEGIN); 
        	if(index != -1){
        		String value = s.substring(index + Constants.PROD_ID_BEGIN.length()+1, 
        				s.indexOf(".", index + Constants.PROD_ID_BEGIN.length()+1));
        		return Integer.parseInt(value); //System.out.println(value);
        	}
			//System.out.println(s + ": " + new String(this.x509Certificate.getExtensionValue(s)));
		}
        return -1;
	}	
}
