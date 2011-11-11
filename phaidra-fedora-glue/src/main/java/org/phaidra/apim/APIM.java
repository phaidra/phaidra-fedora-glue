/* The contents of this file are subject to the same license and copyright terms
 * as Fedora Commons (http://fedora-commons.org/).
 */
package org.phaidra.apim;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import org.fcrepo.server.Context;
import org.fcrepo.server.ReadOnlyContext;
import org.fcrepo.server.Server;
import org.fcrepo.server.errors.ModuleInitializationException;
import org.fcrepo.server.errors.ServerInitializationException;
import org.fcrepo.server.management.Management;
import org.fcrepo.server.utilities.AxisUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

        
public class APIM {
    
    private static final Logger logger = LoggerFactory.getLogger(APIM.class);
       
    private static String fedoraHome;
    
    private Management s_management;
    
    public APIM() throws ServerInitializationException, ModuleInitializationException{
        
        // Read properties file.
        Properties properties = new Properties();
        try {
            properties.load(Thread.currentThread().getContextClassLoader().getResourceAsStream("META-INF/phaidra-fedora-glue.properties"));
            logger.debug("Properties file loaded:" + properties.toString());
            fedoraHome = properties.getProperty("org.phaidra.fedorahome");
        } catch (IOException e) {
            logger.error("Cannot read properties file, using default values.");
            fedoraHome = "/usr/local/fedora";
        } 
        
        logger.debug(APIM.class + " ctor");
        
        s_management =
                (Management) Server
                        .getInstance(new File(fedoraHome), false)
                        .getModule("org.fcrepo.server.management.Management");
        if (s_management == null) {
            throw new ModuleInitializationException("Can't get Management module from Server.getModule (in "+ APIM.class +")",
                                                    "org.fcrepo.server.management.Management");
        }
    }
    
    // Fuer Phaidra: mehrere Relationships auf einmal anlegen
    public boolean addRelationships(org.fcrepo.server.types.gen.RelationshipTuple[] relationships) throws java.rmi.RemoteException
    {
        logger.debug("start: addRelationships");

        try {
            boolean rval = true;
            for (int i = 0; i < relationships.length; i++) {
                 
                logger.debug("adding relationship: \nsubjekt:"
                        +relationships[i].getSubject() 
                        + "\npredicate:"
                        +relationships[i].getPredicate()
                        + "\nobject:"
                        +relationships[i].getObject()
                        + "\nliteral:"
                        +relationships[i].isIsLiteral()
                        + "\ndatatype:"
                        +relationships[i].getDatatype()
                        );
                
                Context context=ReadOnlyContext.getSoapContext();
                boolean r = s_management.addRelationship(
                                                           context,
                                                           relationships[i].getSubject(), 
                                                           relationships[i].getPredicate(), 
                                                           relationships[i].getObject(),
                                                           relationships[i].isIsLiteral(), 
                                                           relationships[i].getDatatype()
                                                           );
                if(r == false){
                    rval = false;
                }
            }
            
            return rval;
            
        } catch (Throwable th) {
            logger.error("Error adding relationships", th);
            throw AxisUtility.getFault(th);
        } finally {
            logger.debug("end: addRelationships");
        }
    }

    // Fuer Phaidra: mehrere Relationships auf einmal entfernen
    public boolean purgeRelationships(org.fcrepo.server.types.gen.RelationshipTuple[] relationships) throws java.rmi.RemoteException
    {
        logger.debug("start: purgeRelationships");

        try {
            
            boolean rval = true;
            for (int i = 0; i < relationships.length; i++) {
                 
                logger.debug("purging relationship: \nsubjekt:"
                        +relationships[i].getSubject() 
                        + "\npredicate:"
                        +relationships[i].getPredicate()
                        + "\nobject:"
                        +relationships[i].getObject()
                        + "\nliteral:"
                        +relationships[i].isIsLiteral()
                        + "\ndatatype:"
                        +relationships[i].getDatatype()
                        );
                
                Context context=ReadOnlyContext.getSoapContext();
                boolean r = s_management.purgeRelationship(
                                                           context,
                                                           relationships[i].getSubject(), 
                                                           relationships[i].getPredicate(), 
                                                           relationships[i].getObject(),
                                                           relationships[i].isIsLiteral(), 
                                                           relationships[i].getDatatype()
                                                           );
                if(r == false){
                    rval = false;
                }
            }
            
            return rval;
            
        } catch (Throwable th) {
            logger.error("Error purging relationships", th);
            throw AxisUtility.getFault(th);
        } finally {
            logger.debug("end: purgeRelationships");
        }
    }
        
}

    