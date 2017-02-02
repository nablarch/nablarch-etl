package nablarch.etl;

import nablarch.common.dao.EntityUtil;
import nablarch.core.db.connection.AppDbConnection;
import nablarch.core.db.connection.DbConnectionContext;
import nablarch.core.db.statement.SqlPStatement;
import nablarch.core.log.Logger;
import nablarch.core.log.LoggerManager;
import nablarch.core.transaction.TransactionContext;
import nablarch.etl.config.DbToDbStepConfig;
import nablarch.etl.config.DbToDbStepConfig.UpdateSize;
import nablarch.etl.config.EtlConfig;
import nablarch.etl.config.StepConfig;
import nablarch.etl.generator.MergeSqlGenerator;

import javax.batch.api.AbstractBatchlet;
import javax.batch.runtime.context.JobContext;
import javax.batch.runtime.context.StepContext;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.inject.Named;
import java.text.MessageFormat;

/**
 * 入力リソース(SELECT文の結果)を出力テーブルにMERGEする{@link javax.batch.api.Batchlet}実装クラス。
 *
 * @author Hisaaki Shioiri
 */
@Named
@Dependent
public class MergeBatchlet extends AbstractBatchlet {

    /** ロガー */
    private static final Logger LOGGER = LoggerManager.get("PROGRESS");

    /** {@link JobContext} */
    @Inject
    private JobContext jobContext;

    /** {@link StepContext} */
    @Inject
    private StepContext stepContext;

    /** ETLの設定 */
    @EtlConfig
    @Inject
    private StepConfig stepConfig;

    /** 範囲更新のヘルパークラス */
    @Inject
    private RangeUpdateHelper rangeUpdateHelper;

    /**
     * 一括でのMERGE処理を行う。
     *
     * @return 結果(SUCCESS固定)
     * @throws Exception 例外
     */
    @Override
    public String process() throws Exception {

        final MergeSqlGenerator sqlGenerator = new MergeSqlGenerator();

        final String jobId = jobContext.getJobName();
        final String stepId = stepContext.getStepName();

        final DbToDbStepConfig config = (DbToDbStepConfig) stepConfig;

        EtlUtil.verifyRequired(jobId, stepId, "bean", config.getBean());
        EtlUtil.verifyRequired(jobId, stepId, "sqlId", config.getSqlId());
        EtlUtil.verifyRequired(jobId, stepId, "mergeOnColumns", config.getMergeOnColumns());

        final AppDbConnection connection = DbConnectionContext.getConnection();
        final String mergeSql = sqlGenerator.generateSql(config);
        final SqlPStatement statement = connection.prepareStatement(mergeSql);

        final UpdateSize updateSize = config.getUpdateSize();
        final String tableName = EntityUtil.getTableName(config.getBean());

        if (updateSize == null) {
            int updateCount = statement.executeUpdate();
            loggingProgress(tableName, updateCount);
        } else {

            EtlUtil.verifySqlRangeParameter(config);
            rangeUpdateHelper.verifyUpdateSize(updateSize);

            final Long maxSize = rangeUpdateHelper.getMaxLineNumber(config);
            final Range range = new Range(updateSize.getSize(), maxSize);
            long totalMergeCount = 0;
            while (range.next()) {
                statement.setLong(1, range.from);
                statement.setLong(2, range.to);
                totalMergeCount += statement.executeUpdate();
                commit();
                loggingProgress(tableName, totalMergeCount);
            }
        }

        return "SUCCESS";
    }

    /**
     * コミットを行う。
     */
    private static void commit() {
        TransactionContext.getTransaction().commit();
    }

    /**
     * 進捗ログを出力する。
     * @param tableName マージ先テーブル名
     * @param mergeCount マージした件数
     */
    private static void loggingProgress(String tableName, long mergeCount) {
        LOGGER.logInfo(MessageFormat.format("load progress. table name=[{0}], merge count=[{1}]", tableName, mergeCount));
    }
}
