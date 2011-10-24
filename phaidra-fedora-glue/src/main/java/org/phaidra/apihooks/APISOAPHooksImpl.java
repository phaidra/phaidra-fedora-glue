/* The contents of this file are subject to the same license and copyright terms
 * as Fedora Commons (http://fedora-commons.org/).
 */
package org.phaidra.apihooks;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.xml.namespace.QName;

import org.apache.axis.client.Call;
import org.apache.axis.client.Service;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.fcrepo.common.Constants;
import org.fcrepo.server.Context;
import org.fcrepo.server.Server;
import org.fcrepo.server.errors.ModuleInitializationException;
import org.fcrepo.server.storage.DOWriter;


/**
 * SOAP-Capable API hook class.
 * @author Thomas Wana <thomas.wana@univie.ac.at>
 *
 */
public class APISOAPHooksImpl extends APIHooksImpl implements APIHooks 
{
	protected static Log log = LogFactory.getLog(APISOAPHooksImpl.class);
	
	public APISOAPHooksImpl(Map moduleParameters, Server server, String role)
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
		if(getParameter("soapuri")==null ||
				getParameter("soapproxy")==null ||
				getParameter("soapmethod")==null)
		{
			throw new ModuleInitializationException(
					"APISOAPHooksImpl: missing required parameters", this.getRole());
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
		Set<String> attrs = new HashSet<String>();
		while(i.hasNext())
		{
			String name = "";
			try
			{
				name = (String)i.next();
				String[] value = context.getSubjectValues(name);
				for(int j=0;j<value.length;j++)
				{	
					attrs.add(name+"="+value[j]);
				}
			}
			catch(NullPointerException ex)
			{
				log.debug("runHook: caught NullPointerException while trying to retrieve subject attribute "+name);
			}
		}
		for(Iterator<String> j=attrs.iterator();j.hasNext();)
		{
			log.debug("runHook: will send |"+j.next()+"| as subject attribute");
		}
		
		String loginId = context.getSubjectValue(Constants.SUBJECT.LOGIN_ID.uri);
		
		log.debug("runHook: called for method=|"+method+"|, pid=|"+pid+"|");
		try
		{
			Service service = new Service();
			Call call = (Call)service.createCall();
			call.setTargetEndpointAddress(new java.net.URL(getParameter("soapproxy")));
			call.setOperationName(new QName(getParameter("soapuri"), getParameter("soapmethod")));
			// TODO: timeout? retries?
			rval = (String)call.invoke(new Object[] { method, loginId, pid, params, attrs.toArray() });
			
			log.debug("runHook: successful SOAP invocation for method |"+method+"|, returning "+rval);
		}
		catch(Exception ex)
		{
			log.error("runHook: error calling SOAP hook: "+ex.getMessage());
			throw new APIHooksException("Error calling SOAP hook: "+ex.getMessage(), ex);
		}
		
		return processResults(rval, w, context);
	}

}