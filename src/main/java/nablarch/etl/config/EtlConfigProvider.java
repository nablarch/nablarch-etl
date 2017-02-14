package nablarch.etl.config;

import nablarch.core.repository.SystemRepository;

import javax.batch.runtime.context.JobContext;
import javax.batch.runtime.context.StepContext;
import javax.enterprise.inject.Produces;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ETLの設定を提供するクラス。
 * <p/>
 * デフォルトでは、{@link JsonConfigLoader}を使用してETLの設定をロードする。
 * デフォルトのロード処理を変更したい場合は、{@link EtlConfigLoader}の実装クラスを
 * "etlConfigLoader"という名前でコンポーネント定義に設定して行う。
 * 設定のロードは、JVMごとに1度しか行わない。
 *
 * @author Kiyohito Itoh
 */
public final class EtlConfigProvider {

    /** デフォルトの{@link EtlConfigLoader} */
    private static final EtlConfigLoader DEFAULT_LOADER = new JsonConfigLoader();

    /** ロード済みのETLの設定 */
    private static final Map<String, JobConfig> LOADED_ETL_CONFIG = new ConcurrentHashMap<String, JobConfig>();

    /**
     * ETLの設定をロードし、初期化とキャッシュを行う。
     *
     * @param jobContext ジョブコンテキスト
     * @return ETLの設定
     */
    private static JobConfig initialize(JobContext jobContext) {
        JobConfig jobConfig;
        synchronized(LOADED_ETL_CONFIG){
            jobConfig = LOADED_ETL_CONFIG.get(jobContext.getJobName());
            if (jobConfig == null) {
                jobConfig = getLoader().load(jobContext);
                jobConfig.initialize();
                LOADED_ETL_CONFIG.put(jobContext.getJobName(), jobConfig);
            }
        }
        return jobConfig;
    }

    /**
     * {@link EtlConfigLoader}を取得する。
     * <p>
     * "etlConfigLoader"という名前でリポジトリから取得する。
     * リポジトリに存在しない場合は{@link JsonConfigLoader}を返す。
     * @return {@link EtlConfigLoader}
     */
    private static EtlConfigLoader getLoader() {
        EtlConfigLoader loader = SystemRepository.get("etlConfigLoader");
        return loader != null ? loader : DEFAULT_LOADER;
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
    public static StepConfig getConfig(JobContext jobContext, StepContext stepContext) {
        JobConfig jobConfig = LOADED_ETL_CONFIG.get(jobContext.getJobName());
        if (jobConfig == null) {
            jobConfig = initialize(jobContext);
        }

        return jobConfig.getStepConfig(jobContext.getJobName(), stepContext.getStepName());
    }
}
