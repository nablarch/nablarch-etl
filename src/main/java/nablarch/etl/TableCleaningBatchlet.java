package nablarch.etl;

import nablarch.common.dao.EntityUtil;
import nablarch.core.db.connection.AppDbConnection;
import nablarch.core.db.connection.DbConnectionContext;
import nablarch.core.db.statement.SqlPStatement;
import nablarch.etl.config.EtlConfig;
import nablarch.etl.config.StepConfig;
import nablarch.etl.config.TruncateStepConfig;

import javax.batch.api.AbstractBatchlet;
import javax.batch.runtime.context.JobContext;
import javax.batch.runtime.context.StepContext;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;

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
    private StepConfig stepConfig;


    @Override
    public String process() throws Exception {

        final TruncateStepConfig config = (TruncateStepConfig) stepConfig;
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
