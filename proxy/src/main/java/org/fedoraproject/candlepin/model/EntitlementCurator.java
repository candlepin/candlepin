package org.fedoraproject.candlepin.model;

import com.wideplay.warp.persist.Transactional;

import java.util.HashSet;
import java.util.Set;

public class EntitlementCurator extends AbstractHibernateCurator<Entitlement> {
    public EntitlementCurator() {
        super(Entitlement.class);
    }
    
    // TODO: handles addition of new entitlements only atm!
    @Transactional
    public Set<Entitlement> bulkUpdate(Set<Entitlement> entitlements) {
        Set<Entitlement> toReturn = new HashSet<Entitlement>();
        for(Entitlement toUpdate: entitlements) {
            Entitlement found = find(toUpdate.getId()); 
            if(found != null) {
                toReturn.add(found);
                continue;
            }
            toReturn.add(create(toUpdate));
        }
        return toReturn;
    }
}
