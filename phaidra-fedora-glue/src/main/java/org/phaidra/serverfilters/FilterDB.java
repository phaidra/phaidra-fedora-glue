/* The contents of this file are subject to the same license and copyright terms
 * as Fedora Commons (http://fedora-commons.org/).
 */

package org.phaidra.serverfilters;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Hashtable;
import java.sql.*;
import javax.sql.*;
import javax.servlet.FilterConfig;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.fcrepo.server.security.servletfilters.BaseCaching;
import javax.naming.Context;
import javax.naming.InitialContext;
import org.fcrepo.server.security.servletfilters.CacheElement;

/**
 * @author Thomas Wana (thomas.wana@univie.ac.at)
 */
public class FilterDB extends BaseCaching {
    
    protected static Log log = LogFactory.getLog(FilterDB.class);
    
    //    <init-param>
    //    <param-name>resource</param-name>
    //    <param-value>jdbc/webdb</param-value>
    // </init-param>
    // <init-param>
    //    <param-name>query</param-name>
    //    <param-value>SELECT d.inum,
    //     NVL2(i.name1, i.name1 || ' ', '') || NVL2(i.name2, i.name2 || ' ', '') || NVL(i.name3, '') AS institut,
    //     f.code,
    //     f.name
    //FROM pers.user_main u,
    //     pers.dienstverhaeltnis d,
    //     pers.instic i,
    //     pers.fakultaeten f
    //WHERE u.username = RPAD(?, 8)
    //AND u.pkey = d.pkey
    //AND d.inum = i.inum
    //AND i.fakcode = f.code
    //AND d.eintritt <= SYSDATE
    //AND d.austritt >= SYSDATE</param-value>
    // </init-param>
    // <init-param>
    //    <param-name>bindparams</param-name>
    //    <param-value>username,username</param-name>
    // </init-param>
    // <init-param>
    //    <param-name>attributes</param-name>
    //    <param-value>inum,institute,fakcode,faculty</param-value>
    // </init-param> 
    
    public static final String RESOURCE_KEY = "resource";
    
    public static final String QUERY_KEY = "query";
    
    public static final String ATTRIBUTES_KEY = "attributes";
    
    public static final String BINDPARAMS_KEY = "bindparams";
    
    private String RESOURCE = null;
    
    private String QUERY = null;
    
    private String[] ATTRIBUTES = null;
    
    private String[] BINDPARAMS = null;
    
    public void init(FilterConfig filterConfig) {
        super.init(filterConfig);
        inited = false;
        if (!initErrors) {
            
        } else {
            log.error("FilterDB not initialized; see previous error");
        }
        inited = true;
    }
    
    public void destroy() {
        super.destroy();
    }
    
    protected void initThisSubclass(String key, String value) {
        if (RESOURCE_KEY.equals(key)) {
            RESOURCE = value;
        } else if (QUERY_KEY.equals(key)) {
            QUERY = value;
        } else if (ATTRIBUTES_KEY.equals(key)) {
            if (value.indexOf(",") < 0) {
                if ("".equals(value)) {
                    ATTRIBUTES = null;
                } else {
                    ATTRIBUTES = new String[1];
                    ATTRIBUTES[0] = value;
                }
            } else {
                ATTRIBUTES = value.split(",");
            }
        } else if (BINDPARAMS_KEY.equals(key)) {
            if (value.indexOf(",") < 0) {
                if ("".equals(value)) {
                    BINDPARAMS = null;
                } else {
                    BINDPARAMS = new String[1];
                    BINDPARAMS[0] = value;
                }
            } else {
                BINDPARAMS = value.split(",");
            }
        } else {
            super.initThisSubclass(key, value);
        }
        
        // TODO check missing attributes
        
        log.info("FilterDB: initialized.");
        log.debug("  RESOURCE: " + RESOURCE);
        log.debug("  QUERY: " + QUERY);
    }
    
    // implemented from BaseCaching
    public void populateCacheElement(CacheElement cacheElement, String password) {
        Boolean authenticated = null;
        Map map = new Hashtable();
        
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet res = null;
        try {
            // get DB connection from pool
            Context initContext = new InitialContext();
            Context envContext = (Context) initContext.lookup("java:/comp/env");
            DataSource ds = (DataSource) envContext.lookup(RESOURCE);
            conn = ds.getConnection();
            
            // Prepare statement
            pstmt = conn.prepareStatement(QUERY);
            String userId = cacheElement.getUserid();
            for (int i = 0, j = 1; i < BINDPARAMS.length; i++) {
                if (BINDPARAMS[i].compareTo("username") == 0) {
                    pstmt.setString(j, userId);
                    j++;
                }
            }
            
            // Execute statement
            res = pstmt.executeQuery();
            
            // fetch results
            while (res.next()) {
                for (int i = 0; i < ATTRIBUTES.length; i++) {
                    String key = ATTRIBUTES[i];
                    String value = res.getString(i + 1);
                    Set values;
                    if (map.containsKey(key)) {
                        values = (Set) map.get(key);
                    } else {
                        values = new HashSet();
                        map.put(key, values);
                    }
                    
                    if (!values.contains(value)) {
                        values.add(value);
                        log.debug("FilterDB: added value |" + value
                                + "| for key |" + key + "|");
                    }
                }
            }
            
            cacheElement.populate(authenticated, null, map, null);
        } catch (Exception e) {
            log.error("FilterDB: error while querying database: "
                    + e.toString());
            e.printStackTrace();
        }
        
        try {
            if (res != null) res.close();
            if (pstmt != null) pstmt.close();
            if (conn != null) conn.close();
        } catch (Exception e) {
            // don't care
        }
    }
}
