/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.oodt.cas.metadata.extractors;

// JDK imports
import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;


// JAVAX imports
import javax.sql.DataSource;


// OODT imports
import org.apache.oodt.cas.metadata.AbstractMetExtractor;
import org.apache.oodt.cas.metadata.Metadata;
import org.apache.oodt.cas.metadata.exceptions.MetExtractionException;
import org.apache.oodt.commons.database.DatabaseConnectionBuilder;


// Google imports
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;

/**
 * MetExtractor which uses input file's name as key for lookup into a sql database to get metadata.
 *
 * @author bfoster@apache.org (Brian Foster)
 */
public class DataSourceMetExtractor extends AbstractMetExtractor {

  public DataSourceMetExtractor() {
    super(new DataSourceMetExtractorConfigReader());
  }

  @Override
  protected Metadata extrMetadata(File file) throws MetExtractionException {
    String key = getKey(file);
    DataSourceMetExtractorConfig dsConfig = (DataSourceMetExtractorConfig) config;
    DataSource dataSource = DatabaseConnectionBuilder.buildDataSource(dsConfig.getUserName(),
        dsConfig.getPassword(), dsConfig.getDriver(), dsConfig.getDatabaseUrl());

    return getMetadata(dataSource, dsConfig.getQuery(), key);
  }

  @VisibleForTesting
  protected String getKey(File file) {
    return Splitter.on(".").split(file.getName()).iterator().next();
  }

  @VisibleForTesting
  protected Metadata getMetadata(DataSource dataSource, String query, String key)
      throws MetExtractionException {
    String sqlQuery = String.format(query, key);

    Connection conn = null;
    Statement statement = null;
    ResultSet rs = null;

    try {
      conn = dataSource.getConnection();
      statement = conn.createStatement();
      rs = statement.executeQuery(sqlQuery);

      return getMetadata(rs);
    } catch (SQLException e) {
      throw new MetExtractionException(
          String.format("Failed to get metadaata for key '%s'", key), e);
    } finally {
      try { conn.close(); } catch (Exception e) { /* ignore */ }
      try { statement.close(); } catch (Exception e) { /* ignore */ }
      try { rs.close(); } catch (Exception e) { /* ignore */ }
    }
  }

  private Metadata getMetadata(ResultSet rs) throws SQLException {
    Metadata metadata = new Metadata();
    for (int i = 0; i < rs.getMetaData().getColumnCount(); i++) {
      String metKey = rs.getMetaData().getColumnName(i);
      String metVal = rs.getString(i);
      metadata.addMetadata(metKey, metVal);
    }
    return metadata;
  }
}
