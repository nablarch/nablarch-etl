package nablarch.etl.config;

import nablarch.core.repository.SystemRepository;

import javax.batch.runtime.context.JobContext;
import javax.batch.runtime.context.StepContext;
import javax.enterprise.inject.Produces;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ETLの設定を提供するクラス。
 * <p>
 * デフォルトでは、{@link JsonConfigLoader}を使用してETLの設定をロードする。
 * デフォルトのロード処理を変更したい場合は、{@link EtlConfigLoader}の実装クラスを
 * "etlConfigLoader"という名前でコンポーネント定義に設定して行う。
 *
 * ロードしたジョブの設定はキャッシュし、同じジョブの場合はキャッシュから返す。
 *
 * @author Kiyohito Itoh
 */
public final class EtlConfigProvider {

    /** ロード済みのETLの設定 */
    private static final Map<String, JobConfig> cacheJobConfig = new ConcurrentHashMap<String, JobConfig>();

    /**
     * ETLの設定をロードし、初期化とキャッシュを行う。
     *
     * @param jobContext ジョブコンテキスト
     * @return ETLの設定
     */
    private synchronized JobConfig initialize(JobContext jobContext) {
        JobConfig jobConfig = cacheJobConfig.get(jobContext.getJobName());
        if ( jobConfig != null) {
            return jobConfig;
        }
        jobConfig = getLoader().load(jobContext);
        jobConfig.initialize();
        cacheJobConfig.put(jobContext.getJobName(), jobConfig);
        return jobConfig;
    }

    /**
     * {@link EtlConfigLoader}を取得する。
     * <p>
     * "etlConfigLoader"という名前でリポジトリから取得する。
     * リポジトリに存在しない場合は{@link JsonConfigLoader}を返す。
     * @return {@link EtlConfigLoader}
     */
    private EtlConfigLoader getLoader() {
        EtlConfigLoader loader = SystemRepository.get("etlConfigLoader");

        return loader != null ? loader : new JsonConfigLoader();
    }

    /**
     * ETLの設定を取得する。
     * <p/>
     * ジョブコンテキストとステップコンテキストに対応するステップの設定を取得する。
     *
     * @param jobContext ジョブコンテキスト
     * @param stepContext ステップコンテキスト
     * @return 実行するステップの設定
     */
    @EtlConfig
    @Produces
    public StepConfig getConfig(JobContext jobContext, StepContext stepContext) {
        JobConfig jobConfig = cacheJobConfig.get(jobContext.getJobName());
        if (jobConfig == null) {
            jobConfig = initialize(jobContext);
        }

        return jobConfig.getStepConfig(stepContext.getStepName());
    }
}
