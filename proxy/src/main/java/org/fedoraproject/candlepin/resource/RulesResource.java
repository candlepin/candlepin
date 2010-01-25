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
    
    //@Inject
    public RulesResource() {
        rulesCurator = this.rulesCurator;
    }
    
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public String upload(String rulesBuffer) {
        
        if (rulesBuffer == null || "".equals(rulesBuffer)) {
            throw new WebApplicationException(Response.Status.BAD_REQUEST);
        }
  
        Rules rules = new Rules(rulesBuffer);
        rulesCurator.create(rules);        
        return rulesBuffer;
    }
    
    @GET
    @Produces(MediaType.TEXT_HTML)
    public String get() {
        return "<h1>BLARGH</h1>";
    }
    
    
//    @GET
//    @Produces({ MediaType.TEXT_PLAIN })
//    public List get() {
//         List<Rules> rules = rulesCurator.findAll();
//         return rules;
//    }
}   
