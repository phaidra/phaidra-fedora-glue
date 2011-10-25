package org.phaidra.apihooks;

import java.util.regex.Pattern;

import org.fcrepo.common.Constants;
import org.fcrepo.common.PID;
import org.fcrepo.server.errors.GeneralException;
import org.fcrepo.server.errors.ServerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tools from Fedora
 */
public class FedoraHelper {
    
    private static final Logger logger = LoggerFactory
            .getLogger(FedoraHelper.class);
    
    private static Pattern pidRegex =
            Pattern.compile("^([A-Za-z0-9]|-|\\.)+:(([A-Za-z0-9])|-|\\.|~|_|(%[0-9A-F]{2}))+$");

    public static String getSubjectAsUri(String subject) {
        // if we weren't given a pid, assume it's a URI
        if (!isPid(subject)) {
            return subject;
        }
        // otherwise return URI from the pid
        logger.warn("Relationships API methods:  the 'pid' (" + subject +
                    ") form of a relationship's subject is deprecated.  Please specify the subject using the " +
                    Constants.FEDORA.uri + " uri scheme.");
        return PID.toURI(subject);
    }

    public static String getSubjectPID(String subject) throws ServerException {
        if (isPid(subject)) {
            return subject;
        }
        // check for info:uri scheme
        if (subject.startsWith(Constants.FEDORA.uri)) {
            // pid is everything after the first / to the 2nd / or to the end of the string
            return subject.split("/", 3)[1];

        } else {
            throw new GeneralException("Subject URI must be in the " + Constants.FEDORA.uri + " scheme.");
        }

    }

    public static boolean isPid(String subject) {
        return pidRegex.matcher(subject).matches();
    }
}

    