package com.filmforest.settings.controller;

import com.filmforest.common.dto.Result;
import com.filmforest.settings.service.SystemSettingService;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SettingsControllerTest {

    @Test
    void exposesOnlyNonSensitiveDatabaseMetadata() throws SQLException {
        SystemSettingService settingService = mock(SystemSettingService.class);
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("MySQL");
        when(metadata.getDatabaseProductVersion()).thenReturn("8.0");
        when(metadata.getDriverName()).thenReturn("MySQL Connector/J");
        when(metadata.getDriverVersion()).thenReturn("9.0");

        Result<Map<String, String>> result = new SettingsController(settingService, dataSource).getDbInfo();

        assertThat(result.getData())
                .containsEntry("connected", "true")
                .containsEntry("productName", "MySQL")
                .containsEntry("productVersion", "8.0")
                .containsEntry("driverName", "MySQL Connector/J")
                .containsEntry("driverVersion", "9.0")
                .doesNotContainKeys("url", "username", "password");
        verify(metadata, never()).getURL();
        verify(metadata, never()).getUserName();
    }

    @Test
    void reportsOnlyConnectionStatusWhenMetadataLookupFails() throws SQLException {
        SystemSettingService settingService = mock(SystemSettingService.class);
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("connection failed"));

        Result<Map<String, String>> result = new SettingsController(settingService, dataSource).getDbInfo();

        assertThat(result.getData()).containsOnly(Map.entry("connected", "false"));
    }
}
