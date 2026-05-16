package org.bpmnflow.runtime.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLIntegrityConstraintViolationException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("VariableUpsertHelper — upsert branches")
class VariableUpsertHelperTest {

    @Mock
    JdbcTemplate jdbcTemplate;

    @InjectMocks
    VariableUpsertHelper helper;

    // ---------------------------------------------------------------
    // Branch 1: UPDATE hits → done, no INSERT attempted
    // ---------------------------------------------------------------

    @Test
    @DisplayName("returns after UPDATE when row already exists")
    void updateHit_noInsert() {
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any())).thenReturn(1);

        helper.upsert(10L, "key", "STRING", "value");

        verify(jdbcTemplate, times(1)).update(anyString(), any(), any(), any(), any());
        verify(jdbcTemplate, never()).execute(any(ConnectionCallback.class));
    }

    // ---------------------------------------------------------------
    // Branch 2: UPDATE misses → INSERT succeeds → done
    // ---------------------------------------------------------------

    @Test
    @DisplayName("falls through to INSERT when UPDATE finds no row")
    void updateMiss_insertSucceeds() throws Exception {
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any())).thenReturn(0);

        PreparedStatement stmt = mock(PreparedStatement.class);
        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(anyString())).thenReturn(stmt);

        // Simulate execute() calling the callback and returning true (insert succeeded)
        when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenAnswer(inv -> {
            ConnectionCallback<Boolean> cb = inv.getArgument(0);
            return cb.doInConnection(conn);
        });

        helper.upsert(10L, "key", "STRING", "value");

        verify(jdbcTemplate, times(1)).update(anyString(), any(), any(), any(), any());
        verify(jdbcTemplate, times(1)).execute(any(ConnectionCallback.class));
        verify(stmt).executeUpdate();
        // No second UPDATE needed since INSERT succeeded
        verify(jdbcTemplate, times(1)).update(anyString(), any(), any(), any(), any());
    }

    // ---------------------------------------------------------------
    // Branch 3: UPDATE misses → INSERT collides → fallback UPDATE
    // ---------------------------------------------------------------

    @Test
    @DisplayName("falls back to second UPDATE when INSERT collides on duplicate key")
    void updateMiss_insertCollides_fallbackUpdate() throws Exception {
        // First call = UPDATE miss, second call = fallback UPDATE
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any()))
                .thenReturn(0)   // first UPDATE: row not found
                .thenReturn(1);  // second UPDATE: succeeds after collision

        PreparedStatement stmt = mock(PreparedStatement.class);
        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        // INSERT throws duplicate key
        doThrow(new SQLIntegrityConstraintViolationException("ORA-00001"))
                .when(stmt).executeUpdate();

        when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenAnswer(inv -> {
            ConnectionCallback<Boolean> cb = inv.getArgument(0);
            return cb.doInConnection(conn);
        });

        helper.upsert(10L, "key", "STRING", "value");

        // UPDATE called twice: initial miss + fallback after INSERT collision
        verify(jdbcTemplate, times(2)).update(anyString(), any(), any(), any(), any());
        verify(jdbcTemplate, times(1)).execute(any(ConnectionCallback.class));
    }
}