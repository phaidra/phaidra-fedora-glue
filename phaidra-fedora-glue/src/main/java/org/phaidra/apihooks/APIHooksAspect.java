/* The contents of this file are subject to the same license and copyright terms
 * as Fedora Commons (http://fedora-commons.org/).
 */
package org.phaidra.apihooks;

import java.io.File;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Date;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.fcrepo.server.Context;
import org.fcrepo.server.Server;
import org.fcrepo.server.errors.ModuleInitializationException;
import org.fcrepo.server.errors.ServerInitializationException;
import org.fcrepo.server.management.DefaultManagement;
import org.fcrepo.server.storage.DOManager;
import org.fcrepo.server.storage.DOReader;
import org.fcrepo.server.storage.DOWriter;
import org.fcrepo.server.storage.types.Datastream;
import org.fcrepo.server.storage.types.DatastreamXMLMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Aspect
public class APIHooksAspect {

    private static final Logger logger = LoggerFactory
            .getLogger(APIHooksAspect.class);

    private static String fedoraHome;
    
    private APIHooks m_hooks;
    
    private DOManager m_manager;

    public APIHooksAspect()
            throws ServerInitializationException, ModuleInitializationException {
    	
    	//FIXME
    	fedoraHome = "/opt/phaidra/app/fedora";
    	
        m_hooks =
                (APIHooks) Server
                        .getInstance(new File(fedoraHome))
                        .getModule("org.phaidra.apihooks.APIHooks");
        if (m_hooks == null) {
            throw new ModuleInitializationException("Can't get hooks module from Server.getModule",
                                                    "org.phaidra.apihooks.APIHooks");
        }
        
        m_manager = (DOManager) Server.getInstance(new File(fedoraHome))
                .getModule("org.fcrepo.server.storage.DOManager");
        
        if (m_manager == null) {
            throw new ModuleInitializationException("Can't get DOManager module from Server.getModule",
                                                    "org.fcrepo.server.storage.DefaultDOManager");
        }
    }
    
