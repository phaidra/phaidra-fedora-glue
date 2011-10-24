/* The contents of this file are subject to the same license and copyright terms
 * as Fedora Commons (http://fedora-commons.org/).
 */
package org.phaidra.apihooks;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.fcrepo.common.Constants;
import org.fcrepo.server.Context;
import org.fcrepo.server.Server;
import org.fcrepo.server.errors.ModuleInitializationException;
import org.fcrepo.server.storage.DOWriter;

/**
 * REST-Capable API hook class.
 * @author Thomas Wana <thomas.wana@univie.ac.at>
 *
 */
public class APIRESTHooksImpl extends APIHooksImpl implements APIHooks 
{
	protected static Log log = LogFactory.getLog(APIRESTHooksImpl.class);
	
	public APIRESTHooksImpl(Map moduleParameters, Server server, String role)
			throws ModuleInitializationException 
	{
		super(moduleParameters, server, role);
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
		
		// check if all required parameters are available
		if(getParameter("restmethod")==null)
		{
			throw new ModuleInitializationException(
					"APIRESTHooksImpl: missing required parameters", this.getRole());
		}
		
		log.debug("initialized");
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
	public String runHook(String method, DOWriter w, Context context, String pid, Object[] params) throws APIHooksException
	{
		String rval = null;
		
		// Only do this if the method is enabled in fedora.fcfg
		if(getParameter(method)==null)
		{
			log.debug("runHook: method |"+method+"| not configured, not calling webservice");
			return "OK";
		}
		
		Iterator i = context.subjectAttributes();
		String attrs = "";
		while(i.hasNext())
		{
			String name = "";
			try
			{
				name = (String)i.next();
				String[] value = context.getSubjectValues(name);
				for(int j=0;j<value.length;j++)
				{	
					attrs += "&attr="+URLEncoder.encode(name+"="+value[j], "UTF-8");
					log.debug("runHook: will send |"+name+"="+value[j]+"| as subject attribute");
				}
			}
			catch(NullPointerException ex)
			{
				log.debug("runHook: caught NullPointerException while trying to retrieve subject attribute "+name);
			}
			catch(UnsupportedEncodingException ex)
			{
				log.debug("runHook: caught UnsupportedEncodingException while trying to encode subject attribute "+name);
			}
		}
		String paramstr = "";
		try
		{
			for(int j=0;j<params.length;j++)
			{
				paramstr += "&param"+Integer.toString(j)+"=";
				if(params[j]!=null)
				{
					String p = params[j].toString();
					paramstr += URLEncoder.encode(p, "UTF-8");
				}
			}
		}
		catch(UnsupportedEncodingException ex)
		{
			log.debug("runHook: caught UnsupportedEncodingException while trying to encode a parameter");
		}
		
		String loginId = context.getSubjectValue(Constants.SUBJECT.LOGIN_ID.uri);
		
		log.debug("runHook: called for method=|"+method+"|, pid=|"+pid+"|");
		try
		{
			// TODO: timeout? retries?
			URL url;
			URLConnection   urlConn;
		    DataOutputStream    printout;
		    BufferedReader     input;

			url = new URL (getParameter("restmethod"));
			urlConn = url.openConnection();
			urlConn.setDoInput (true);
			urlConn.setDoOutput (true);
			urlConn.setUseCaches (false);
			urlConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			printout = new DataOutputStream (urlConn.getOutputStream ());
			String content = "method="+URLEncoder.encode(method, "UTF-8")+
							 "&username="+URLEncoder.encode(loginId, "UTF-8")+
							 "&pid="+URLEncoder.encode(pid, "UTF-8")+
							 paramstr+
							 attrs;
			printout.writeBytes (content);
		    printout.flush ();
		    printout.close ();
		    
		    // Get response data.
		    input = new BufferedReader(new InputStreamReader(urlConn.getInputStream(), "UTF-8"));
		    String str;
		    rval = "";
		    while (null != ((str = input.readLine())))
		    {
			    rval += str+"\n";
		    }
		    input.close ();
		    
		    String ct = urlConn.getContentType();
		    if(ct.startsWith("text/xml"))
		    {
				log.debug("runHook: successful REST invocation for method |"+method+"|, returning: "+rval);
		    }
		    else if(ct.startsWith("text/plain"))
		    {
		    	log.debug("runHook: successful REST invocation for method |"+method+"|, but hook returned an error: "+rval);
		    	throw new Exception(rval);
		    }
		    else
		    {
		    	throw new Exception("Invalid content type "+ct);
		    }
		}
		catch(Exception ex)
		{
			log.error("runHook: error calling REST hook: "+ex.toString());
			throw new APIHooksException("Error calling REST hook: "+ex.toString(), ex);
		}
		
		return processResults(rval, w, context);
	}

}