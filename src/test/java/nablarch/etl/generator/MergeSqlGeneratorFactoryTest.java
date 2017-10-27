package nablarch.etl.generator;

import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertThat;

import java.sql.Connection;
import java.sql.DatabaseMetaData;

import nablarch.core.db.connection.TransactionManagerConnection;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import mockit.Mocked;
import mockit.Expectations;

/**
 * {@link MergeSqlGeneratorFactory}のテスト
 */
public class MergeSqlGeneratorFactoryTest {

    @Mocked
    private TransactionManagerConnection mockConnection;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void testOracle() throws Exception {
        new Expectations() {{
            final Connection connection = mockConnection.getConnection();
            final DatabaseMetaData metaData = connection.getMetaData();
            metaData.getURL();
            result = "jdbc:oracle:thin:@localhost:1521/xe";
        }};

        assertThat(MergeSqlGeneratorFactory.create(mockConnection),
                instanceOf(StandardMergeSqlGenerator.class));
    }

    @Test
    public void testH2() throws Exception {
        new Expectations() {{
            final Connection connection = mockConnection.getConnection();
            final DatabaseMetaData metaData = connection.getMetaData();
            metaData.getURL();
            result = "jdbc:h2:mem:test-db";
        }};

        assertThat(MergeSqlGeneratorFactory.create(mockConnection),
                instanceOf(H2MergeSqlGenerator.class));
    }

    @Test
    public void testSqlServer() throws Exception {
        new Expectations() {{
            final Connection connection = mockConnection.getConnection();
            final DatabaseMetaData metaData = connection.getMetaData();
            metaData.getURL();
            result = "jdbc:sqlserver://localhost:1433";
        }};

        assertThat(MergeSqlGeneratorFactory.create(mockConnection),
                instanceOf(SqlServerMergeSqlGenerator.class));
    }

    @Test
    public void testPostgres() throws Exception {
        new Expectations() {{
            final Connection connection = mockConnection.getConnection();
            final DatabaseMetaData metaData = connection.getMetaData();
            metaData.getURL();
            result = "jdbc:postgresql:test-db";
        }};

        assertThat(MergeSqlGeneratorFactory.create(mockConnection),
                instanceOf(PostgresMergeSqlGenerator.class));
    }
    
    @Test
    public void testDb2() throws Exception {
        new Expectations() {{
            final Connection connection = mockConnection.getConnection();
            final DatabaseMetaData metaData = connection.getMetaData();
            metaData.getURL();
            result = "jdbc:db2:localhost:1234";
        }};

        assertThat(MergeSqlGeneratorFactory.create(mockConnection),
                instanceOf(StandardMergeSqlGenerator.class));
    }

    @Test
    public void urlIsNull_shouldThrowException() throws Exception {
        new Expectations() {{
            final Connection connection = mockConnection.getConnection();
            final DatabaseMetaData metaData = connection.getMetaData();
            metaData.getURL();
            result = null;
        }};

        expectedException.expect(IllegalStateException.class);
        expectedException.expectMessage("database that can not use merge. database url: ");
        MergeSqlGeneratorFactory.create(mockConnection);
    }
}