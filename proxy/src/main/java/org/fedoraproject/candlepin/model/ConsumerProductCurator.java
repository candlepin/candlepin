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
