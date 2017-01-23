package nablarch.etl.config;

import nablarch.etl.EtlUtil;

import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Produces;
import javax.inject.Named;
import java.io.File;

/**
 * 共通項目設定のBean。
 *
 * @author TIS
 */
@Named
@Dependent
public class CommonConfig {

    /** 入力ファイルのベースパス */
    private File inputFileBasePath;

    /** 出力ファイルのベースパス */
    private File outputFileBasePath;

    /** SQLLoaderに使用するコントロールファイルのベースパス */
    private File sqlLoaderControlFileBasePath;

    /** SQLLoaderが出力するファイルのベースパス */
    private File sqlLoaderOutputFileBasePath;

    /**
     * 共通項目の設定を初期化する。
     */
    public void initialize() {
        inputFileBasePath = EtlUtil.verifyAndGetCommonSetting("inputFileBasePath");
        outputFileBasePath = EtlUtil.verifyAndGetCommonSetting("outputFileBasePath");
        sqlLoaderControlFileBasePath = EtlUtil.verifyAndGetCommonSetting("sqlLoaderControlFileBasePath");
        sqlLoaderOutputFileBasePath = EtlUtil.verifyAndGetCommonSetting("sqlLoaderOutputFileBasePath");
    }

    @EtlConfig
    @Produces
    public static CommonConfig createConfig() {
        CommonConfig commonConfig = new CommonConfig();
        commonConfig.initialize();
        return commonConfig;
    }

    /**
     * 入力ファイルへのベースパスを取得する。
     * @return 入力ファイルへのベースパス
     */
    public File getInputFileBasePath() {
        return inputFileBasePath;
    }

    /**
     * 入力ファイルへのベースパスを設定する。
     * @param inputFileBasePath 入力ファイルへのベースパス
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
}
