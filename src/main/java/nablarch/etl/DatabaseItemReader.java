package nablarch.etl;

import nablarch.common.dao.UniversalDao;
import nablarch.etl.config.DbInputStepConfig;
import nablarch.etl.config.EtlConfig;
import nablarch.etl.config.StepConfig;
import nablarch.fw.batch.ee.chunk.BaseDatabaseItemReader;
import nablarch.fw.batch.ee.progress.ProgressManager;

import javax.batch.api.chunk.AbstractItemReader;
import javax.batch.runtime.context.JobContext;
import javax.batch.runtime.context.StepContext;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;
import java.sql.SQLException;
import java.util.Iterator;

/**
 * 指定されたSELECT文を使ってテーブルから取得したレコードの読み込みを行う{@link AbstractItemReader}の実装クラス。
 *
 * @author Kumiko Omi
 */
@Named
@Dependent
public class DatabaseItemReader extends BaseDatabaseItemReader {

    /** {@link JobContext} */
    private final JobContext jobContext;

    /** {@link StepContext} */
    private final StepContext stepContext;

    /** 進捗状況を管理するBean */
    private final ProgressManager progressManager;

    /** ETLの設定 */
    private final DbInputStepConfig stepConfig;

    /** テーブルのデータを格納する変数 */
    private Iterator<?> reader;

    /**
     * コンストラクタ。
     *
     * @param jobContext {@link JobContext}
     * @param stepContext {@link StepContext}
     * @param progressManager {@link ProgressManager}
     * @param stepConfig ステップ設定
     */
    @Inject
    public DatabaseItemReader(
            final JobContext jobContext,
            final StepContext stepContext,
            final ProgressManager progressManager,
            @EtlConfig final StepConfig stepConfig) {
        this.jobContext = jobContext;
        this.stepContext = stepContext;
        this.progressManager = progressManager;
        this.stepConfig = (DbInputStepConfig) stepConfig;
    }

    /**
     * テーブルにアクセスして指定されたSELECT文を使ってレコードを取得する。
     */
    @Override
    public void doOpen(final Serializable checkpoint) throws SQLException {

        final String jobId = jobContext.getJobName();
        final String stepId = stepContext.getStepName();

        EtlUtil.verifyRequired(jobId, stepId, "bean", stepConfig.getBean());
        EtlUtil.verifyRequired(jobId, stepId, "sqlId", stepConfig.getSqlId());

        progressManager.setInputCount(UniversalDao.countBySqlFile(stepConfig.getBean(), stepConfig.getSqlId()));

        reader = UniversalDao.defer().findAllBySqlFile(
                        stepConfig.getBean(), stepConfig.getSqlId()).iterator();
    }

    @Override
    public Object readItem() {
        if (reader.hasNext()) {
            return reader.next();
        }
        return null;
    }
}
