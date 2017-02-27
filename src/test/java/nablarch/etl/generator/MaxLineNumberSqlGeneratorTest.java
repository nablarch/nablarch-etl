package nablarch.etl.generator;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;

import java.util.Map;

import javax.persistence.Entity;
import javax.persistence.Table;

import nablarch.core.db.connection.ConnectionFactory;
import nablarch.core.db.connection.DbConnectionContext;
import nablarch.core.transaction.TransactionContext;
import nablarch.etl.InvalidEtlConfigException;
import nablarch.etl.config.DbToDbStepConfig;
import nablarch.test.support.SystemRepositoryResource;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * {@link MaxLineNumberSqlGenerator}のテスト。
 */
public class MaxLineNumberSqlGeneratorTest {

    private final MaxLineNumberSqlGenerator sut = new MaxLineNumberSqlGenerator();
    
    @Rule
    public SystemRepositoryResource systemRepositoryResource = new SystemRepositoryResource("db-default.xml");

    @Before
    public void setUp() throws Exception {
        final ConnectionFactory connectionFactory = systemRepositoryResource.getComponentByType(ConnectionFactory.class);
        DbConnectionContext.setConnection(connectionFactory.getConnection(TransactionContext.DEFAULT_TRANSACTION_CONTEXT_KEY));
    }

    @After
    public void tearDown() throws Exception {
        DbConnectionContext.removeConnection();
    }

    /**
     * 設定で指定されたEntityのテーブル名が入ったSQL文が生成されること。
     */
    @Test
    public void generateMaxLineNumberSql() {

        final DbToDbStepConfig config = new DbToDbStepConfig() {
            {
                setUpdateSize(new UpdateSize());
                getUpdateSize().setBean(EtlMaxLineNumberGenEntity.class);
            }
        };

        final String result = sut.generateSql(config);

        assertThat(result, is("select max(LINE_NUMBER) from etl_work.etl_max_line_number_gen"));
    }

    /**
     * 非Entityクラスはエラーとなること。
     */
    @Test(expected = InvalidEtlConfigException.class)
    public void notEntityClass() throws Exception {

        final DbToDbStepConfig config = new DbToDbStepConfig() {
            {
                setUpdateSize(new UpdateSize());
                getUpdateSize().setBean(Map.class);
            }
        };

        sut.generateSql(config);
    }

    @Entity
    @Table(name = "etl_max_line_number_gen", schema = "etl_work")
    public static class EtlMaxLineNumberGenEntity {
    }
}
