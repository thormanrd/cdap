/*
 * Copyright © 2015 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.cdap.template.etl.batch.sink;

import co.cask.cdap.api.annotation.Description;
import co.cask.cdap.api.annotation.Name;
import co.cask.cdap.api.annotation.Plugin;
import co.cask.cdap.api.data.format.StructuredRecord;
import co.cask.cdap.api.dataset.lib.KeyValue;
import co.cask.cdap.api.templates.plugins.PluginConfig;
import co.cask.cdap.api.templates.plugins.PluginProperties;
import co.cask.cdap.template.etl.api.Emitter;
import co.cask.cdap.template.etl.api.PipelineConfigurer;
import co.cask.cdap.template.etl.api.batch.BatchSink;
import co.cask.cdap.template.etl.api.batch.BatchSinkContext;
import co.cask.cdap.template.etl.common.DBConfig;
import co.cask.cdap.template.etl.common.DBRecord;
import co.cask.cdap.template.etl.common.ETLDBOutputFormat;
import co.cask.cdap.template.etl.common.JDBCDriverShim;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapred.lib.db.DBConfiguration;
import org.apache.hadoop.mapreduce.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Sink that can be configured to export data to a database table
 */
@Plugin(type = "sink")
@Name("Database")
@Description("Batch sink for a database.")
public class DBSink extends BatchSink<StructuredRecord, DBRecord, NullWritable> {
  private static final Logger LOG = LoggerFactory.getLogger(DBSink.class);

  private static final String COLUMNS_DESCRIPTION = "Comma-separated list of columns to export to in the specified " +
    "table.";

  private final DBSinkConfig dbSinkConfig;
  private ResultSetMetaData resultSetMetadata;
  private Configuration conf;

  public DBSink(DBSinkConfig dbSinkConfig) {
    this.dbSinkConfig = dbSinkConfig;
  }

  @Override
  public void configurePipeline(PipelineConfigurer pipelineConfigurer) {
    Preconditions.checkArgument(!(dbSinkConfig.user == null && dbSinkConfig.password != null),
                                "dbUser is null. Please provide both user name and password if database requires " +
                                  "authentication. If not, please remove dbPassword and retry.");
    Preconditions.checkArgument(!(dbSinkConfig.user != null && dbSinkConfig.password == null),
                                "dbPassword is null. Please provide both user name and password if database requires" +
                                  "authentication. If not, please remove dbUser and retry.");
    String jdbcPluginId = String.format("%s.%s.%s", "sink", dbSinkConfig.jdbcPluginType, dbSinkConfig.jdbcPluginName);
    Class<Object> jdbcDriverClass = pipelineConfigurer.usePluginClass(dbSinkConfig.jdbcPluginType,
                                                                      dbSinkConfig.jdbcPluginName,
                                                                      jdbcPluginId,
                                                                      PluginProperties.builder().build());
    Preconditions.checkArgument(jdbcDriverClass != null, "JDBC Driver class must be found.");
  }

  @Override
  public void prepareRun(BatchSinkContext context) {
    LOG.debug("tableName = {}; driverClass = {}; connectionString = {}; importQuery = {}; columns = {}",
              dbSinkConfig.tableName, dbSinkConfig.driverClass, dbSinkConfig.connectionString, dbSinkConfig.columns);

    Job job = context.getHadoopJob();
    conf = job.getConfiguration();
    String jdbcPluginId = String.format("%s.%s.%s", "sink", dbSinkConfig.jdbcPluginType, dbSinkConfig.jdbcPluginName);
    // Load the plugin class to make sure it is available.
    context.loadPluginClass(jdbcPluginId);
    if (dbSinkConfig.user == null && dbSinkConfig.password == null) {
      DBConfiguration.configureDB(conf, dbSinkConfig.driverClass, dbSinkConfig.connectionString);
    } else {
      DBConfiguration.configureDB(conf, dbSinkConfig.driverClass, dbSinkConfig.connectionString,
                                  dbSinkConfig.user, dbSinkConfig.password);
    }
    List<String> fields = Lists.newArrayList(Splitter.on(",").omitEmptyStrings().split(dbSinkConfig.columns));
    try {
      ETLDBOutputFormat.setOutput(job, dbSinkConfig.tableName, fields.toArray(new String[fields.size()]));
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }
    job.setOutputFormatClass(ETLDBOutputFormat.class);
  }

  @Override
  public void initialize(BatchSinkContext context) throws Exception {
    super.initialize(context);
    setResultSetMetadata();
  }

  @Override
  public void transform(StructuredRecord input, Emitter<KeyValue<DBRecord, NullWritable>> emitter) throws Exception {
    emitter.emit(new KeyValue<DBRecord, NullWritable>(new DBRecord(input, resultSetMetadata), null));
  }

  private void setResultSetMetadata() throws Exception {
    ensureJDBCDriverIsAvailable();
    Connection connection;
    if (dbSinkConfig.user == null) {
      connection = DriverManager.getConnection(dbSinkConfig.connectionString);
    } else {
      connection = DriverManager.getConnection(dbSinkConfig.connectionString, dbSinkConfig.user, dbSinkConfig.password);
    }
    try {
      // Using LIMIT in the following query even though its not SQL standard since DBInputFormat already depends on it
      try (
        Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery(String.format("SELECT %s from %s LIMIT 1",
                                                            dbSinkConfig.columns, dbSinkConfig.tableName))
      ) {
        resultSetMetadata = rs.getMetaData();
      }
    } finally {
      connection.close();
    }
  }

  /**
   * Ensures that the JDBC driver is available for {@link DriverManager}
   *
   * @throws Exception if the driver is not available
   */
  private void ensureJDBCDriverIsAvailable() throws Exception {
    try {
      DriverManager.getDriver(dbSinkConfig.connectionString);
    } catch (SQLException e) {
      // Driver not found. We will try to register it with the DriverManager.
      LOG.debug("Driver {} not found. Registering JDBC driver via shim {} ",
                dbSinkConfig.driverClass, JDBCDriverShim.class.getName());
      ClassLoader classLoader = conf.getClassLoader();
      Class<?> driverClass = classLoader.loadClass(dbSinkConfig.driverClass);
      DriverManager.registerDriver(new JDBCDriverShim((Driver) driverClass.newInstance()));
    }
  }

  /**
   * {@link PluginConfig} for {@link DBSink}
   */
  public static class DBSinkConfig extends DBConfig {
    @Description(COLUMNS_DESCRIPTION)
    String columns;
  }
}
