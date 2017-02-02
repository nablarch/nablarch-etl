package nablarch.etl;

import nablarch.common.databind.ObjectMapper;
import nablarch.common.databind.ObjectMapperFactory;
import nablarch.etl.config.DbToFileStepConfig;
import nablarch.etl.config.EtlConfig;
import nablarch.etl.config.PathConfig;
import nablarch.etl.config.StepConfig;

import javax.batch.api.chunk.AbstractItemWriter;
import javax.batch.runtime.context.JobContext;
import javax.batch.runtime.context.StepContext;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;

/**
 * ファイルにデータを書き込む{@link javax.batch.api.chunk.ItemWriter}の実装クラス。
 *
 * @author Kumiko Omi
 */
@Dependent
@Named
public class FileItemWriter extends AbstractItemWriter {

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

    /** 出力ファイルのベースパス */
    @PathConfig(BasePath.OUTPUT)
    @Inject
    private File outputFileBasePath;

    /** Javaオブジェクトからデータに変換を行うマッパー */
    ObjectMapper<Object> mapper;

    @SuppressWarnings("unchecked")
    @Override
    public void open(Serializable checkpoint) throws Exception {

        final String jobId = jobContext.getJobName();
        final String stepId = stepContext.getStepName();

        final DbToFileStepConfig config = (DbToFileStepConfig) stepConfig;

        EtlUtil.verifyRequired(jobId, stepId, "bean", config.getBean());
        EtlUtil.verifyRequired(jobId, stepId, "fileName", config.getFileName());

        mapper = (ObjectMapper<Object>) ObjectMapperFactory.create(
                        config.getBean(), new FileOutputStream(new File(outputFileBasePath, config.getFileName())));

        super.open(checkpoint);
    }

    @Override
    public void writeItems(List<Object> items) throws IOException {
        for (Object item : items) {
            mapper.write(item);
        }
    }

    @Override
    public void close() throws Exception {
        if (mapper != null) {
            mapper.close();
        }
    }
}
