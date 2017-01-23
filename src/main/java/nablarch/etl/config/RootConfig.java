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

    /** ジョブの設定。キーはジョブID */
    private Map<String, JobConfig> jobs = Collections.emptyMap();

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
     * ジョブの設定を順に初期化する。
     */
    public void initialize() {
        for (Map.Entry<String, JobConfig> entry : jobs.entrySet()) {
            JobConfig jobConfig = entry.getValue();
            jobConfig.setJobId(entry.getKey());
            jobConfig.initialize();
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
