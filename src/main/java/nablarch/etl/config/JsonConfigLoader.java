package nablarch.etl.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.ObjectMapper;
import nablarch.core.util.FileUtil;

import javax.batch.runtime.context.JobContext;

/**
 * JSON形式のファイルに定義されたETLの設定をロードするクラス。
 * <p/>
 * "classpath:META-INF/etl-config/" 配下に置かれた "ジョブID.json" をロードする。
 * 
 * @author Kiyohito Itoh
 */
public class JsonConfigLoader implements EtlConfigLoader {

    /** 設定ファイルを配置するディレクトリのベースパス */
    private String configBasePath = "classpath:META-INF/etl-config/";

    /**
     * 設定ファイルから設定をロードする。
     */
    @Override
    public JobConfig load(JobContext jobContext) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.addMixIn(StepConfig.class, PolymorphicStepConfigMixIn.class);

        final String configFilePath = configBasePath + jobContext.getJobName() + ".json";
        try {
            return mapper.readValue(FileUtil.getResourceURL(configFilePath), JobConfig.class);
        } catch (Exception e) {
            throw new IllegalStateException(
                String.format("failed to load etl config file. file = [%s]", configFilePath), e);
        }
    }

    /**
     * 設定ファイルを配置するディレクトリのベースパスを設定する。
     * @param configBasePath ディレクトリのベースパス
     */
    public void setConfigBasePath(String configBasePath) {
        this.configBasePath = configBasePath;
    }

    /**
     * JSONに定義されたステップの設定を{@link StepConfig}のサブクラスにバインドする定義。
     * <p>
     * Jacksonが提供するPolymorphic Type Handling機能を使用する。
     * 
     * @author Kiyohito Itoh
     */
    @JsonTypeInfo(use = Id.NAME, property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(name = "db2db", value = DbToDbStepConfig.class),
        @JsonSubTypes.Type(name = "db2file", value = DbToFileStepConfig.class),
        @JsonSubTypes.Type(name = "file2db", value = FileToDbStepConfig.class),
        @JsonSubTypes.Type(name = "validation", value = ValidationStepConfig.class),
        @JsonSubTypes.Type(name = "truncate", value = TruncateStepConfig.class)
    })
    interface PolymorphicStepConfigMixIn {
        // nop
    }
}
