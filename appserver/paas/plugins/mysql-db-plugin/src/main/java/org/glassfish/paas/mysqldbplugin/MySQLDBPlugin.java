/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package org.glassfish.paas.mysqldbplugin;

import org.glassfish.paas.dbspecommon.DatabaseSPEBase;
import org.glassfish.virtualization.spi.VirtualMachine;
import org.jvnet.hk2.annotations.Scoped;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.component.PerLookup;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Plugin for MySQL Database Service
 *
 * @author Shalini M
 */
@Scoped(PerLookup.class)
@Service
public class MySQLDBPlugin  extends DatabaseSPEBase {

    private String mysqlDatabaseName = "foo";
    private static final String MYSQL_USERNAME = "root";
    private static final String MYSQL_PASSWORD = "mysql";
    // TODO :: grab the actual port.
    private static final String MYSQL_PORT = "3306";
    private static Logger logger = Logger.getLogger(MySQLDBPlugin.class.getName());

    public String getDefaultServiceName() {
        return "default-mysql-db-service";
    }

    @Override
    public void executeInitSql(Properties dbProps, String sqlFile) {
        try {
            logger.log(Level.INFO, "Executing init-sql : " + sqlFile);
            String url = "jdbc:mysql://" + dbProps.getProperty(HOST) +
                    ":" + dbProps.getProperty(PORT) + "/" +
                    dbProps.getProperty(DATABASENAME) +
                    "?createDatabaseIfNotExist=true";
            executeAntTask(dbProps, "com.mysql.jdbc.Driver", url, sqlFile, true);
            logger.log(Level.INFO, "Completed executing init-sql : " + sqlFile);
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Init SQL execution [ " + sqlFile + " ] failed with exception : " + ex);
        }
    }

    @Override
    public void createDatabase(Properties dbProps) {
        try {
            logger.log(Level.INFO, "Creating Database: " + dbProps.getProperty(DATABASENAME));
            String url = "jdbc:mysql://" + dbProps.getProperty(HOST) +
                    ":" + dbProps.getProperty(PORT) + "/" +
                    dbProps.getProperty(DATABASENAME) +
                    "?createDatabaseIfNotExist=true";
            String sql = "SELECT '1'";
            executeAntTask(dbProps, "com.mysql.jdbc.Driver", url, sql, false);
            logger.log(Level.INFO, "Created database : ", dbProps.getProperty(DATABASENAME));
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Database creation failed with exception : " + ex);
        }
    }

    @Override
    protected Properties getServiceProperties(String ipAddress, String databaseName) {
        Properties defaultConnPoolProperties = getDefaultConnectionProperties();
        Properties serviceProperties = new Properties();
        serviceProperties.putAll(defaultConnPoolProperties);
        serviceProperties.put(HOST, ipAddress);
        serviceProperties.put(URL, "jdbc\\:mysql\\://" + ipAddress + "\\:" +
                MYSQL_PORT + "/" + databaseName); // TODO :: grab the actual port.
        serviceProperties.put(PORT, MYSQL_PORT);
        if (databaseName != null && databaseName.trim().length() > 0) {
            serviceProperties.put(DATABASENAME, databaseName);
        }
        return serviceProperties;
    }

    protected void setDatabaseName(String databaseName) {
        mysqlDatabaseName = databaseName;
    }

    protected String getDatabaseName() {
        return mysqlDatabaseName;
    }

    public Properties getDefaultConnectionProperties() {
        Properties properties = new Properties();
        properties.put(USER, MYSQL_USERNAME);
        properties.put(PASSWORD, MYSQL_PASSWORD);
        properties.put(DATABASENAME, mysqlDatabaseName);
        properties.put(RESOURCE_TYPE, "javax.sql.XADataSource");
        properties.put(CLASSNAME, "com.mysql.jdbc.jdbc2.optional.MysqlXADataSource");
        properties.put("createDatabaseIfNotExist", "true");
        return properties;
    }

    public void startDatabase(VirtualMachine virtualMachine) {
        //no-op
    }

    public void stopDatabase(VirtualMachine virtualMachine) {
        //no-op
    }
}
