package nablarch.etl;

import nablarch.common.databind.ObjectMapper;
import nablarch.common.databind.ObjectMapperFactory;
import nablarch.etl.config.EtlConfig;
import nablarch.etl.config.FileToDbStepConfig;
import nablarch.etl.config.PathConfig;
import nablarch.etl.config.StepConfig;

import javax.batch.api.chunk.AbstractItemReader;
import javax.batch.runtime.context.JobContext;
import javax.batch.runtime.context.StepContext;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.File;
import java.io.FileInputStream;
import java.io.Serializable;

/**
 * 入力ファイルからJavaオブジェクトへ変換を行う{@link javax.batch.api.chunk.ItemReader}実装クラス。
 * <p/>
 * 本実装ではチェックポイントはサポートしない。このため、restart時にはファイルの先頭から処理を再開する。
 *
 * @author Hisaaki Shioiri
 */
@Named
@Dependent
public class FileItemReader extends AbstractItemReader {

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

    /** 入力ファイルのベースパス */
    @PathConfig(BasePath.INPUT)
    @Inject
    private File inputFileBasePath;

    /** データからJavaオブジェクトに変換を行うマッパー */
    private ObjectMapper<?> reader;

    /**
     * 入力ファイルを開き、{@link ObjectMapper}を生成する。
     */
    @Override
    public void open(final Serializable checkpoint) throws Exception {

        final String jobId = jobContext.getJobName();
        final String stepId = stepContext.getStepName();

        final FileToDbStepConfig config = (FileToDbStepConfig) stepConfig;

        EtlUtil.verifyRequired(jobId, stepId, "bean", config.getBean());
        EtlUtil.verifyRequired(jobId, stepId, "fileName", config.getFileName());

        reader = ObjectMapperFactory.create(
                    config.getBean(), new FileInputStream(new File(inputFileBasePath, config.getFileName())));
    }

    @Override
    public Object readItem() throws Exception {
        return reader.read();
    }

    @Override
    public void close() throws Exception {
        reader.close();
    }
}
