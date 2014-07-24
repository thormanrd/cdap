/*
 * Copyright 2012-2014 Continuuity, Inc.
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

package com.continuuity.explore.jdbc;

import com.continuuity.explore.client.ExploreClient;

import com.google.common.collect.Maps;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.Date;
import java.sql.NClob;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Map;

/**
 * Explore JDBC prepared statement.
 */
public class ExplorePreparedStatement extends ExploreStatement implements PreparedStatement {

  private final String sql;

  // Save the SQL parameters {paramLoc:paramValue}
  private final Map<Integer, String> parameters = Maps.newHashMap();

  ExplorePreparedStatement(Connection connection, ExploreClient exploreClient, String sql) {
    super(connection, exploreClient);

    // Although a PreparedStatement is meant to precompile sql statement, our Explore client
    // interface does not allow it.
    this.sql = sql;
  }

  @Override
  public boolean execute() throws SQLException {
    return super.execute(updateSql());
  }

  @Override
  public ResultSet executeQuery() throws SQLException {
    return super.executeQuery(updateSql());
  }

  @Override
  public void setBoolean(int parameterIndex, boolean x) throws SQLException {
    this.parameters.put(parameterIndex, String.valueOf(x));
  }

  @Override
  public void setByte(int parameterIndex, byte x) throws SQLException {
    this.parameters.put(parameterIndex, String.valueOf(x));
  }

  @Override
  public void setShort(int parameterIndex, short x) throws SQLException {
    this.parameters.put(parameterIndex, String.valueOf(x));
  }

  @Override
  public void setInt(int parameterIndex, int x) throws SQLException {
    this.parameters.put(parameterIndex, String.valueOf(x));
  }

  @Override
  public void setLong(int parameterIndex, long x) throws SQLException {
    this.parameters.put(parameterIndex, String.valueOf(x));
  }

  @Override
  public void setFloat(int parameterIndex, float x) throws SQLException {
    this.parameters.put(parameterIndex, String.valueOf(x));
  }

  @Override
  public void setDouble(int parameterIndex, double x) throws SQLException {
    this.parameters.put(parameterIndex, String.valueOf(x));
  }

  @Override
  public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {
    this.parameters.put(parameterIndex, x.toPlainString());
  }

  @Override
  public void setString(int parameterIndex, String x) throws SQLException {
    this.parameters.put(parameterIndex, String.format("'%s'", x.replace("'", "\\'")));
  }

  @Override
  public void setNull(int i, int i2) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setBytes(int i, byte[] bytes) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setDate(int parameterIndex, Date date) throws SQLException {
    this.parameters.put(parameterIndex, date.toString());
  }

  @Override
  public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {
    this.parameters.put(parameterIndex, x.toString());
  }

  @Override
  public void clearParameters() throws SQLException {
    parameters.clear();
  }

  @Override
  public void setTime(int i, Time time) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public int executeUpdate() throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setAsciiStream(int i, InputStream inputStream, int i2) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setUnicodeStream(int i, InputStream inputStream, int i2) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setBinaryStream(int i, InputStream inputStream, int i2) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setObject(int i, Object o, int i2) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setObject(int i, Object o) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void addBatch() throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setCharacterStream(int i, Reader reader, int i2) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setRef(int i, Ref ref) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setBlob(int i, Blob blob) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setClob(int i, Clob clob) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setArray(int i, Array array) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public ResultSetMetaData getMetaData() throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setDate(int i, Date date, Calendar calendar) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setTime(int i, Time time, Calendar calendar) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setTimestamp(int i, Timestamp timestamp, Calendar calendar) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setNull(int i, int i2, String s) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setURL(int i, URL url) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public ParameterMetaData getParameterMetaData() throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setRowId(int i, RowId rowId) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setNString(int i, String s) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setNCharacterStream(int i, Reader reader, long l) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setNClob(int i, NClob nClob) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setClob(int i, Reader reader, long l) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setBlob(int i, InputStream inputStream, long l) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setNClob(int i, Reader reader, long l) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setSQLXML(int i, SQLXML sqlxml) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setObject(int i, Object o, int i2, int i3) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setAsciiStream(int i, InputStream inputStream, long l) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setBinaryStream(int i, InputStream inputStream, long l) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setCharacterStream(int i, Reader reader, long l) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setAsciiStream(int i, InputStream inputStream) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setBinaryStream(int i, InputStream inputStream) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setCharacterStream(int i, Reader reader) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setNCharacterStream(int i, Reader reader) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setClob(int i, Reader reader) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setBlob(int i, InputStream inputStream) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setNClob(int i, Reader reader) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  /**
   * Update the SQL string with parameters set by setXXX methods of {@link PreparedStatement}.
   * Package visibility is for testing.
   */
  String updateSql() throws SQLException {
    StringBuffer newSql = new StringBuffer(sql);

    int paramLoc = 1;
    while (getCharIndexFromSqlByParamLocation(sql, '?', paramLoc) > 0) {
      String tmp = parameters.get(paramLoc);
      if (tmp == null) {
        throw new SQLException("Parameter in position " + paramLoc + " has not been set.");
      }
      int tt = getCharIndexFromSqlByParamLocation(newSql.toString(), '?', 1);
      newSql.deleteCharAt(tt);
      newSql.insert(tt, tmp);
      paramLoc++;
    }

    return newSql.toString();
  }

  /**
   * Get the index of the paramLoc-th given cchar from the SQL string.
   * -1 will be return, if nothing found
   */
  private int getCharIndexFromSqlByParamLocation(String sql, char cchar, int paramLoc) {
    int signalCount = 0;
    int charIndex = -1;
    int num = 0;
    for (int i = 0; i < sql.length(); i++) {
      char c = sql.charAt(i);
      if (c == '\'' || c == '\"') {
        // record the count of char "'" and char "\""
        signalCount++;
      } else if (c == cchar && signalCount % 2 == 0) {
        num++;
        if (num == paramLoc) {
          charIndex = i;
          break;
        }
      }
    }
    return charIndex;
  }
}
