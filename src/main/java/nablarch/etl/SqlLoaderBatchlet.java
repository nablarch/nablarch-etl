package nablarch.etl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import javax.batch.api.AbstractBatchlet;
import javax.batch.runtime.context.JobContext;
import javax.batch.runtime.context.StepContext;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.inject.Named;

import nablarch.core.log.basic.LogLevel;
import nablarch.core.log.operation.OperationLogger;
import nablarch.core.message.MessageLevel;
import nablarch.core.message.MessageUtil;
import nablarch.core.repository.SystemRepository;
import nablarch.core.util.FileUtil;
import nablarch.etl.config.EtlConfig;
import nablarch.etl.config.FileToDbStepConfig;
import nablarch.etl.config.PathConfig;
import nablarch.etl.config.StepConfig;

/**
 * SQL*Loaderを用いてCSVファイルのデータをワークテーブルに登録する{@link javax.batch.api.Batchlet}の実装クラス。
 *
 * @author Naoki Yamamoto
 */
@Named
@Dependent
public class SqlLoaderBatchlet extends AbstractBatchlet {

    /** {@link JobContext} */
    private final JobContext jobContext;

    /** {@link StepContext} */
    private final StepContext stepContext;

    /** ETLの設定 */
    private final FileToDbStepConfig stepConfig;

    /** 入力ファイルのベースパス */
    private final File inputFileBasePath;

    /** SQLLoaderに使用するコントロールファイルのベースパス */
    private final File sqlLoaderControlFileBasePath;

    /** SQLLoaderが出力するファイルのベースパス */
    private final File sqlLoaderOutputFileBasePath;

    /**
     * コンストラクタ。
     * @param jobContext JobContext
     * @param stepContext StepContext
     * @param stepConfig ステップの設定
     * @param inputFileBasePath 入力ファイルのあるディレクトリ
     * @param sqlLoaderControlFileBasePath SQL*Loaderのコントロールファイルが置かれたディレクトリ
     * @param sqlLoaderOutputFileBasePath SQL*Loaderが出力するファイルを置くディレクトリ
     */
    @Inject
    public SqlLoaderBatchlet(
            final JobContext jobContext,
            final StepContext stepContext,
            @EtlConfig final StepConfig stepConfig,
            @PathConfig(BasePath.INPUT) final File inputFileBasePath,
            @PathConfig(BasePath.SQLLOADER_CONTROL) final File sqlLoaderControlFileBasePath,
            @PathConfig(BasePath.SQLLOADER_OUTPUT) final File sqlLoaderOutputFileBasePath) {
        this.jobContext = jobContext;
        this.stepContext = stepContext;
        this.stepConfig = (FileToDbStepConfig) stepConfig;
        this.inputFileBasePath = inputFileBasePath;
        this.sqlLoaderControlFileBasePath = sqlLoaderControlFileBasePath;
        this.sqlLoaderOutputFileBasePath = sqlLoaderOutputFileBasePath;
    }


    /**
     * SQL*Loaderを実行してCSVファイルのデータをワークテーブルに一括登録する。
     *
     * @return 実行結果
     * @throws Exception 例外
     */
    @Override
    public String process() throws Exception {

        final String jobId = jobContext.getJobName();
        final String stepId = stepContext.getStepName();

        EtlUtil.verifyRequired(jobId, stepId, "fileName", stepConfig.getFileName());
        EtlUtil.verifyRequired(jobId, stepId, "bean", stepConfig.getBean());

        final String ctlFileName = stepConfig.getBean().getSimpleName();
        final String ctlFile = new File(sqlLoaderControlFileBasePath, ctlFileName + ".ctl").getPath();
        final File dataFile = new File(inputFileBasePath, stepConfig.getFileName());
        final String badFile = new File(sqlLoaderOutputFileBasePath, ctlFileName + ".bad").getPath();
        final String logFile = new File(sqlLoaderOutputFileBasePath, ctlFileName + ".log").getPath();

        if (!dataFile.isFile()) {
            OperationLogger.write(LogLevel.ERROR,
                    MessageUtil.createMessage(MessageLevel.ERROR, "nablarch.etl.input-file-not-found", dataFile.getAbsoluteFile()).formatMessage());
            return "FAILED";
        }

        final SqlLoaderRunner runner = new SqlLoaderRunner(ctlFile, dataFile.getPath(), badFile, logFile);
        runner.execute();

        if (!runner.isSuccess()) {
            throw new SqlLoaderFailedException(
                    "failed to execute SQL*Loader. controlFile = [" + ctlFile + ']');
        }
        return "SUCCESS";
    }

    /**
     * SQL*Loaderを実行するクラス。
     *
     * @author Naoki Yamamoto
     */
    private static class SqlLoaderRunner {

        /** コントロールファイル */
        private final String ctrlFile;

        /** データファイル */
        private final String dataFile;

        /** BADファイル */
        private final String badFile;

        /** ログファイル */
        private final String logFile;

        /** SQL*Loaderの{@link Process} */
        private Process process;

        /**
         * コンストラクタ。
         *
         * @param ctrlFile コントロールファイル
         * @param dataFile データファイル
         * @param badFile BADファイル
         * @param logFile ログファイル
         */
        private SqlLoaderRunner(String ctrlFile, String dataFile, String badFile, String logFile) {
            this.ctrlFile = ctrlFile;
            this.dataFile = dataFile;
            this.badFile = badFile;
            this.logFile = logFile;
        }

        /**
         * SQL*Loaderを実行する。
         *
         * @throws IOException 入出力例外
         * @throws InterruptedException SQL*Loaderの終了待ちをしているスレッドが、他のスレッドによって割り込まれた場合
         */
        public void execute() throws IOException, InterruptedException {

            final SqlLoaderConfig config = getConfig();
            ProcessBuilder pb = new ProcessBuilder(
                    "sqlldr",
                    "USERID=" + config.getUserName() + '/' + config.getPassword() + '@' + config.getDatabaseName(),
                    "CONTROL=" + ctrlFile,
                    "DATA=" + dataFile,
                    "BAD=" + badFile,
                    "LOG=" + logFile,
                    "SILENT=(HEADER,FEEDBACK)");
            pb.redirectErrorStream(true);
            process = pb.start();

            InputStream is = process.getInputStream();
            try {
                while (is.read() >= 0) {
                    // 標準出力の内容はログに出力しないが、
                    // バッファサイズが上限を超えた場合にプロセスが待ち状態になるのを防ぐため、
                    // プロセスが完了するまで標準出力の読み込みを行う。
                }
            } finally {
                FileUtil.closeQuietly(is);
            }

            process.waitFor();
        }

        /**
         * SQL*Loaderのプロセスが正常終了しているか否か。
         *
         * @return プロセスが正常終了していれば{@code true}
         */
        public boolean isSuccess() {
            return process.exitValue() == 0;
        }
        
        /**
         * SQL*Loaderの実行時に必要な設定情報を{@link SystemRepository}から取得する。
         *
         * {@code SystemRepository}には、コンポーネント名を{@code sqlLoaderConfig}として{@link SqlLoaderConfig}を設定する必要がある。
         *
         * @return SqlLoaderを実行するための設定情報
         */
        private static SqlLoaderConfig getConfig() {
            final SqlLoaderConfig config = SystemRepository.get("sqlLoaderConfig");
            if (config == null) {
                throw new IllegalStateException("SqlLoaderConfig must be registered in SystemRepository. key=[sqlLoaderConfig]");
            }
            return config;
        }
    }
}
