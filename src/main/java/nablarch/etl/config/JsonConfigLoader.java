package nablarch.etl.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import nablarch.core.util.FileUtil;

import javax.batch.runtime.context.JobContext;
import java.util.LinkedHashMap;
import java.util.Map;

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

    /** {@link ObjectMapper}でjsonからMapオブジェクトを生成する際に使用する型情報 */
    private static final TypeReference<Map<String, StepConfig>> TYPE_REFERENCE = new TypeReference<Map<String, StepConfig>>(){};

    /**
     * 設定ファイルから設定をロードする。
     */
    @Override
    public JobConfig load(JobContext jobContext) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.addMixIn(StepConfig.class, PolymorphicStepConfigMixIn.class);

        final String configFilePath = configBasePath + jobContext.getJobName() + ".json";
        try {
            JobConfig jobConfig = new JobConfig();
            Map<String, StepConfig> steps = mapper.readValue(FileUtil.getResourceURL(configFilePath), TYPE_REFERENCE);
            jobConfig.setSteps(steps);
            return jobConfig;
        } catch (Exception e) {
            throw new IllegalStateException(
                String.format("failed to load etl config file. file = [%s]", configFilePath), e);
        }
    }

    /**
     * 設定ファイルを配置するディレクトリのベースパスを設定する。
     * <p/>
     * パスの指定方法は{@link FileUtil#getResourceURL(String)}を参照。
     *
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
