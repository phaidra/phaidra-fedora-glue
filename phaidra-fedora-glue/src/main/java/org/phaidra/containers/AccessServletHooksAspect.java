/* The contents of this file are subject to the same license and copyright terms
 * as Fedora Commons (http://fedora-commons.org/).
 */
package org.phaidra.containers;

import java.util.Date;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.fcrepo.server.Context;
import org.fcrepo.server.storage.types.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Aspect
public class AccessServletHooksAspect {

    private static final Logger logger = LoggerFactory
            .getLogger(AccessServletHooksAspect.class);
    
    private static ThreadLocal<String> dateStringStorage = new ThreadLocal<String>();
 
    private static void logCall(String pid, int stackIndex) {
        
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        logger.debug("[pid = "+ pid +"]" + stack[stackIndex].getMethodName() + " called from "
                + stack[stackIndex+1].getClassName() + "." + stack[stackIndex+1].getMethodName()
                + "(" + stack[stackIndex+1].getLineNumber() + ")");
    }

    @Pointcut("execution(* org.fcrepo.utilities.DateUtility.parseDateStrict(..)) && args(dateString)")
    public void parseDateStrict(String dateString) {
    }    

    @Around("parseDateStrict(dateString)")
    public Object parseDateStrictHook(String dateString, ProceedingJoinPoint thisJoinPoint)
            throws Throwable {

            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            logger.trace(stack[2].getClassName() + "." + stack[2].getMethodName() + " called from "+ stack[3].getClassName() + "." + stack[3].getMethodName() + "(" + stack[3].getLineNumber() + ")");
    
            if (dateString.indexOf(":") == -1) {
                dateStringStorage.set(dateString);
                return null;
            }else{                
                return thisJoinPoint.proceed();
            }
    }
    
    @Pointcut("execution(* org.fcrepo.server.access.FedoraAccessServlet.getDissemination(..)) "
            + "&& args(context, PID, sDefPID, methodName, userParms, asOfDateTime, response, request)")
    public void getDissemination(Context context,
                              String PID,
                              String sDefPID,
                              String methodName,
                              Property[] userParms,
                              Date asOfDateTime,
                              HttpServletResponse response,
                              HttpServletRequest request) {
    }
    
    /**
     * Handle requests of type:
     * http://host:port/fedora/get/pid/bDefPid/methodName/param0?parm=value[&parm=value]
     * i.e. treat a value after the methodName as first parameter. 
     * This is needed to support Phaidra-Containers.
     */
    @Around("getDissemination(context, PID, sDefPID, methodName, userParms, asOfDateTime, response, request)")
    public void getDisseminationHook(Context context,
                                     String PID,
                                     String sDefPID,
                                     String methodName,
                                     Property[] userParms,
                                     Date asOfDateTime,
                                     HttpServletResponse response,
                                     HttpServletRequest request,                                    
                                    ProceedingJoinPoint thisJoinPoint)
            throws Throwable {

        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        logger.debug("[pid = "+ PID +"] " + stack[2].getClassName() + "." + stack[2].getMethodName() + " called from "+ stack[3].getClassName() + "." + stack[3].getMethodName() + "(" + stack[3].getLineNumber() + ")");
        
        String dateString = dateStringStorage.get();
              
        Property userParm = new Property();        
        userParm.name = "param0";
        userParm.value = dateString;
        
        Property[] newUserParms = new Property[userParms.length+1];
        if(userParms.length > 0){
            System.arraycopy(userParm, 0, newUserParms, 0, userParms.length);
        }
        newUserParms[userParms.length] = userParm;
        
        thisJoinPoint.proceed(new Object[] {context, PID, sDefPID, methodName, newUserParms, asOfDateTime, response, request});
        
    }
    
   
    
}
