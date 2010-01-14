package org.fedoraproject.candlepin.enforcer.java;

import java.util.LinkedList;
import java.util.List;

import org.fedoraproject.candlepin.DateSource;
import org.fedoraproject.candlepin.enforcer.Enforcer;
import org.fedoraproject.candlepin.enforcer.ValidationError;
import org.fedoraproject.candlepin.enforcer.ValidationWarning;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.EntitlementPoolCurator;

public class JavaEnforcer implements Enforcer {
    private List<ValidationError> errors = new LinkedList<ValidationError>();
    private List<ValidationWarning> warnings = new LinkedList<ValidationWarning>();

    private DateSource dateSource;
    private EntitlementPoolCurator epCurator;
    
    public JavaEnforcer(DateSource dateSource, 
            EntitlementPoolCurator epCurator) {
        this.dateSource = dateSource;
        this.epCurator = epCurator;
    }
    
    @Override
    public List<ValidationError> errors() {
        return errors;
    }

    @Override
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    @Override
    public List<ValidationWarning> warnings() {
        return warnings;
    }        

    @Override
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    @Override
    public boolean validate(Consumer consumer, EntitlementPool enitlementPool) {
        if (!enitlementPool.entitlementsAvailable()) {
            errors.add(new ValidationError("Not enough entitlements"));
            return false;
        }
                    
        if (enitlementPool.isExpired(dateSource)) {
            errors.add(new ValidationError("Entitlements for " + enitlementPool.getProduct().getName() + 
                    " expired on: " + enitlementPool.getEndDate()));
            return false;
        }
        
        return true;
    }
}
