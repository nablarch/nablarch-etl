package nablarch.etl.config;

import nablarch.core.util.FileUtil;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JSON形式のファイルに定義されたETLの設定をロードするクラス。
 * 
 * @author Kiyohito Itoh
 */
public class JsonConfigLoader implements EtlConfigLoader {

    /** 設定ファイルのパス */
    private String configPath;

    /**
     * 設定ファイルのパスを設定する。
     * @param configPath 設定ファイルのパス
     */
    public void setConfigPath(String configPath) {
        this.configPath = configPath;
    }

    /**
     * 設定ファイルから設定をロードする。
     */
    @Override
    public JobConfig load() {

        ObjectMapper mapper = new ObjectMapper();
        mapper.addMixIn(StepConfig.class, PolymorphicStepConfigMixIn.class);

        try {
            return mapper.readValue(FileUtil.getClasspathResourceURL(configPath), JobConfig.class);
        } catch (Exception e) {
            throw new IllegalStateException(
                String.format("failed to load etl config file. file = [%s]", configPath), e);
        }
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
