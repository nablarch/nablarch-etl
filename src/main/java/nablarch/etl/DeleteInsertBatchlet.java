package nablarch.etl;

import nablarch.common.dao.EntityUtil;
import nablarch.core.db.connection.AppDbConnection;
import nablarch.core.db.connection.DbConnectionContext;
import nablarch.core.db.statement.SqlPStatement;
import nablarch.core.log.Logger;
import nablarch.core.log.LoggerManager;
import nablarch.core.transaction.TransactionContext;
import nablarch.etl.config.DbToDbStepConfig;
import nablarch.etl.config.DbToDbStepConfig.InsertMode;
import nablarch.etl.config.DbToDbStepConfig.UpdateSize;
import nablarch.etl.config.EtlConfig;
import nablarch.etl.config.StepConfig;
import nablarch.etl.generator.InsertSqlGenerator;

import javax.batch.api.AbstractBatchlet;
import javax.batch.runtime.context.JobContext;
import javax.batch.runtime.context.StepContext;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.inject.Named;
import java.text.MessageFormat;

/**
 * テーブル間のデータ転送を行う{@link javax.batch.api.Batchlet}実装クラス。
 * <p/>
 * 移送先テーブルのデータをクリーニング後に、移送元のデータを一括で移送先のテーブルに転送（登録）する。
 *
 * @author Hisaaki Shioiri
 */
@Named
@Dependent
public class DeleteInsertBatchlet extends AbstractBatchlet {

    /** ロガー */
    private static final Logger LOGGER = LoggerManager.get("PROGRESS");

    /** {@link JobContext} */
    @Inject
    private JobContext jobContext;

    /** {@link StepContext} */
    @Inject
    private StepContext stepContext;

    /** 範囲更新のヘルパークラス */
    @Inject
    private RangeUpdateHelper rangeUpdateHelper;

    /** ETLの設定 */
    @EtlConfig
    @Inject
    private StepConfig stepConfig;

    /**
     * 一括登録処理を行う。
     *
     * @return 結果(SUCCESS固定)
     * @throws Exception 例外
     */
    @Override
    public String process() throws Exception {

        final DbToDbStepConfig config = (DbToDbStepConfig) stepConfig;

        verify(config);

        final AppDbConnection connection = DbConnectionContext.getConnection();

        cleaning(connection, config);

        insert(connection, config);

        return "SUCCESS";
    }

    /**
     * 設定値の検証を行う。
     *
     * @param config 設定
     */
    private void verify(final DbToDbStepConfig config) {
        final String stepName = stepContext.getStepName();
        final String jobName = jobContext.getJobName();
        EtlUtil.verifyRequired(jobName, stepName, "bean", config.getBean());
        EtlUtil.verifyRequired(jobName, stepName, "sqlId", config.getSqlId());

        final UpdateSize updateSize = config.getUpdateSize();
        final InsertMode insertMode = config.getInsertMode();

        if (insertMode == InsertMode.ORACLE_DIRECT_PATH && updateSize != null) {
            throw new IllegalArgumentException("Oracle Direct Path mode does not support UpdateSize.");
        }

        if (updateSize != null) {
            EtlUtil.verifySqlRangeParameter(config);
            rangeUpdateHelper.verifyUpdateSize(updateSize);
        }
    }

    /**
     * テーブルのクリーニング処理を行う。
     *
     * @param connection データベース接続
     * @param config 設定
     */
    private void cleaning(final AppDbConnection connection, final DbToDbStepConfig config) {
        final String tableName = EntityUtil.getTableName(config.getBean());
        final SqlPStatement statement = connection.prepareStatement(
                "delete from " + tableName);
        statement.execute();
        loggingCleaning(tableName, statement.getUpdateCount());
    }

    /**
     * テーブルへの登録処理を行う。
     *
     * @param connection データベース接続
     * @param config 設定
     */
    private void insert(final AppDbConnection connection, final DbToDbStepConfig config) {
        final InsertMode mode = config.getInsertMode();
        final UpdateSize updateSize = config.getUpdateSize();
        final InsertSqlGenerator sqlGenerator = mode.getInsertSqlGenerator();

        final String insertSql = sqlGenerator.generateSql(config);
        final SqlPStatement statement = connection.prepareStatement(insertSql);
        final String tableName = EntityUtil.getTableName(config.getBean());
        if (mode == InsertMode.ORACLE_DIRECT_PATH || updateSize == null) {
            int updateCount = statement.executeUpdate();
            loggingProgress(tableName, updateCount);
        } else {
            final Range range = new Range(updateSize.getSize(), rangeUpdateHelper.getMaxLineNumber(config));
            long totalWriteCount = 0;
            while (range.next()) {
                statement.setLong(1, range.from);
                statement.setLong(2, range.to);
                totalWriteCount += statement.executeUpdate();
                commit();
                loggingProgress(tableName, totalWriteCount);
            }
        }
    }

    /**
     * コミットを行う。
     */
    private static void commit() {
        TransactionContext.getTransaction().commit();
    }

    /**
     * 進捗ログを出力する。
     * @param tableName 登録先テーブル名
     * @param writeCount 書き込み件数
     */
    private static void loggingProgress(String tableName, long writeCount) {
        LOGGER.logInfo(MessageFormat.format("load progress. table name=[{0}], write count=[{1}]", tableName, writeCount));
    }

    /**
     * クリーニングのログを出力する。
     * @param tableName クリーンしたテーブル名
     * @param deleteCount 削除した件数
     */
    private static void loggingCleaning(String tableName, int deleteCount) {
        LOGGER.logInfo(MessageFormat.format("clean table. table name=[{0}], delete count=[{1}]", tableName, deleteCount));
    }
}
