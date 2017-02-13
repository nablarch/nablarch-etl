package nablarch.etl.config;

import nablarch.core.util.annotation.Published;

import javax.batch.runtime.context.JobContext;

/**
 * 外部リソースに定義されたETLの設定をロードするインタフェース。
 * 
 * @author Kiyohito Itoh
 */
@Published(tag = "architect")
public interface EtlConfigLoader {

    /**
     * ETLの設定をロードする。
     *
     * @param jobContext ジョブコンテキスト
     * @return ETLの設定
     */
    JobConfig load(JobContext jobContext);

}
