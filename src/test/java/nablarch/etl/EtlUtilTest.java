package nablarch.etl;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import nablarch.common.dao.DatabaseUtil;
import nablarch.core.db.connection.ConnectionFactory;
import nablarch.core.db.connection.DbConnectionContext;
import nablarch.core.db.connection.TransactionManagerConnection;
import nablarch.core.transaction.TransactionContext;
import nablarch.test.support.SystemRepositoryResource;
import nablarch.test.support.db.helper.DatabaseTestRunner;
import nablarch.test.support.db.helper.VariousDbTestHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

import mockit.Expectations;

/**
 * {@link EtlUtil}のテスト。
 */
@RunWith(DatabaseTestRunner.class)
public class EtlUtilTest {

    @ClassRule
    public static SystemRepositoryResource resource = new SystemRepositoryResource("db-default.xml");

    @BeforeClass
    public static void setUpClass() throws Exception {
        VariousDbTestHelper.createTable(EtlUtilEntity.class);
    }

    @Before
    public void setUp() throws Exception {
        final ConnectionFactory connectionFactory = resource.getComponentByType(ConnectionFactory.class);
        final TransactionManagerConnection connection = connectionFactory.getConnection(TransactionContext.DEFAULT_TRANSACTION_CONTEXT_KEY);
        DbConnectionContext.setConnection(connection);
    }

    @After
    public void tearDown() throws Exception {
        final TransactionManagerConnection connection = DbConnectionContext.getTransactionManagerConnection();
        DbConnectionContext.removeConnection();
        connection.terminate();
    }

    /**
     * {@link EtlUtil#getAllColumns(String)}のテスト。
     */
    @Test
    public void getAllColumns() throws Exception {
        final List<String> columns = EtlUtil.getAllColumns("etl_util");

        assertThat("カラム数は3", columns.size(), is(3));

        final Connection connection = DbConnectionContext.getTransactionManagerConnection()
                .getConnection();
        final ResultSet expected = connection.getMetaData()
                .getColumns(null, null, DatabaseUtil.convertIdentifiers("etl_util"), null);

        int index = 0;
        while (expected.next()) {
            assertThat(columns.get(index), is(expected.getString("COLUMN_NAME")));
            index++;
        }
        expected.close();
    }

    /**
     * {@link EtlUtil#getAllColumns(String)}でデータベース関連の例外が発生するケース
     */
    @Test(expected = RuntimeException.class)
    public void getAllColumns_Failed() throws Exception {
        final TransactionManagerConnection connection = DbConnectionContext.getTransactionManagerConnection();
        new Expectations(connection) {{
            connection.getConnection()
                    .getMetaData().getColumns(null, null, anyString, null);
            result = new SQLException("sql error");
        }};

        EtlUtil.getAllColumns("etl_util");
    }

    /**
     * {@link EtlUtil#verifyRequired(String, String, String, Object)}のテスト。
     */
    @Test
    public void verifyRequired() throws Exception {

        // 正常
        EtlUtil.verifyRequired("job", "step", "key", "ok");

        // 異常
        try {
            EtlUtil.verifyRequired("job", "step", "key", null);
            fail();
        } catch (Exception e) {
            assertThat(e.getMessage(), is("key is required. jobId = [job], stepId = [step]"));
        }
    }

    @Entity
    @Table(name = "etl_util")
    public static class EtlUtilEntity {

        @Id
        @Column(name = "etl_id", length = 18)
        public Long id;

        @Column(name = "name")
        public String name;

        @Column(name = "age", length = 2)
        public Short age;

    }
}