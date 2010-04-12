package com.redhat.candlepin.util;

import java.util.HashMap;
import java.util.Map;

/**
 * implements the oid structure found here
 * 
 * https://docspace.corp.redhat.com/clearspace/docs/DOC-30244
 * @author jomara
 *
 */
public final class OIDUtil {
    
    private OIDUtil() { }
	
    public static final String REDHAT_OID = "1.3.6.1.4.1.2312.9";
    public static final String SYSTEM_NAMESPACE_KEY = "System";
    public static final String ORDER_NAMESPACE_KEY = "Order";
    public static final String CHANNEL_FAMILY_NAMESPACE_KEY = "Channel Family";
    public static final String ROLE_ENT_NAMESPACE_KEY = "Role Entitlement";
    public static final String PRODUCT_CERT_NAMESPACE_KEY = "Product";
    public static final String UUID_KEY = "UUID";
    public static final String HOST_UUID_KEY = "Host UUID";
    public static final String ORDER_NUMBER_KEY = "Order Number";
    public static final String ORDER_SKU_KEY = "SKU";
    public static final String ORDER_REGNUM_KEY = "Registration Number";
    public static final String ORDER_QUANTITY_KEY = "Quantity";
    public static final String ORDER_STARTDATE_KEY = "Entitlement Start Date";
    public static final String ORDER_ENDDATE_KEY = "Entitlement End Date";
    public static final String ORDERED_PRODUCT_KEY = "Ordered Product";
    public static final String OP_NAME_KEY = "Name";
    public static final String OP_DESC_KEY = "Description";
    public static final String OP_ARCH_KEY = "Architecture";
    public static final String OP_VERSION_KEY = "Version";
    public static final String OP_SUBTYPE_KEY = "Subtype";
    public static final String OP_VIRTLIMIT_KEY = "Virtualization Limit";
    public static final String OP_SOCKETLIMIT_KEY = "Socket Limit";
    public static final String OP_OPTIONCODE_KEY = "Product Option Code";
;
    public static final String CF_NAME_KEY = "Name";
    public static final String CF_LABEL_KEY = "Label";
    public static final String CF_PHYS_QUANTITY_KEY = "Physical Entitlement Quantity";
    public static final String CF_FLEX_QUANTITY_KEY = "Flex Guest Entitlement Quantity";
    public static final String CF_CHANNELS_NAMESPACE = "Channel Namespace";
    public static final String CF_VENDOR_ID_KEY = "Vendor ID";   
    public static final String CF_DOWNLOAD_URL_KEY = "Download URL";
    public static final String CF_GPG_URL_KEY = "GPG Key URL";
    public static final String CF_ENABLED = "Enabled";


    public static final String ROLE_NAME_KEY = "Name";
    public static final String ROLE_DESC_KEY = "Description";
    public static final String ARCH_X86_KEY = "x86";
    public static final String ARCH_X86_64_KEY = "x86_64";
    
	public static final Map<String,String> SYSTEM_OIDS = new HashMap<String,String>();
	public static final Map<String,String> ORDER_OIDS = new HashMap<String,String>();
	public static final Map<String,String> ORDER_PRODUCT_OIDS = new HashMap<String,String>();
	public static final Map<String,String> TOPLEVEL_NAMESPACES = new HashMap<String,String>();
	public static final Map<String,String> CONTENT_ENTITLEMENT_OIDS = new HashMap<String,String>();
	public static final Map<String,String> ROLE_ENTITLEMENT_OIDS = new HashMap<String,String>();
	public static final Map<String,String> CONTENT_ENTITLEMENT_NAMESPACES = new HashMap<String,String>();
	public static final Map<String,String> ROLE_ENTITLEMENT_NAMESPACES = new HashMap<String,String>();
	public static final Map<String,String> CONTENT_ARCHITECTURES = new HashMap<String,String>();
	public static final Map<String,String> CHANNEL_FAMILY_OIDS = new HashMap<String,String>();
	public static final Map<String,String> CHANNEL_OIDS = new HashMap<String,String>();

	static { 
		// load toplevel namespaces
		TOPLEVEL_NAMESPACES.put(PRODUCT_CERT_NAMESPACE_KEY, "1");
		TOPLEVEL_NAMESPACES.put(CHANNEL_FAMILY_NAMESPACE_KEY, "2");
		TOPLEVEL_NAMESPACES.put(ROLE_ENT_NAMESPACE_KEY, "3");
		TOPLEVEL_NAMESPACES.put(ORDER_NAMESPACE_KEY, "4" );
		TOPLEVEL_NAMESPACES.put(SYSTEM_NAMESPACE_KEY,"5");
		
		
		
		// system oids
		SYSTEM_OIDS.put(UUID_KEY, "1");
		SYSTEM_OIDS.put(HOST_UUID_KEY, "2");
		// order oids
		ORDER_OIDS.put(ORDER_NUMBER_KEY, "1");
		ORDER_OIDS.put(ORDER_SKU_KEY, "2");
		ORDER_OIDS.put(ORDER_REGNUM_KEY, "3");
		ORDER_OIDS.put(ORDER_QUANTITY_KEY, "4");
		ORDER_OIDS.put(ORDER_STARTDATE_KEY, "5");
		ORDER_OIDS.put(ORDER_ENDDATE_KEY, "6");
		ORDER_OIDS.put(ORDERED_PRODUCT_KEY, "7");
		// load order product oids
		ORDER_PRODUCT_OIDS.put(OP_NAME_KEY, "1");
		ORDER_PRODUCT_OIDS.put(OP_DESC_KEY, "2");
		ORDER_PRODUCT_OIDS.put(OP_ARCH_KEY, "3");
		ORDER_PRODUCT_OIDS.put(OP_VERSION_KEY, "4");
		ORDER_PRODUCT_OIDS.put(OP_SUBTYPE_KEY, "5");
		ORDER_PRODUCT_OIDS.put(OP_VIRTLIMIT_KEY, "6");
		ORDER_PRODUCT_OIDS.put(OP_SOCKETLIMIT_KEY, "7");
		ORDER_PRODUCT_OIDS.put(OP_OPTIONCODE_KEY, "8");

		// role entitlement oids
		ROLE_ENTITLEMENT_OIDS.put(ROLE_NAME_KEY, "1");
		ROLE_ENTITLEMENT_OIDS.put(ROLE_DESC_KEY, "2");
		// channel family oids
		CHANNEL_FAMILY_OIDS.put(CF_NAME_KEY, "1");
		CHANNEL_FAMILY_OIDS.put(CF_LABEL_KEY, "2");
        CHANNEL_FAMILY_OIDS.put(CF_PHYS_QUANTITY_KEY, "3");
        CHANNEL_FAMILY_OIDS.put(CF_FLEX_QUANTITY_KEY, "4");
        CHANNEL_FAMILY_OIDS.put(CF_VENDOR_ID_KEY, "5");
        CHANNEL_FAMILY_OIDS.put(CF_DOWNLOAD_URL_KEY, "6");
        CHANNEL_FAMILY_OIDS.put(CF_GPG_URL_KEY, "7");
        CHANNEL_FAMILY_OIDS.put(CF_ENABLED, "8");

		// role namespaces
		ROLE_ENTITLEMENT_NAMESPACES.put("RHN Management", "1");
		ROLE_ENTITLEMENT_NAMESPACES.put("RHN Virtualization", "2");
		CONTENT_ARCHITECTURES.put(ARCH_X86_KEY,"1");
		CONTENT_ARCHITECTURES.put(ARCH_X86_64_KEY,"2");
	}
}
