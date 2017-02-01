package nablarch.etl;

import java.util.List;

import javax.batch.api.AbstractBatchlet;
import javax.batch.runtime.context.JobContext;
import javax.batch.runtime.context.StepContext;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.inject.Named;

import nablarch.common.dao.EntityUtil;
import nablarch.core.db.connection.AppDbConnection;
import nablarch.core.db.connection.DbConnectionContext;
import nablarch.core.db.statement.SqlPStatement;
import nablarch.etl.config.EtlConfig;
import nablarch.etl.config.JobConfig;
import nablarch.etl.config.TruncateStepConfig;

/**
 * テーブルのデータをクリーニング(truncate)する{@link javax.batch.api.Batchlet}実装クラス。
 * <p/>
 * {@link TruncateStepConfig}で指定されたEntityクラスに対応するテーブルのデータをクリーニング(truncate)する。
 *
 * @author Hisaaki Shioiri
 */
@Named
@Dependent
public class TableCleaningBatchlet extends AbstractBatchlet {

    /** {@link JobContext} */
    @Inject
    private JobContext jobContext;

    /** {@link StepContext} */
    @Inject
    private StepContext stepContext;

    /** ETLの設定 */
    @Inject
    @EtlConfig
    private JobConfig rootConfig;


    @Override
    public String process() throws Exception {

        final TruncateStepConfig config = rootConfig.getStepConfig(stepContext.getStepName());
        final List<Class<?>> entities = config.getEntities();

        final AppDbConnection connection = DbConnectionContext.getConnection();
        for (Class<?> entity : entities) {
            final SqlPStatement statement = connection.prepareStatement(
                    "truncate table " + EntityUtil.getTableNameWithSchema(entity));
            statement.execute();
        }

        return "SUCCESS";
    }
}
