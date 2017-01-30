package nablarch.etl.config;

import java.util.Collections;
import java.util.Map;

import nablarch.core.util.annotation.Published;

/**
 * ジョブの設定を保持するクラス。
 * <p>
 * ファイルのベースパスに関する設定項目は、ジョブの設定にて指定がない場合、
 * ETLの設定({@link RootConfig})を使用する。
 * 
 * @author Kiyohito Itoh
 */
@Published(tag = "architect")
public class JobConfig {

    /** ジョブID */
    private String jobId;

    /** ステップの設定。キーはステップID */
    private Map<String, StepConfig> steps = Collections.emptyMap();

    /**
     * ジョブIDを取得する。
     * @return ジョブID
     */
    public String getJobId() {
        return jobId;
    }

    /**
     * ジョブIDを設定する。
     * @param jobId ジョブID
     */
    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    /**
     * ステップの設定を取得する。
     * @return ステップの設定
     */
    public Map<String, StepConfig> getSteps() {
        return steps;
    }

    /**
     * ステップの設定を設定する。
     * @param steps ステップの設定
     */
    public void setSteps(Map<String, StepConfig> steps) {
        this.steps = steps;
    }

    /**
     * ステップの設定を順に初期化する。
     */
    public void initialize() {
        for (Map.Entry<String, StepConfig> entry : steps.entrySet()) {
            StepConfig stepConfig = entry.getValue();
            stepConfig.setStepId(entry.getKey());
            stepConfig.initialize();
        }
    }

}
