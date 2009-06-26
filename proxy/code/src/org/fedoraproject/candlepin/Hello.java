package org.fedoraproject.candlepin;

import com.sun.jersey.api.representation.Form;

import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.Path;
import javax.ws.rs.POST;

@Path("/helloworld")
public class Hello {
    private static String message = "Hello from Candlepin";

    @GET
    @Produces("text/plain")
    public String getClinchedMessage() {
        return message;
    }

    @POST
    @Produces("text/plain")
    public void setMessage(Form form) {
       System.out.println(form.toString());
       message = form.getFirst("message"); 
       
    }

    public static void main(String[] args) {
        System.out.println("Hello from Candlepin");
    }
}
