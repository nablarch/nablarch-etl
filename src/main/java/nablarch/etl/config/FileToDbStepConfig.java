package nablarch.etl.config;

import nablarch.core.util.annotation.Published;

/**
 * FILEtoDBステップの設定を保持するクラス。
 * 
 * @author Kiyohito Itoh
 */
@Published(tag = "architect")
public class FileToDbStepConfig extends StepConfig {

    /** Beanクラス */
    private Class<?> bean;

    /** ファイル名 */
    private String fileName;

    /**
     * Beanクラスを取得する。
     * @return Beanクラス
     */
    public Class<?> getBean() {
        return bean;
    }

    /**
     * Beanクラスを設定する。
     * @param bean Beanクラス
     */
    public void setBean(Class<?> bean) {
        this.bean = bean;
    }

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
