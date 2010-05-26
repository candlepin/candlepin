package org.fedoraproject.candlepin.client;

public interface Constants {
	String KEY_STORE_FILE = "/usr/share/tomcat6/conf/keystore";
	String DEFAULT_SERVER = "https://localhost:8443/candlepin";
	String CANDLE_PIN_CERTIFICATE_FILE = "/etc/candlepin/certs/candlepin-ca.crt";
	String X509 = "X509";
	String CANDLE_PIN_HOME_DIR = "/home/ajay/.candlepin";
	String PROD_NAME_EXTN_VAL = "1.3.6.1.4.1.2312.9.4.1";
	String START_DATE = "1.3.6.1.4.1.2312.9.4.6";
	String END_DATE = "1.3.6.1.4.1.2312.9.4.7";
	String PROD_ID_BEGIN = "1.3.6.1.4.1.2312.9.1";
}