    private void logCall() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        logger.info(stack[2].getMethodName() + " called from "
                + stack[3].getClassName() + "." + stack[3].getMethodName()
                + "(" + stack[3].getLineNumber() + ")");
    }
    

    @Pointcut("execution(void org.fcrepo.server.storage.SimpleDOWriter.addDatastream(..)) && args(datastream, addNewVersion) && !within(org.phaidra.apihooks.APIHooksAspect)")
    public void simpleDOWriterAddDatastream(Datastream datastream,
                                            boolean addNewVersion) {
    }

    @Pointcut("execution(* org.fcrepo.server.management.DefaultManagement.addDatastream(..)) "
            + "&& args(context, pid, dsID, altIDs, dsLabel, versionable, MIMEType, formatURI, dsLocation, controlGroup, dsState, checksumType, checksum, logMessage)"
            + "&& !within(org.phaidra.apihooks.APIHooksAspect)")
    public void addDatastream(Context context,
                              String pid,
                              String dsID,
                              String[] altIDs,
                              String dsLabel,
                              boolean versionable,
                              String MIMEType,
                              String formatURI,
                              String dsLocation,
                              String controlGroup,
                              String dsState,
                              String checksumType,
                              String checksum,
                              String logMessage) {
    }

    /**
     * wormhole 
     */
    @Pointcut("simpleDOWriterAddDatastream(datastream, addNewVersion) && cflow(addDatastream(context, pid, dsID, altIDs, dsLabel, versionable, MIMEType, formatURI, dsLocation, controlGroup, dsState, checksumType, checksum, logMessage))")
    public void addDatastreamHook(Context context,
                                  String pid,
                                  String dsID,
                                  String[] altIDs,
                                  String dsLabel,
                                  boolean versionable,
                                  String MIMEType,
                                  String formatURI,
                                  String dsLocation,
                                  String controlGroup,
                                  String dsState,
                                  String checksumType,
                                  String checksum,
                                  String logMessage,
                                  Datastream datastream,
                                  boolean addNewVersion
                                  ) {

    }

    @Around("addDatastreamHook(context, pid, dsID, altIDs, dsLabel, versionable, MIMEType, formatURI, dsLocation, controlGroup, dsState, checksumType, checksum, logMessage, datastream, addNewVersion)")
    public Object addDatastreamHook(Context context,
                                    String pid,
                                    String dsID,
                                    String[] altIDs,
                                    String dsLabel,
                                    boolean versionable,
                                    String MIMEType,
                                    String formatURI,
                                    String dsLocation,
                                    String controlGroup,
                                    String dsState,
                                    String checksumType,
                                    String checksum,
                                    String logMessage,
                                    Datastream datastream,
                                    boolean addNewVersion,                                    
                                    ProceedingJoinPoint thisJoinPoint)
            throws Throwable {

            logCall();
    
            // First let the addDatastream finish.. we cannot use @after advice because we need the DOWriter which is in thisJoinPoint.getThis()
            Object retVal = thisJoinPoint.proceed();
    
            String hcontent = null;
            if (controlGroup.equals("X")) {
                hcontent = new String((((DatastreamXMLMetadata) datastream).xmlContent), "UTF8");
            }
    
            DOWriter w = (DOWriter) thisJoinPoint.getThis();
            String hv =
                    m_hooks.runHook("addDatastream", w, context, pid, new Object[] {
                            datastream.DatastreamID, datastream.DSMIME, hcontent,
                            datastream.DSLabel});
    
            if (!hv.startsWith("OK")) throw new APIHooksException(hv);        
            
            return retVal;
    }
    
    @After("addDatastream(context, pid, dsID, altIDs, dsLabel, versionable, MIMEType, formatURI, dsLocation, controlGroup, dsState, checksumType, checksum, logMessage)")
    public void addDatastreamHookPostCommit(Context context,
                                    String pid,
                                    String dsID,
                                    String[] altIDs,
                                    String dsLabel,
                                    boolean versionable,
                                    String MIMEType,
                                    String formatURI,
                                    String dsLocation,
                                    String controlGroup,
                                    String dsState,
                                    String checksumType,
                                    String checksum,
                                    String logMessage,                                    
                                    ProceedingJoinPoint thisJoinPoint)
            throws Throwable {

        logCall();
        
        DOWriter w = null;
        
        try{
                
          w = m_manager.getWriter(Server.USE_DEFINITIVE_STORE, context, pid);
   
          m_hooks.runHook("addDatastream_PostCommit", w, context, pid, new Object[] { dsID, MIMEType, null, dsLabel});
          
          w.commit("Added a new datastream (addDatastream_PostCommit)");

        }catch(Exception e){
          logger.info("Caught exception while running addDatastream_PostCommit-Hook: "+e.getMessage());
        }finally{
            // DefaultManagement.finishModification
            m_manager.releaseWriter(w);
        }
        
    }
    
    @Pointcut("execution(* org.fcrepo.server.management.DefaultManagement.modifyDatastreamByValue(..)) "
            + "&& args(context, pid, datastreamId, altIDs, dsLabel, mimeType, formatURI, dsContent, checksumType, checksum, logMessage, lastModifiedDate)"
            + "&& !within(org.phaidra.apihooks.APIHooksAspect)")
    public void modifyDatastreamByValue(Context context,
                                        String pid,
                                        String datastreamId,
                                        String[] altIDs,
                                        String dsLabel,
                                        String mimeType,
                                        String formatURI,
                                        InputStream dsContent,
                                        String checksumType,
                                        String checksum,
                                        String logMessage,
                                        Date lastModifiedDate) {
    }
    
    /**
     * wormhole 
     */
    @Pointcut("simpleDOWriterAddDatastream(datastream, addNewVersion) && cflow(modifyDatastreamByValue(context, pid, datastreamId, altIDs, dsLabel, mimeType, formatURI, dsContent, checksumType, checksum, logMessage, lastModifiedDate))")
    public void modifyDatastreamByValueHook(Context context,
                                  String pid,
                                  String datastreamId,
                                  String[] altIDs,
                                  String dsLabel,
                                  String mimeType,
                                  String formatURI,
                                  InputStream dsContent,
                                  String checksumType,
                                  String checksum,
                                  String logMessage,
                                  Date lastModifiedDate,
                                  Datastream datastream,
                                  boolean addNewVersion
                                  ) {

    }
    
    @Around("modifyDatastreamByValueHook(context, pid, datastreamId, altIDs, dsLabel, mimeType, formatURI, dsContent, checksumType, checksum, logMessage, lastModifiedDate, datastream, addNewVersion)")
    public Object modifyDatastreamByValueHook(Context context,
                                    String pid,
                                    String datastreamId,
                                    String[] altIDs,
                                    String dsLabel,
                                    String mimeType,
                                    String formatURI,
                                    InputStream dsContent,
                                    String checksumType,
                                    String checksum,
                                    String logMessage,
                                    Date lastModifiedDate,
                                    Datastream datastream,
                                    boolean addNewVersion,                                   
                                    ProceedingJoinPoint thisJoinPoint)
            throws Throwable {

            logCall();
    
            // First let the addDatastream finish.. we cannot use @after advice because we need the DOWriter which is in thisJoinPoint.getThis()
            Object retVal = thisJoinPoint.proceed();
    
            String hcontent = null;
            if (dsContent != null) {
                try {
                    hcontent=new String(((DatastreamXMLMetadata) datastream).xmlContent, "UTF8");
                } catch (UnsupportedEncodingException e) {
                    throw new APIHooksException("Error converting content to UTF-8: "+e.toString());
                }
            }
    
            DOWriter w = (DOWriter) thisJoinPoint.getThis();
           String hv =
                    m_hooks.runHook("modifyDatastreamByValue", w, context, pid, new Object[] {
                            datastream.DatastreamID, datastream.DSMIME, hcontent,
                            datastream.DSLabel});
    
            if (!hv.startsWith("OK")) throw new APIHooksException(hv);        
            
            return retVal;
    }


    // FIXME: in fedora 3.5 <boolean force> becomes <Date lastModifiedDate>
    @Before("modifyDatastreamByValue(context, pid, datastreamId, altIDs, dsLabel, mimeType, formatURI, dsContent, checksumType, checksum, logMessage, lastModifiedDate)")
    public void modifyDatastreamByValueCheckExists(Context context,
                                    String pid,
                                    String datastreamId,
                                    String[] altIDs,
                                    String dsLabel,
                                    String mimeType,
                                    String formatURI,
                                    InputStream dsContent,
                                    String checksumType,
                                    String checksum,
                                    String logMessage,
                                    Date lastModifiedDate,                              
                                    JoinPoint thisJoinPoint)
            throws Throwable {

            logCall();
            
            // Does this DS exists?
            DOReader r = m_manager.getReader(Server.GLOBAL_CHOICE, context, pid);
            for (String dsId : r.ListDatastreamIDs(null)) {
                if(dsId.equals(datastreamId)) return;
            }
    
            // No, so create new..

            try{
                DefaultManagement mngmt = (DefaultManagement)thisJoinPoint.getThis();            
                // Upload the file
                String dsLocation = mngmt.putTempStream(context, dsContent);
                // Add
                mngmt.addDatastream(context, pid, datastreamId, altIDs, dsLabel, true, mimeType, formatURI, dsLocation, "X", "A", checksumType, checksum, logMessage);
            }finally{
                // This stream will be used by the original call to modifyDatastreamByValue
                dsContent.reset();
            }
    }    
    
    @Pointcut("execution(void org.fcrepo.server.storage.SimpleDOWriter.commit(..)) && !within(org.phaidra.apihooks.APIHooksAspect)")
    public void simpleDOWriterCommit() {
    }
    
    @Pointcut("execution(* org.fcrepo.server.management.DefaultManagement.modifyObject(..)) "
            + "&& args(context, pid, state, label, ownerId, logMessage)"
            + "&& !within(org.phaidra.apihooks.APIHooksAspect)")
    public void modifyObject(Context context, String pid, String state, String label, String ownerId, String logMessage) {
    }
    
    /**
     * wormhole 
     */
    @Pointcut("simpleDOWriterCommit() && cflow(modifyObject(context, pid, state, label, ownerId, logMessage))")
    public void modifyObjectHook(Context context, String pid, String state, String label, String ownerId, String logMessage) {

    }
    
    @Around("modifyObjectHook(context, pid, state, label, ownerId, logMessage)")
    public Object modifyObjectHook(Context context, 
                                 String pid, 
                                 String state, 
                                 String label, 
                                 String ownerId, 
                                 String logMessage,                              
                                 ProceedingJoinPoint thisJoinPoint)
            throws Throwable {

            logCall();
    
            String hv = m_hooks.runHook("modifyObject", (DOWriter)thisJoinPoint.getThis(), context, pid, new Object[] { state, label, ownerId });
            if(!hv.startsWith("OK"))
                throw new APIHooksException(hv);

            return thisJoinPoint.proceed();
    } 
    
}