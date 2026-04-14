package org.migration.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.migration.model.Migration;
import org.migration.model.Resource;
import org.migration.repository.MigrationRepository;
import org.migration.util.ConnectionProvider;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MigrationServiceImplTest {
    @Mock
    private MigrationRepository migrationRepository;
    @Mock
    private ConnectionProvider connectionProvider;
    @Mock
    private Connection conn;
    @Mock
    private MigrationSource migrationSource;
    @Mock
    private MigrationParser migrationParser;

    @InjectMocks
    private MigrationServiceImpl migrationService;

    private final Resource resource1 = new Resource("V1__init.sql", "test");
    private final Resource resource2 = new Resource("V2__create_table.sql", "test2");
    private final Resource resource4 = new Resource("V4__insert_records.sql", "test4");
    private final Migration migration1 = new Migration("1", "init", resource1, "123");
    private final Migration migration2 = new Migration("2", "create_table", resource2, "456");
    private final Migration migration4 = new Migration("4", "insert_records", resource4, "789");
    private final Resource rollbackResource4 = new Resource("R4__insert_records.sql", "test4");
    private final Resource rollbackResource2 = new Resource("R2__create_table.sql", "test2");
    private final String sql = "test sql";

    @Test
    void migrate_shouldExecuteAndSaveTransaction_whenNoAppliedTransactions() throws SQLException, IOException {
        setupMigrationContextBeforeMigrate();

        doReturn(List.of(resource1)).when(migrationSource).getMigrationResources();
        when(migrationParser.parse(resource1)).thenReturn(migration1);

        List<Migration> appliedMigrations = new ArrayList<>();
        doReturn(appliedMigrations).when(migrationRepository).getAppliedMigrations(conn);
        when(migrationSource.read(migration1.resource())).thenReturn(sql);

        migrationService.migrate();

        verify(migrationRepository, times(1)).executeMigration(conn, sql);
        verify(migrationRepository, times(1)).saveMigration(conn, migration1);
        assertTrue(appliedMigrations.contains(migration1));
        verify(conn, times(1)).commit();
        verifyLockReleasedAndConnectionClosed();
    }

    @Test
    void migrate_shouldApplyOnlySecondTransaction_whenFirstIsAlreadyApplied() throws SQLException, IOException {
        setupMigrationContextBeforeMigrate();

        List<Resource> resources = new ArrayList<>();
        resources.add(resource1);
        resources.add(resource2);
        doReturn(resources).when(migrationSource).getMigrationResources();

        when(migrationParser.parse(resource1)).thenReturn(migration1);
        when(migrationParser.parse(resource2)).thenReturn(migration2);

        List<Migration> appliedMigrations = new ArrayList<>();
        appliedMigrations.add(migration1);
        doReturn(appliedMigrations).when(migrationRepository).getAppliedMigrations(conn);

        when(migrationSource.read(migration2.resource())).thenReturn(sql);

        migrationService.migrate();

        verify(migrationRepository, times(1)).executeMigration(conn, sql);
        verify(migrationRepository, times(1)).saveMigration(conn, migration2);
        verify(migrationSource, never()).read(migration1.resource());
        verify(migrationRepository, never()).saveMigration(conn, migration1);
        assertTrue(appliedMigrations.contains(migration2));
        verify(conn, times(1)).commit();
        verifyLockReleasedAndConnectionClosed();
    }

    @Test
    void migrate_shouldNotApplyAnything_whenAllTransactionsAlreadyApplied() throws SQLException, IOException {
        setupMigrationContextBeforeMigrate();

        List<Resource> resources = new ArrayList<>();
        resources.add(resource1);
        resources.add(resource2);
        resources.add(resource4);
        doReturn(resources).when(migrationSource).getMigrationResources();

        when(migrationParser.parse(resource1)).thenReturn(migration1);
        when(migrationParser.parse(resource2)).thenReturn(migration2);
        when(migrationParser.parse(resource4)).thenReturn(migration4);

        List<Migration> appliedMigrations = getListOf3Migrations();
        doReturn(appliedMigrations).when(migrationRepository).getAppliedMigrations(conn);

        migrationService.migrate();

        verify(migrationSource, never()).read(migration1.resource());
        verify(migrationRepository, never()).saveMigration(conn, migration1);
        verify(migrationSource, never()).read(migration2.resource());
        verify(migrationRepository, never()).saveMigration(conn, migration2);
        verify(migrationSource, never()).read(migration4.resource());
        verify(migrationRepository, never()).saveMigration(conn, migration4);
        verify(conn, never()).commit();
        verifyLockReleasedAndConnectionClosed();
    }

    @Test
    void migrate_shouldThrow_whenFileWasChanged() throws SQLException, IOException {
        setupMigrationContextBeforeMigrate();

        doReturn(List.of(resource1)).when(migrationSource).getMigrationResources();
        when(migrationParser.parse(resource1)).thenReturn(migration1);

        List<Migration> appliedMigrations = new ArrayList<>();
        Migration changedMigration = new Migration("1", "init", resource1, "1234");
        appliedMigrations.add(changedMigration);
        doReturn(appliedMigrations).when(migrationRepository).getAppliedMigrations(conn);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> migrationService.migrate());
        assertTrue(ex.getMessage().contains("Migration file checksum mismatch. Version: 1"));

        verify(conn, never()).commit();
        verifyLockReleasedAndConnectionClosed();
    }

    @Test
    void migrate_shouldThrowAndExecuteTransactionRollback_whenSqlErrorArise() throws SQLException, IOException {
        setupMigrationContextBeforeMigrate();

        doReturn(List.of(resource1)).when(migrationSource).getMigrationResources();
        when(migrationParser.parse(resource1)).thenReturn(migration1);

        List<Migration> appliedMigrations = new ArrayList<>();
        doReturn(appliedMigrations).when(migrationRepository).getAppliedMigrations(conn);
        when(migrationSource.read(migration1.resource())).thenReturn(sql);
        doThrow(SQLException.class).when(migrationRepository).executeMigration(conn, sql);

        assertThrows(SQLException.class, () -> migrationService.migrate());

        verify(conn, times(1)).rollback();
        verify(migrationRepository, never()).saveMigration(conn, migration1);
        assertFalse(appliedMigrations.contains(migration1));
        verify(conn, never()).commit();
        verifyLockReleasedAndConnectionClosed();
    }

    @Test
    void rollback_shouldRollbackLastTransaction_whenNoVersionPassed() throws SQLException, IOException {
        setupMigrationContextBeforeRollback();

        when(migrationRepository.getAppliedMigrations(conn)).thenReturn(getListOf3Migrations());

        when(migrationSource.getRollbackResource("4")).thenReturn(Optional.of(rollbackResource4));
        when(migrationSource.read(rollbackResource4)).thenReturn(sql);

        migrationService.rollback(null);

        verify(migrationRepository, times(1)).executeMigration(conn, sql);
        verify(migrationRepository, times(1)).deleteMigration(conn, "4");
        verify(migrationRepository, never()).deleteMigration(conn, "2");
        verify(migrationRepository, never()).deleteMigration(conn, "1");
        verify(conn, times(1)).commit();
        verifyLockReleasedAndConnectionClosed();
    }

    @Test
    void rollback_shouldRollbackToSpecifiedVersion_whenVersionPassed() throws SQLException, IOException {
        setupMigrationContextBeforeRollback();

        when(migrationRepository.getAppliedMigrations(conn)).thenReturn(getListOf3Migrations());

        when(migrationSource.getRollbackResource("4")).thenReturn(Optional.of(rollbackResource4));
        when(migrationSource.getRollbackResource("2")).thenReturn(Optional.of(rollbackResource2));
        when(migrationSource.read(rollbackResource4)).thenReturn(sql);
        when(migrationSource.read(rollbackResource2)).thenReturn(sql);

        migrationService.rollback(1);

        verify(migrationRepository, times(2)).executeMigration(conn, sql);
        verify(migrationRepository, times(1)).deleteMigration(conn, "4");
        verify(migrationRepository, times(1)).deleteMigration(conn, "2");
        verify(migrationRepository, never()).deleteMigration(conn, "1");
        verify(conn, times(2)).commit();
        verifyLockReleasedAndConnectionClosed();
    }

    @Test
    void rollback_shouldThrow_whenTargetVersionBiggerThanCurrent() throws SQLException {
        setupMigrationContextBeforeRollback();

        when(migrationRepository.getAppliedMigrations(conn)).thenReturn(getListOf3Migrations());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> migrationService.rollback(5));
        assertTrue(ex.getMessage().contains("Target version is equal to/greater than current version. Nothing to rollback"));

        verifyNoMigrationsWereDeleted();
        verifyLockReleasedAndConnectionClosed();
    }

    @Test
    void rollback_shouldThrow_whenNoAppliedMigrationFoundForGivenVersion() throws SQLException {
        setupMigrationContextBeforeRollback();

        when(migrationRepository.getAppliedMigrations(conn)).thenReturn(getListOf3Migrations());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> migrationService.rollback(3));
        assertTrue(ex.getMessage().contains("Target version mismatch for rollback"));

        verifyNoMigrationsWereDeleted();
        verifyLockReleasedAndConnectionClosed();
    }

    @Test
    void rollback_shouldThrow_whenNoLogTableInDb() throws SQLException {
        when(connectionProvider.getConnection()).thenReturn(conn);
        when(migrationRepository.acquireLock(conn)).thenReturn(true);

        when(migrationRepository.checkLogTableExists(conn)).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> migrationService.rollback(2));
        assertTrue(ex.getMessage().contains("Migration history is empty. Nothing to rollback"));

        verifyNoMigrationsWereDeleted();
        verifyLockReleasedAndConnectionClosed();
    }

    @Test
    void rollback_shouldThrow_whenLogIsEmpty() throws SQLException{
        setupMigrationContextBeforeRollback();

        when(migrationRepository.getAppliedMigrations(conn)).thenReturn(List.of());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> migrationService.rollback(2));
        assertTrue(ex.getMessage().contains("No applied migrations found for rollback"));

        verifyNoMigrationsWereDeleted();
        verifyLockReleasedAndConnectionClosed();
    }

    @Test
    void rollback_shouldThrow_whenNoRollbackFileForGivenVersion() throws SQLException, IOException {
        setupMigrationContextBeforeRollback();

        when(migrationRepository.getAppliedMigrations(conn)).thenReturn(getListOf3Migrations());
        when(migrationSource.getRollbackResource("4")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> migrationService.rollback(null));
        assertTrue(ex.getMessage().contains("No rollback file found for version 4"));

        verifyNoMigrationsWereDeleted();
        verifyLockReleasedAndConnectionClosed();
    }

    @Test
    void rollback_shouldThrowAndExecuteTransactionRollback_whenSqlErrorArise() throws SQLException, IOException {
        setupMigrationContextBeforeRollback();

        when(migrationRepository.getAppliedMigrations(conn)).thenReturn(getListOf3Migrations());

        when(migrationSource.getRollbackResource("4")).thenReturn(Optional.of(rollbackResource4));
        when(migrationSource.read(rollbackResource4)).thenReturn(sql);
        doThrow(SQLException.class).when(migrationRepository).deleteMigration(conn, "4");

        assertThrows(SQLException.class, () -> migrationService.rollback(null));

        verify(conn, times(1)).rollback();
        verify(migrationRepository, times(1)).deleteMigration(conn, "4");
        verify(migrationRepository, never()).deleteMigration(conn, "2");
        verify(migrationRepository, never()).deleteMigration(conn, "1");
        verify(conn, never()).commit();
        verifyLockReleasedAndConnectionClosed();
    }

    private void setupMigrationContextBeforeMigrate() throws SQLException {
        when(connectionProvider.getConnection()).thenReturn(conn);
        when(migrationRepository.acquireLock(conn)).thenReturn(true);
    }

    private void setupMigrationContextBeforeRollback() throws SQLException {
        when(connectionProvider.getConnection()).thenReturn(conn);
        when(migrationRepository.acquireLock(conn)).thenReturn(true);
        when(migrationRepository.checkLogTableExists(conn)).thenReturn(true);
    }

    private void verifyLockReleasedAndConnectionClosed() throws SQLException {
        verify(migrationRepository, times(1)).releaseLock(conn);
        verify(conn, times(1)).close();
    }

    private List<Migration> getListOf3Migrations() {
        List<Migration> migrations = new ArrayList<>();
        migrations.add(migration1);
        migrations.add(migration2);
        migrations.add(migration4);
        return migrations;
    }

    private void verifyNoMigrationsWereDeleted() throws SQLException {
        verify(migrationRepository, never()).executeMigration(conn, sql);
        verify(migrationRepository, never()).deleteMigration(conn, "4");
        verify(migrationRepository, never()).deleteMigration(conn, "2");
        verify(migrationRepository, never()).deleteMigration(conn, "1");
        verify(conn, never()).commit();
    }
}