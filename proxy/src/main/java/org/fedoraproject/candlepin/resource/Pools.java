package org.fedoraproject.candlepin.resource;

import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;

import org.fedoraproject.candlepin.model.EntitlementPool;

/**
 * This object exists only to expose entitlement pools 
 * as a collection with the pattern. This is a workaroung
 * object for jeresy
 * https://jersey.dev.java.net/issues/show_bug.cgi?id=361
 * <pools><pool></pool></pools>
 * @author bkearney
 *
 */
@XmlRootElement
public class Pools {
    public List<EntitlementPool> pool ;
}
