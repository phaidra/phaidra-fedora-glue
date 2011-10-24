/* The contents of this file are subject to the same license and copyright terms
 * as Fedora Commons (http://fedora-commons.org/).
 */
package org.phaidra.apihooks;

import org.fcrepo.server.Context;
import org.fcrepo.server.storage.DOWriter;

/**
 * API-Hooks interface.
 * 
 * @author Thomas Wana <thomas.wana@univie.ac.at>
 *
 */
public interface APIHooks 
{
	/**
	 * Runs the hook if enabled in fedora.fcfg.
	 *
	 * @param method The name of the method that calls the hook
	 * @param w The opened DOWriter to use for object modifications
	 * @param context The calling context to get user information from
	 * @param pid The PID that is being accessed
	 * @param params Method parameters, depend on the method called
	 * @return Boolean TRUE if hook allows us to proceed, FALSE otherwise.
	 * @throws APIHookException If the remote call failed
	 */
	String runHook(String method, DOWriter w, Context context, String pid, Object[] params) throws APIHooksException;
}