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
package org.fedoraproject.candlepin.resource;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.model.ObjectFactory;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.OwnerCurator;


/**
 * Base api gateway for all model objects.
 */
public abstract class BaseResource {

    private Class model;

    /**
     * Ctor
     * @param modelClass class of derived model class.
     */
    public BaseResource(Class modelClass) {
        this.model = modelClass;
    }
    
    /**
     * Logger for this class
     */
    private static Logger log = Logger.getLogger(BaseResource.class);

    /**
     * Returns the model object matching the given uuid.
     * @param uuid unique id of model sought.
     * @return the model object matching the given uuid.
     */
    @GET @Path("/{uuid}")
    @Produces({  MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML  })
    public Object get(@PathParam("uuid") String uuid) {
        Object o = ObjectFactory.get().lookupByUUID(getApiClass(), uuid);
        return o;
    }
    
    /**
     * List all owners and just take the first one. 
     * 
     * TODO This is a temporary hack until we have authentication in place.
     * 
     * @param ownerCurator
     * @return
     */
    protected Owner getCurrentUsersOwner(OwnerCurator oCurator) {
        Owner owner = oCurator.listAll().get(0);
        return owner;
    }

//    @POST
//    @Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_FORM_URLENCODED })
//    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
//    public Object create(Form form) {
//        String newuuid = BaseModel.generateUUID();
//        Object args[] = new Object[1];
//        args[0] = newuuid;
//        BaseModel newobject = (BaseModel) 
//            MethodUtil.callNewMethod(getApiClass().getName(), args);
//        Iterator i = form.keySet().iterator();
//        while (i.hasNext()) {
//            String key = (String) i.next();
//            String value = form.getFirst(key); 
//            log.debug("value : " + value);
//            MethodUtil.callSetter(newobject, key, value);
//        }
//        if (log.isDebugEnabled()) {
//            log.debug("before store name: " + newobject.getName());
//            log.debug("before store uuid: " + newobject.getUuid());
//        }
//        return ObjectFactory.get().store(newobject);
//    }

   
    /**
     * Delete the model object matching the given uuid.
     * @param uuid unique id of object to be deleted.
     */
    @DELETE @Path("/{uuid}")
    public void delete(String uuid) {
        System.out.println("Delete called: " + uuid);
        Object obj = ObjectFactory.get().lookupByUUID(getApiClass(), uuid);
        ObjectFactory.get().delete(getApiClass(), obj);
    }
    
    protected Class getApiClass() {
        return model;
    }

}
