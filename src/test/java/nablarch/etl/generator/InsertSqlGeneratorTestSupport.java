package nablarch.etl.generator;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import nablarch.common.dao.DatabaseUtil;
import nablarch.core.db.connection.DbConnectionContext;
import nablarch.core.db.connection.TransactionManagerConnection;
import nablarch.test.support.db.helper.VariousDbTestHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;

import mockit.Mocked;
import mockit.Expectations;

/**
 * InsertSqlGeneratorのテストをサポートするクラス。
 */
public class InsertSqlGeneratorTestSupport {

    @Mocked
    TransactionManagerConnection mockConnection;

    Connection connection;

    @BeforeClass
    public static void setUpClass() throws Exception {
        VariousDbTestHelper.createTable(EtlInsertGenEntity.class);
    }

    @Before
    public void setUp() throws Exception {
        connection = VariousDbTestHelper.getNativeConnection();
        new Expectations() {{
            mockConnection.getConnection();
            result = connection;
            minTimes = 0;
        }};
        DbConnectionContext.setConnection(mockConnection);
    }

    @After
    public void tearDown() throws Exception {
        connection.close();
        DbConnectionContext.removeConnection();
    }

    protected List<String> getColumnNames(String tableName) throws SQLException {
        final ResultSet columns = connection.getMetaData()
                .getColumns(null, null, DatabaseUtil.convertIdentifiers(tableName), null);

        Map<Integer, String> names = new TreeMap<Integer, String>();
        while (columns.next()) {
            names.put(
                    columns.getInt("ORDINAL_POSITION"),
                    columns.getString("COLUMN_NAME")
            );
        }
        return new ArrayList<String>(names.values());
    }

    @Entity
    @Table(name = "etl_insert_gen")
    public static class EtlInsertGenEntity {

        @Id
        @Column(name = "TEST_ID", length = 15)
        public Long id;

        @Column(name = "last_name")
        public String lastName;

        @Column(name = "first_name")
        public String firstName;

        @Id
        @Column(name = "TEST_ID")
        public Long getId() {
            return id;
        }

        public String getLastName() {
            return lastName;
        }

        public String getFirstName() {
            return firstName;
        }
    }
}
