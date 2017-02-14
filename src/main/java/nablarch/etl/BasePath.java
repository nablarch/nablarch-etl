package nablarch.etl;

import nablarch.core.util.annotation.Published;

/**
 * ベースパスのキーを定義したEnum。
 *
 * @author TIS
 */
@Published(tag = "architect")
public enum BasePath {
    /** 入力ファイルのベースパス */
    INPUT("nablarch.etl.inputFileBasePath"),
    /** 出力ファイルのベースパス */
    OUTPUT("nablarch.etl.outputFileBasePath"),
    /** SQLLoaderに使用するコントロールファイルのベースパス */
    SQLLOADER_CONTROL("nablarch.etl.sqlLoaderControlFileBasePath"),
    /** SQLLoaderが出力するファイルのベースパス */
    SQLLOADER_OUTPUT("nablarch.etl.sqlLoaderOutputFileBasePath");

    /** ベースパスのキー */
    private final String key;

    /**
     * コンストラクタ。
     * @param key ベースパスのキー
     */
    BasePath(final String key){
        this.key = key;
    }

    /**
     * ベースパスのキーを取得する。
     * @return ベースパスのキー
     */
    public String getKey() {
        return key;
    }
}
