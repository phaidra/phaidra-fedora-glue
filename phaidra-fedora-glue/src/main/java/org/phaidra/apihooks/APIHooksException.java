/* The contents of this file are subject to the same license and copyright terms
 * as Fedora Commons (http://fedora-commons.org/).
 */
package org.phaidra.apihooks;

import org.fcrepo.server.errors.ServerException;

public class APIHooksException extends ServerException {

	private static final long serialVersionUID = -5102977466575051401L;

	public APIHooksException(String bundleName, String code, String[] values,
			String[] details, Throwable cause) {
		super(bundleName, code, values, details, cause);
		// TODO Auto-generated constructor stub
	}
	
    public APIHooksException(String message) {
        super(null, message, null, null, null);
    }
    
    public APIHooksException(String message, Throwable cause) {
        super(null, message, null, null, cause);
    }
}