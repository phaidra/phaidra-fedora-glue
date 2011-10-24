/* The contents of this file are subject to the same license and copyright terms
 * as Fedora Commons (http://fedora-commons.org/).
 */
package org.phaidra.apihooks;

import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.fcrepo.common.Constants;
import org.fcrepo.server.Context;
import org.fcrepo.server.Module;
import org.fcrepo.server.Server;
import org.fcrepo.server.errors.GeneralException;
import org.fcrepo.server.errors.ModuleInitializationException;
import org.fcrepo.server.errors.ServerException;
import org.fcrepo.server.storage.DOWriter;
import org.fcrepo.server.storage.types.AuditRecord;
import org.fcrepo.server.storage.types.Datastream;
import org.fcrepo.server.storage.types.DatastreamXMLMetadata;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;

/**
 * API hooks abstract superclass.
 * 
 * @author Thomas Wana <thomas.wana@univie.ac.at>
 *
 */
public abstract class APIHooksImpl extends Module implements APIHooks {

	protected static Log log = LogFactory.getLog(APISOAPHooksImpl.class);	
	
	public APIHooksImpl(Map moduleParameters, Server server, String role)
			throws ModuleInitializationException {
		super(moduleParameters, server, role);
	}

	/**
	 * Runs the hook if enabled in fedora.fcfg.
	 *
	 * @param method The name of the method that calls the hook
	 * @param pid The PID that is being accessed
	 * @param params Method parameters, depend on the method called
	 * @return String Hook verdict. Begins with "OK" if it's ok to proceed.
	 * @throws APIHooksException If the remote call went wrong
	 */
	public abstract String runHook(String method, DOWriter w, Context context, String pid, Object[] params) throws APIHooksException;
	
	/**
	 * Process the Hook Result XML and act accordingly. 
	 * 
	 * @param hookResultXML The XML from the hook call
	 * @param w The opened DOWriter to modify the object (active transaction)
	 * @return String The verdict and the additionalInfo
	 * @throws APIHooksException If parsing or processing failed
	 */
	protected String processResults(String hookResultXML, DOWriter w, Context context) throws APIHooksException
	{	
		String verdict="", additionalInfo="";
		
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		dbf.setNamespaceAware(true);
		Document hookResult = null;
		try
		{
			DocumentBuilder db = dbf.newDocumentBuilder();
			hookResult = db.parse(new InputSource(new StringReader(hookResultXML)));
		}
		catch(Exception ex)
		{
			throw new APIHooksException("Error parsing hook XML: "+ex.getMessage(), ex);
		}
		
		Element doc = hookResult.getDocumentElement();
		
		// Verdict-Node suchen
		NodeList nl = doc.getElementsByTagName("ph:verdict");
		if(nl==null)
		{
			throw new APIHooksException("Error processing hook XML: no 'verdict' node found");
		}
		Element el = (Element)nl.item(0);
		if(el==null)
		{
			throw new APIHooksException("Error processing hook XML: no 'verdict' node found (2)");
		}
		
		verdict = el.getFirstChild().getNodeValue();
		additionalInfo = el.getAttribute("additionalInfo");
		
		// Wenn das verdict "OK" ist, auch nach abzuarbeitenden Kommandos suchen
		if(verdict.equalsIgnoreCase("OK") && w!=null)
		{
			// replaceIntoDatastream
			nl = doc.getElementsByTagName("ph:replaceIntoDatastream");
			if(nl!=null && nl.getLength()>0)
			{
				for(int i=0;i<nl.getLength();i++)
				{
					el = (Element)nl.item(i);
					String datastream = el.getAttribute("datastream");
					String dsContent = el.getFirstChild().getNodeValue();
					
					log.debug("processResults: got command: replaceIntoDatastream with DS "+datastream+" and content "+dsContent);
					
					replaceIntoDatastream(w, context, datastream, dsContent);
				}
			}
			nl = doc.getElementsByTagName("ph:purgeDatastream");
			if(nl!=null && nl.getLength()>0)
			{
				for(int i=0;i<nl.getLength();i++)
				{
					el = (Element)nl.item(i);
					String datastream = el.getAttribute("datastream");
					
					log.debug("processResults: got command: purgeDatastream with DS "+datastream);
					
					purgeDatastream(w, context, datastream);
				}
			}
		}
		
		log.debug("processResults: returning "+verdict+": "+additionalInfo);
		return verdict+": "+additionalInfo;
	}
	
