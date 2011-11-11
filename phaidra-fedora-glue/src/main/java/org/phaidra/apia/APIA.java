/* The contents of this file are subject to the same license and copyright terms
 * as Fedora Commons (http://fedora-commons.org/).
 */
package org.phaidra.apia;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.rmi.RemoteException;
import java.util.Map;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.axis.MessageContext;
import org.apache.axis.client.Call;
import org.apache.axis.client.Service;
import org.apache.axis.transport.http.HTTPConstants;
import org.fcrepo.common.Constants;
import org.fcrepo.server.Context;
import org.fcrepo.server.MultiValueMap;
import org.fcrepo.server.Parameterized;
import org.fcrepo.server.ReadOnlyContext;
import org.fcrepo.server.Server;
import org.fcrepo.server.errors.GeneralException;
import org.fcrepo.server.errors.ModuleInitializationException;
import org.fcrepo.server.errors.ServerException;
import org.fcrepo.server.errors.ServerInitializationException;
import org.fcrepo.server.errors.authorization.AuthzDeniedException;
import org.fcrepo.server.errors.authorization.AuthzException;
import org.fcrepo.server.security.Authorization;
import org.fcrepo.server.security.servletfilters.ExtendedHttpServletRequest;
import org.fcrepo.server.utilities.AxisUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.InputSource;
        
public class APIA {
    
    private static final Logger logger = LoggerFactory.getLogger(APIA.class);
       
    private static String fedoraHome;
    
    private Authorization m_authorizationModule;
    
    public APIA() throws ServerInitializationException, ModuleInitializationException{
        
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
        
        logger.debug(APIA.class + " ctor");
        
        m_authorizationModule =
                (Authorization) Server
                        .getInstance(new File(fedoraHome))
                        .getModule("org.fcrepo.server.security.Authorization");
        if (m_authorizationModule == null) {
            throw new ModuleInitializationException("Can't get Authorization module from Server.getModule (in "+ APIA.class +")",
                                                    "org.fcrepo.server.security.Authorization");
        }
    }
    
    public String pfindObjects(String query, int hitPageStart, int hitPageSize, int snippetsMax, int fieldMaxLength, String indexName) throws RemoteException {

        MessageContext mctx = MessageContext.getCurrentContext();
        HttpServletRequest req = (HttpServletRequest) mctx.getProperty(HTTPConstants.MC_HTTP_SERVLETREQUEST);
        Context context = ReadOnlyContext.getContext(Constants.HTTP_REQUEST.REST.uri, req);
        try {
            logger.debug("findObjectsAndRights");                
            return pfindObjects(context, query, hitPageStart, hitPageSize, snippetsMax, fieldMaxLength, indexName);
        } catch (Throwable th) {
            logger.error("Error finding objects", th);
            throw AxisUtility.getFault(th);
        }
    }  
    
    /**
     * findObjectsAndRights
     * 
     * Die Phaidra-spezifische Ergaenzung zu gfindObjects vom gsearch-Service. Schleift
     * die Suchanfrage 1:1 durch gsearch durch, fettet jedoch das Ergebnis-XML mit
     * Berechtigungsinformationen auf.
     * 
     * Dokumentation der Schnittstelle siehe gsearch-Doku.
     * 
     * Siehe https://ylvi.univie.ac.at/confluence/display/swdevel/Phaidra+pfindObjects
     */
    public String pfindObjects(Context context,
            String query, int hitPageStart, int hitPageSize, int snippetsMax,
            int fieldMaxLength, String indexName)
            throws ServerException
    {
        logger.debug("findObjectsAndRights");
        logger.debug("query:"+query);

        // Suchanfrage an gsearch schicken
        String gsearchxml = null;
          try
          {
              Service service = new Service();
              Call call = (Call)service.createCall();
              call.setTargetEndpointAddress(new java.net.URL(((Parameterized)m_authorizationModule).getParameter("gsearchsoapproxy")));
              call.setOperationName(new QName(((Parameterized)m_authorizationModule).getParameter("gsearchsoapuri"), "gfindObjects"));
              gsearchxml = (String)call.invoke(new Object[] { query, hitPageStart, hitPageSize, snippetsMax, fieldMaxLength, indexName, "" });
          }
          catch(Exception ex)
          {
              throw new GeneralException("Error calling gsearch via SOAP : "+ex.getMessage(), ex);
          }
          
          logger.debug("got XML from gsearch: |"+gsearchxml+"|");
          
          // Fuer alle "Objekte" im Ergebnis-XML...
          DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
          Document gsearchdoc = null;
          try
          {
              DocumentBuilder db = dbf.newDocumentBuilder();
              gsearchdoc = db.parse(new InputSource(new StringReader(gsearchxml)));
          }
          catch(Exception ex)
          {
              throw new GeneralException("Error parsing gsearch output : "+ex.getMessage(), ex);
          }
          
          Element doc = gsearchdoc.getDocumentElement();
          NodeList nl = doc.getElementsByTagName("field");
          if(nl!=null && nl.getLength()>0)
          {
              for(int i=0; i<nl.getLength(); i++)
              {
                  String pid = "";
                  Element el = (Element)nl.item(i);
                  if(el.getAttribute("name").equals("PID"))
                  {
                      pid = el.getFirstChild().getNodeValue();
                      String permissions = "";

                      // Zugriffsrechte ermitteln
                      // Schaun, ob der User einen fiktiven Datastream "READONLY" lesen darf. Wenn ja,
                      // hat er Leserechte am Objekt.
                      try
                      {
                          m_authorizationModule.enforceGetDatastreamDissemination(context, pid, "READONLY", null);
                          permissions += "read,";
                      }
                      catch(AuthzDeniedException ex)
                      {
                          // fall through
                      }
                      catch(AuthzException ex)
                      {
                          throw new GeneralException("error enforcing policy: "+ex.getMessage(), ex);
                      }
                      
                      // Schaun, ob der User einen fiktiven Datastream "READWRITE" lesen darf. Wenn ja,
                      // hat er Schreibrechte am Objekt.
                      try
                      {
                          m_authorizationModule.enforceGetDatastreamDissemination(context, pid, "READWRITE", null);
                          permissions += "write,";
                      }
                      catch(AuthzDeniedException ex)
                      {
                          // fall through
                      }
                      catch(AuthzException ex)
                      {
                          throw new GeneralException("error enforcing policy: "+ex.getMessage(), ex);
                      }
                      
                      // Neue Node an Parent anhaengen
                      Node objekt = el.getParentNode();
                      
                      Element permele = gsearchdoc.createElement("permissions");
                      Text permtext = gsearchdoc.createTextNode(permissions);
                      permele.appendChild(permtext);
                      objekt.appendChild(permele);
                  }
              }
              
              // Wiederum XML-String erzeugen
              try
              {
                  DOMImplementationRegistry reg = DOMImplementationRegistry.newInstance();
                  DOMImplementationLS impl = (DOMImplementationLS)reg.getDOMImplementation("LS");
                  LSSerializer writer = impl.createLSSerializer();
                  gsearchxml = writer.writeToString(gsearchdoc);
              }
              catch(Exception ex)
              {
                  throw new GeneralException("Error serializing modified gsearch output : "+ex.getMessage(), ex);
              }
              
          }
          
          logger.debug("returning: |"+gsearchxml+"|");
          // fertig
          return gsearchxml;
    }
    
    
  
}

    