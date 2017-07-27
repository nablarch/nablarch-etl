package nablarch.etl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.Serializable;

import javax.batch.api.chunk.AbstractItemReader;
import javax.batch.operations.BatchRuntimeException;
import javax.batch.runtime.context.JobContext;
import javax.batch.runtime.context.StepContext;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.inject.Named;

import nablarch.common.databind.ObjectMapper;
import nablarch.common.databind.ObjectMapperFactory;
import nablarch.core.log.basic.LogLevel;
import nablarch.core.log.operation.OperationLogger;
import nablarch.core.message.MessageLevel;
import nablarch.core.message.MessageUtil;
import nablarch.etl.config.EtlConfig;
import nablarch.etl.config.FileToDbStepConfig;
import nablarch.etl.config.PathConfig;
import nablarch.etl.config.StepConfig;
import nablarch.fw.batch.ee.progress.ProgressManager;

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
    private final JobContext jobContext;

    /** {@link StepContext} */
    private final StepContext stepContext;
    
    /** ETLの設定 */
    private final FileToDbStepConfig stepConfig;

    /** 入力ファイルのベースパス */
    private final File inputFileBasePath;

     /** 進捗状況を管理するBean */
    private final ProgressManager progressManager;

    /** データからJavaオブジェクトに変換を行うマッパー */
    private ObjectMapper<?> reader;

    /**
     * コンストラクタ。
     * @param jobContext {@link JobContext}
     * @param stepContext {@link StepContext}
     * @param stepConfig ステップの設定
     * @param inputFileBasePath 入力ファイルの配置ディレクトリ
     * @param progressManager 進捗状況を管理するBean
     */
    @Inject
    public FileItemReader(
            final JobContext jobContext,
            final StepContext stepContext,
            @EtlConfig final StepConfig stepConfig,
            @PathConfig(BasePath.INPUT) final File inputFileBasePath,
            final ProgressManager progressManager) {
        this.jobContext = jobContext;
        this.stepContext = stepContext;
        this.stepConfig = (FileToDbStepConfig) stepConfig;
        this.inputFileBasePath = inputFileBasePath;
        this.progressManager = progressManager;
    }

    /**
     * 入力ファイルを開き、{@link ObjectMapper}を生成する。
     */
    @Override
    public void open(final Serializable checkpoint) throws Exception {
        final String jobId = jobContext.getJobName();
        final String stepId = stepContext.getStepName();

        EtlUtil.verifyRequired(jobId, stepId, "bean", stepConfig.getBean());
        EtlUtil.verifyRequired(jobId, stepId, "fileName", stepConfig.getFileName());

        final File inputFilePath = new File(inputFileBasePath, stepConfig.getFileName());

        reader = ObjectMapperFactory.create(
                stepConfig.getBean(), createFileInputStream(inputFilePath));
        progressManager.setInputCount(getNumberOfRecordInInputFile(createFileInputStream(inputFilePath)));

    }

    /**
     * 入力ファイルのレコード数を返す。
     * @param inputStream 入力ストリーム
     * @return レコード数
     */
    private long getNumberOfRecordInInputFile(final InputStream inputStream) {
        final ObjectMapper<?> inputCountReader =
                ObjectMapperFactory.create(stepConfig.getBean(), inputStream);
        try {
            long inputCount = 0;
            while (true) {
                if (inputCountReader.read() == null) {
                    break;
                }
                inputCount++;
            }
            return inputCount;
        } finally {
            inputCountReader.close();
        }
    }

    /**
     * 入力ファイルのストリームを生成する。
     * @param inputFilePath 入力ファイルパス
     * @return 入力ストリーム
     */
    private InputStream createFileInputStream(final File inputFilePath) {
        try {
            return new FileInputStream(inputFilePath);
        } catch (FileNotFoundException e) {
            final String message = MessageUtil.createMessage(MessageLevel.ERROR, "nablarch.etl.input-file-not-found",
                    inputFilePath.getAbsolutePath())
                    .formatMessage();
            OperationLogger.write(LogLevel.ERROR, message, e);
            throw new BatchRuntimeException(message, e);
        }
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
