package org.fedoraproject.candlepin.model;

import java.util.HashSet;
import java.util.Set;

import com.wideplay.warp.persist.Transactional;

public class ConsumerProductCurator extends AbstractHibernateCurator<ConsumerProduct> {
    public ConsumerProductCurator() {
        super(ConsumerProduct.class);
    }
    
    @Transactional
    public ConsumerProduct update(ConsumerProduct updated) {
        return getEntityManager().merge(updated);
    }    
    
    public Set<ConsumerProduct> bulUpdate(Set<ConsumerProduct> consumerProducts) {
        Set<ConsumerProduct> toReturn = new HashSet<ConsumerProduct>();        
        for(ConsumerProduct toUpdate: consumerProducts) { 
            toReturn.add(update(toUpdate));
        }
        return toReturn;        
    }    
}
