package org.migration.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.migration.model.Migration;
import org.migration.model.Resource;
import org.migration.util.HashCalculator;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SQLMigrationParserTest {
    @Mock
    private HashCalculator hashCalculator;
    @Mock
    private MigrationSource migrationSource;
    @InjectMocks
    private SQLMigrationParser sqlMigrationParser;

    @Test
    void parse_shouldCreateTransaction_whenGivenValidResource() throws IOException {
        Resource resource = new Resource("V1__init.sql", "test");
        when(migrationSource.read(resource)).thenReturn("test content");
        when(hashCalculator.calculate("test content")).thenReturn("123");

        Migration migration = sqlMigrationParser.parse(resource);

        assertEquals("1", migration.version());
        assertEquals("init", migration.description());
        assertEquals(resource, migration.resource());
        assertEquals("123", migration.checkSum());
    }

    @Test
    void parse_shouldThrow_whenFileNameInvalid()  {
        Resource resource = new Resource("V1_init.sql", "test");

        assertThrows(ArrayIndexOutOfBoundsException.class, () -> sqlMigrationParser.parse(resource));
    }

}