package org.fedoraproject.candlepin.resource;

import java.io.IOException;
import java.text.ParseException;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.model.RulesCurator;
import org.fedoraproject.candlepin.model.Rules;

import com.google.inject.Inject;

@Path("/rules")
public class RulesResource {
    
    
    private static Logger log = Logger.getLogger(CertificateResource.class);
    private RulesCurator rulesCurator;
    
    @Inject
    public RulesResource(RulesCurator rulesCurator) {
        this.rulesCurator = rulesCurator;
    }
    
    @POST
    @Consumes({MediaType.TEXT_PLAIN})
    @Produces({MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public String upload(String rulesBuffer) {
        
        if (rulesBuffer == null || "".equals(rulesBuffer)) {
            throw new WebApplicationException(Response.Status.BAD_REQUEST);
        }
  
        Rules rules = new Rules(rulesBuffer);
        rulesCurator.update(rules);        
        return rulesBuffer;
    }
    
    
    @GET
    @Produces({MediaType.TEXT_PLAIN})
    public String get() {
         return rulesCurator.getRules().getRules();
    }
}   
