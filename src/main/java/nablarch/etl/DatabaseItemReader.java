package nablarch.etl;

import nablarch.common.dao.UniversalDao;
import nablarch.etl.config.DbInputStepConfig;
import nablarch.etl.config.EtlConfig;
import nablarch.etl.config.StepConfig;

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
 * 指定されたSELECT文を使ってテーブルから取得したレコードの読み込みを行う{@link javax.batch.api.chunk.AbstractItemReader}の実装クラス。
 *
 * @author Kumiko Omi
 */
@Named
@Dependent
public class DatabaseItemReader extends AbstractItemReader {

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

    /** テーブルのデータを格納する変数 */
    private Iterator<?> reader;

    /**
     * テーブルにアクセスして指定されたSELECT文を使ってレコードを取得する。
     */
    @Override
    public void open(Serializable checkpoint) throws SQLException {

        final String jobId = jobContext.getJobName();
        final String stepId = stepContext.getStepName();

        final DbInputStepConfig config = (DbInputStepConfig) stepConfig;

        EtlUtil.verifyRequired(jobId, stepId, "bean", config.getBean());
        EtlUtil.verifyRequired(jobId, stepId, "sqlId", config.getSqlId());

        reader = UniversalDao.defer().findAllBySqlFile(
                        config.getBean(), config.getSqlId()).iterator();
    }

    @Override
    public Object readItem() {
        if (reader.hasNext()) {
            return reader.next();
        }
        return null;
    }

}
