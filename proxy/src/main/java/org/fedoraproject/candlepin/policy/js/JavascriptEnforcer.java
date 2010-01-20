package org.fedoraproject.candlepin.policy.js;

import java.util.List;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.policy.Enforcer;
import org.fedoraproject.candlepin.policy.ValidationError;
import org.fedoraproject.candlepin.policy.ValidationWarning;

public class JavascriptEnforcer implements Enforcer {

    @Override
    public List<ValidationError> errors() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean hasErrors() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean hasWarnings() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean validate(Consumer consumer, EntitlementPool enitlementPool) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public List<ValidationWarning> warnings() {
        // TODO Auto-generated method stub
        return null;
    }

}
