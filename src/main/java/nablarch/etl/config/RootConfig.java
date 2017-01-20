package nablarch.etl.config;

import java.io.File;
import java.util.Collections;
import java.util.Map;

import nablarch.core.repository.SystemRepository;
import nablarch.core.util.annotation.Published;

/**
 * ETLの設定を保持するクラス。
 * 
 * @author Kiyohito Itoh
 */
@Published(tag = "architect")
public class RootConfig {

    /** 入力ファイルのベースパス */
    private File inputFileBasePath;

    /** 出力ファイルのベースパス */
    private File outputFileBasePath;

    /** SQLLoaderに使用するコントロールファイルのベースパス */
    private File sqlLoaderControlFileBasePath;

    /** SQLLoaderが出力するファイルのベースパス */
    private File sqlLoaderOutputFileBasePath;

    /** ジョブの設定。キーはジョブID */
    private Map<String, JobConfig> jobs = Collections.emptyMap();

    /**
     * 入力ファイルのベースパスを取得する。
     * @return 入力ファイルのベースパス
     */
    public File getInputFileBasePath() {
        return inputFileBasePath;
    }

    /**
     * 入力ファイルのベースパスを設定する。
     * @param inputFileBasePath 入力ファイルのベースパス
     */
    public void setInputFileBasePath(File inputFileBasePath) {
        this.inputFileBasePath = inputFileBasePath;
    }

    /**
     * 出力ファイルのベースパスを取得する。
     * @return 出力ファイルのベースパス
     */
    public File getOutputFileBasePath() {
        return outputFileBasePath;
    }

    /**
     * 出力ファイルのベースパスを設定する。
     * @param outputFileBasePath 出力ファイルのベースパス
     */
    public void setOutputFileBasePath(File outputFileBasePath) {
        this.outputFileBasePath = outputFileBasePath;
    }

    /**
     * SQLLoaderに使用するコントロールファイルのベースパスを取得する。
     * @return SQLLoaderに使用するコントロールファイルのベースパス
     */
    public File getSqlLoaderControlFileBasePath() {
        return sqlLoaderControlFileBasePath;
    }

    /**
     * SQLLoaderに使用するコントロールファイルのベースパスを設定する。
     * @param sqlLoaderControlFileBasePath SQLLoaderに使用するコントロールファイルのベースパス
     */
    public void setSqlLoaderControlFileBasePath(File sqlLoaderControlFileBasePath) {
        this.sqlLoaderControlFileBasePath = sqlLoaderControlFileBasePath;
    }

    /**
     * SQLLoaderが出力するファイルのベースパスを取得する。
     * @return SQLLoaderが出力するファイルのベースパス
     */
    public File getSqlLoaderOutputFileBasePath() {
        return sqlLoaderOutputFileBasePath;
    }

    /**
     * SQLLoaderが出力するファイルのベースパスを設定する。
     * @param sqlLoaderOutputFileBasePath SQLLoaderが出力するファイルのベースパス
     */
    public void setSqlLoaderOutputFileBasePath(File sqlLoaderOutputFileBasePath) {
        this.sqlLoaderOutputFileBasePath = sqlLoaderOutputFileBasePath;
    }

    /**
     * ジョブの設定を取得する。
     * @return ジョブの設定
     */
    public Map<String, JobConfig> getJobs() {
        return jobs;
    }

    /**
     * ジョブの設定を設定する。
     * @param jobs ジョブの設定
     */
    public void setJobs(Map<String, JobConfig> jobs) {
        this.jobs = jobs;
    }

    /**
     * ペースパスとジョブの設定を初期化する。
     * <p/>
     * ベースパスはシステムリポジトリから設定する。
     * ジョブの設定を順に初期化する。
     */
    public void initialize() {
        inputFileBasePath = new File(SystemRepository.getString("inputFileBasePath"));
        outputFileBasePath = new File(SystemRepository.getString("outputFileBasePath"));
        sqlLoaderControlFileBasePath = new File(SystemRepository.getString("sqlLoaderControlFileBasePath"));
        sqlLoaderOutputFileBasePath = new File(SystemRepository.getString("sqlLoaderOutputFileBasePath"));

        for (Map.Entry<String, JobConfig> entry : jobs.entrySet()) {
            JobConfig jobConfig = entry.getValue();
            jobConfig.setJobId(entry.getKey());
            jobConfig.initialize(this);
        }
    }

    /**
     * ジョブIDとステップIDに対応するステップの設定を取得する。
     * <p>
     * 見つからない場合は、{@link IllegalStateException}を送出する。
     * 
     * @param jobId ジョブID
     * @param stepId ステップID
     * @return ジョブIDとステップIDに対応するステップの設定
     */
    @SuppressWarnings("unchecked")
    public <T extends StepConfig> T getStepConfig(String jobId, String stepId) {

        JobConfig jobConfig = getJobs().get(jobId);
        if (jobConfig == null) {
            throw new IllegalStateException(
                String.format("job configuration was not found. jobId = [%s]", jobId));
        }

        StepConfig stepConfig = jobConfig.getSteps().get(stepId);
        if (stepConfig == null) {
            throw new IllegalStateException(
                String.format("step configuration was not found. jobId = [%s], stepId = [%s]",
                        jobId, stepId));
        }
        return (T) stepConfig;
    }
}
