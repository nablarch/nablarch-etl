package nablarch.etl.config;

import nablarch.core.util.annotation.Published;

/**
 * DBtoFILEステップの設定を保持するクラス。
 * 
 * @author Kiyohito Itoh
 */
@Published(tag = "architect")
public class DbToFileStepConfig extends DbInputStepConfig {

    /** ファイル名 */
    private String fileName;

    /**
     * ファイル名を取得する。
     * @return ファイル名
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * ファイル名を設定する。
     * @param fileName ファイル名
     */
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    /**
     * 初期化を行う。
     */
    @Override
    protected void onInitialize() {
        // nop
    }
}
