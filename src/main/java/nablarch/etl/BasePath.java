package nablarch.etl;

import nablarch.core.util.annotation.Published;

/**
 * ベースパスのキーを定義したEnum。
 *
 * @author TIS
 */
@Published(tag = "architect")
public enum BasePath {
    /** ベースパスのデフォルト値 */
    DEFAULT(""),
    /** 入力ファイルのベースパス */
    INPUT("inputFileBasePath"),
    /** 出力ファイルのベースパス */
    OUTPUT("outputFileBasePath"),
    /** SQLLoaderに使用するコントロールファイルのベースパス */
    SQLLOADER_CONTROL("sqlLoaderControlFileBasePath"),
    /** SQLLoaderが出力するファイルのベースパス */
    SQLLOADER_OUTPUT("sqlLoaderOutputFileBasePath");

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
