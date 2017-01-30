package nablarch.etl.config;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.List;

import nablarch.core.db.statement.BasicSqlLoader;
import nablarch.etl.config.DbToDbStepConfig.InsertMode;
import nablarch.etl.config.app.TestDto;
import nablarch.etl.config.app.TestDto3;
import nablarch.etl.generator.InsertSqlGenerator;
import nablarch.etl.generator.OracleDirectPathInsertSqlGenerator;
import nablarch.test.support.SystemRepositoryResource;

import org.junit.Rule;
import org.junit.Test;

/**
 * {@link DbToDbStepConfig}のテスト。
 */
public class DbToDbStepConfigTest {

    @Rule
    public SystemRepositoryResource repositoryResource
        = new SystemRepositoryResource("nablarch/etl/config/empty.xml");

    // 正常な設定値
    private final String stepId = "step1";
    private final Class<?> bean = TestDto.class;
    private final String sqlId = "SELECT_PET";
    private final List<String> mergeOnColumns = Arrays.asList("oya_id", "kodomo_id");
    private final DbToDbStepConfig.UpdateSize updateSize;
    {
        updateSize = new DbToDbStepConfig.UpdateSize();
        updateSize.setSize(1000);
        updateSize.setBean(TestDto3.class);
    }

    /**
     * {@link BasicSqlLoader}がリポジトリに登録されていない場合、例外が送出されること。
     */
    @Test
    public void testNotFoundBasicSqlLoader() {

        // 必須項目のみ
        DbToDbStepConfig sut = new DbToDbStepConfig() {{
            setStepId(stepId);
            setBean(bean);
            setSqlId(sqlId);
        }};

        try {
            sut.initialize();
            fail();
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(),
                    is("BasicSqlLoader was not found. Using the name \"sqlLoader\", "
                     + "please set BasicSqlLoader to component configuration."));
        }
    }

    /**
     * 正常に設定された場合、値が取得できること。
     */
    @Test
    public void testNormalSetting() {

        repositoryResource.addComponent("sqlLoader", new BasicSqlLoader());

        // 必須項目のみ
        DbToDbStepConfig sut = new DbToDbStepConfig() {{
            setStepId(stepId);
            setBean(bean);
            setSqlId(sqlId);
        }};
        sut.initialize();

        assertThat(sut.getStepId(), is("step1"));
        assertThat(sut.getBean().getName(), is(TestDto.class.getName()));
        assertThat(sut.getSql(), is("SELECT "
                + "PET_ID, "
                + "PET_NAME "
                + "FROM "
                + "PET "
                + "WHERE "
                + "$if(ownerId) {OWNER_ID = :ownerId} "
                + "AND $if(petName) {PET_NAME LIKE :%petName%} "
                + "$sort(sortId){ "
                + "(idAsc PET_ID) "
                + "(idDesc PET_ID DESC) "
                + "(nameAsc PET_NAME, PROJECT_ID) "
                + "(nameDesc PET_NAME DESC, PROJECT_ID DESC) "
                + "}"));
        assertThat(sut.getMergeOnColumns(), nullValue());
        assertThat(sut.getUpdateSize(), nullValue());
        assertThat("INSERTモードのデフォルトはNORMAL", sut.getInsertMode(), is(DbToDbStepConfig.InsertMode.NORMAL));
        assertThat("NORMAL用のSQLGeneratorが取得できること", sut.getInsertMode()
                .getInsertSqlGenerator(), is(instanceOf(InsertSqlGenerator.class)));


        // 全ての項目を指定
        sut = new DbToDbStepConfig() {{
            setStepId(stepId);
            setBean(bean);
            setSqlId(sqlId);
            setMergeOnColumns(mergeOnColumns);
            setUpdateSize(updateSize);
            setInsertMode(InsertMode.ORACLE_DIRECT_PATH);
        }};
        sut.initialize();

        assertThat(sut.getStepId(), is("step1"));
        assertThat(sut.getBean().getName(), is(TestDto.class.getName()));
        assertThat(sut.getSql(), is("SELECT "
                                      + "PET_ID, "
                                      + "PET_NAME "
                                  + "FROM "
                                      + "PET "
                                  + "WHERE "
                                      + "$if(ownerId) {OWNER_ID = :ownerId} "
                                      + "AND $if(petName) {PET_NAME LIKE :%petName%} "
                                  + "$sort(sortId){ "
                                      + "(idAsc PET_ID) "
                                      + "(idDesc PET_ID DESC) "
                                      + "(nameAsc PET_NAME, PROJECT_ID) "
                                      + "(nameDesc PET_NAME DESC, PROJECT_ID DESC) }"));
        assertThat(sut.getMergeOnColumns(), notNullValue());
        assertThat(sut.getMergeOnColumns().size(), is(2));
        assertThat(sut.getMergeOnColumns().get(0), is("oya_id"));
        assertThat(sut.getMergeOnColumns().get(1), is("kodomo_id"));
        assertThat(sut.getUpdateSize().getSize(), is(1000));
        assertThat(sut.getUpdateSize().getBean().getName(), is(TestDto3.class.getName()));
        assertThat("設定したINSERTモードが取得できること", sut.getInsertMode(), is(InsertMode.ORACLE_DIRECT_PATH));
        assertThat("Oracleダイレクトパスインサート用のSQLGeneratorが取得できること",
                sut.getInsertMode().getInsertSqlGenerator(), is(instanceOf(OracleDirectPathInsertSqlGenerator.class)));
    }
}
