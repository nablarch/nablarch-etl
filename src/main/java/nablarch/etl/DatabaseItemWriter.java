package nablarch.etl;

import nablarch.common.dao.EntityUtil;
import nablarch.common.dao.UniversalDao;
import nablarch.core.log.Logger;
import nablarch.core.log.LoggerManager;
import nablarch.etl.config.DbToDbStepConfig;
import nablarch.etl.config.EtlConfig;
import nablarch.etl.config.FileToDbStepConfig;
import nablarch.etl.config.StepConfig;

import javax.batch.api.chunk.AbstractItemWriter;
import javax.batch.runtime.context.JobContext;
import javax.batch.runtime.context.StepContext;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;
import java.text.MessageFormat;
import java.util.List;


/**
 * データベースのテーブルに対してデータを書き込む{@link javax.batch.api.chunk.ItemWriter}実装クラス。
 * <p/>
 * {@link UniversalDao#insert(Object)}を使用して、Entityオブジェクトの内容をデータベースに登録する。
 *
 * @author Hisaaki Shioiri
 */
@Named
@Dependent
public class DatabaseItemWriter extends AbstractItemWriter {

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

    @Override
    public void open(Serializable checkpoint) throws Exception {
        if (stepConfig instanceof DbToDbStepConfig) {
            loggingStartChunk(EntityUtil.getTableName(((DbToDbStepConfig)stepConfig).getBean()));
        } else if (stepConfig instanceof FileToDbStepConfig) {
            loggingStartChunk(EntityUtil.getTableName(((FileToDbStepConfig)stepConfig).getBean()));
        }
    }

    @Override
    public void writeItems(final List<Object> list) throws Exception {
        UniversalDao.batchInsert(list);
    }

    /**
     * 進捗ログを出力する。
     * @param tableName 登録先テーブル名
     */
    private static void loggingStartChunk(String tableName) {
        LOGGER.logInfo(MessageFormat.format("chunk start. table name=[{0}]", tableName));
    }
}