	/**
	 * Add or modify a datastream using the given DOWriter. Only XML datastreams are supported!
	 * 
	 * @param w Opened DOWriter to use for the modifications
	 * @param datastream Datastream name
	 * @param dsContent New Datastream content
	 * @throws APIHooksException
	 */
	private void replaceIntoDatastream(DOWriter w, Context context, String datastream, String dsContent) 
		throws APIHooksException
	{
		try 
		{
			Date nowUTC = Server.getCurrentDate(context);
			
			AuditRecord audit=new AuditRecord();
            audit.id=w.newAuditRecordID();
			
            DatastreamXMLMetadata newds = new DatastreamXMLMetadata();
			Datastream orig = w.GetDatastream(datastream, null);
			if(orig==null)
			{
				log.info("!!!!!!!!!!!!!!!!!! replaceIntoDatastream: creating new datastream ("+datastream+")");
				
				// Datastream not found - create it
				newds.DSInfoType = "";
				newds.isNew = true;
				newds.DSControlGrp = "X";
				newds.DSVersionable = true;
				newds.DSState = "A";
				newds.DatastreamID = datastream;
				newds.DSVersionID = datastream + ".0";
				newds.DSLabel = "Created by Phaidra Hooks";
				newds.DSLocation = null;
				newds.DSFormatURI = "";
				newds.DatastreamAltIDs = new String[0];
				newds.DSMIME = "text/xml";
				newds.DSChecksumType = null;
				newds.DSCreateDT = nowUTC;

				newds.xmlContent = getEmbeddableXML(new InputSource(new StringReader(dsContent)));
				
				audit.action="addDatastream";
			}
			else
			{
				log.info("!!!!!!!!!!!!!!!!!! replaceIntoDatastream: updating existing datastream ("+datastream+")");
				
				// Datastream found - update it
				newds.DSMDClass=((DatastreamXMLMetadata) orig).DSMDClass;
				newds.DatastreamID=orig.DatastreamID;
	            newds.DSControlGrp=orig.DSControlGrp;
	            newds.DSInfoType=orig.DSInfoType;
	            // next, those that can be changed by client...
	            newds.DSState = orig.DSState;
	            newds.DSVersionable = orig.DSVersionable;
	            
	            // update ds version level attributes, and
	            // make sure ds gets a new version id
	            newds.DSVersionID=w.newDatastreamID(datastream);
	            newds.DSLabel=orig.DSLabel;
	            newds.DatastreamAltIDs=orig.DatastreamAltIDs;
	            newds.DSMIME=orig.DSMIME;
	            newds.DSFormatURI=orig.DSFormatURI;
	            newds.DSCreateDT=nowUTC;
	            newds.DSChecksumType = orig.DSChecksumType;
				newds.xmlContent = getEmbeddableXML(new InputSource(new StringReader(dsContent)));
				
				audit.action="modifyDatastreamByValue";
			}

			w.addDatastream(newds, newds.DSVersionable);
	
            // add the audit record
            audit.processType="Fedora API-M";
            audit.componentID=newds.DatastreamID;
            audit.responsibility=context.getSubjectValue(Constants.SUBJECT.LOGIN_ID.uri);
            audit.date=nowUTC;
            audit.justification="Phaidra Hooks";
            w.getAuditRecords().add(audit);			
		} 
		catch (ServerException e) 
		{
			throw new APIHooksException("replaceIntoDatastream failed: "+e.getMessage(), e);
		}
	}
	
	/**
	 * Purge a datastream using the given DOWriter.
	 * 
	 * @param w Opened DOWriter to use for the modifications
	 * @param datastream Datastream name
	 * @throws APIHooksException
	 */
	private void purgeDatastream(DOWriter w, Context context, String datastream) 
		throws APIHooksException
	{
		log.info("!!!!!!!!!!! purgeDatastream " + datastream);
		try
		{
            Date[] deletedDates =
                w.removeDatastream(datastream, null, null);
            
            AuditRecord audit = new AuditRecord();
            audit.id = w.newAuditRecordID();
            audit.processType = "Fedora API-M";
            audit.action = "purgeDatastream";
            audit.componentID = datastream;
            audit.responsibility =
                    context.getSubjectValue(Constants.SUBJECT.LOGIN_ID.uri);
            Date nowUTC = Server.getCurrentDate(context);
            audit.date = nowUTC;
            audit.justification = getPurgeLogMessage("datastream", datastream, null, null, deletedDates);
            w.getAuditRecords().add(audit);
		}
		catch (ServerException e) 
		{
			throw new APIHooksException("purgeDatastream failed: "+e.getMessage(), e);
		}
	}
	
    private byte[] getEmbeddableXML(InputSource in) throws GeneralException {
        // parse with xerces and re-serialize the fixed xml to a byte array
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            OutputFormat fmt = new OutputFormat("XML", "UTF-8", true);
            fmt.setIndent(2);
            fmt.setLineWidth(120);
            fmt.setPreserveSpace(false);
            fmt.setOmitXMLDeclaration(true);
            fmt.setOmitDocumentType(true);
            XMLSerializer ser = new XMLSerializer(out, fmt);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(in);
            ser.serialize(doc);
            return out.toByteArray();
        } catch (Exception e) {
            String message = e.getMessage();
            if (message == null) message = "";
            throw new GeneralException("XML was not well-formed. " + message, e);
        }
    }
    
    private String getPurgeLogMessage(String kindaThing,
            String id,
            Date start,
            Date end,
            Date[] deletedDates) {
		SimpleDateFormat formatter =
		new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
		StringBuffer buf = new StringBuffer();
		buf.append("Purged ");
		buf.append(kindaThing);
		buf.append(" (ID=");
		buf.append(id);
		buf.append("), versions ranging from ");
		if (start == null) {
			buf.append("the beginning of time");
		} else {
			buf.append(formatter.format(start));
		}
		buf.append(" to ");
		if (end == null) {
			buf.append("the end of time");
		} else {
			buf.append(formatter.format(end));
		}
		buf.append(".  This resulted in the permanent removal of ");
		buf.append(deletedDates.length + " ");
		buf.append(kindaThing);
		buf.append(" version(s) (");
		for (int i = 0; i < deletedDates.length; i++) {
			if (i > 0) {
				buf.append(", ");
			}
			buf.append(formatter.format(deletedDates[i]));
		}
		buf.append(") and all associated audit records.");
		return buf.toString();
	}    
	
	/**
	 * Initializes the Module based on configuration parameters.
	 * 
	 * @throws ModuleInitializationException
	 *             If initialization values are invalid or initialization fails
	 *             for some other reason.
	 */
	public void initModule() throws ModuleInitializationException
	{
		super.initModule();
		log.debug("initialized");
	}

}