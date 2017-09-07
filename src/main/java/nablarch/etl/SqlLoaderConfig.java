package nablarch.etl;

/**
 * SQL*Loaderユーティリティ用の設定情報を持つクラス。
 *
 * @author siosio
 */
public class SqlLoaderConfig {

    /** ユーザ */
    private String userName;

    /** パスワード */
    private String password;

    /** 接続先データベース名 */
    private String databaseName;

    /**
     * ユーザ名を返す。
     *
     * @return ユーザ名
     */
    public String getUserName() {
        return userName;
    }

    /**
     * ユーザ名を設定する。
     *
     * @param userName ユーザ名
     */
    public void setUserName(final String userName) {
        this.userName = userName;
    }

    /**
     * パスワードを返す。
     *
     * @return パスワード
     */
    public String getPassword() {
        return password;
    }

    /**
     * パスワードを設定する。
     *
     * @param password パスワード
     */
    public void setPassword(final String password) {
        this.password = password;
    }

    /**
     * 接続先データベース名を返す。
     *
     * @return 接続先データベース名
     */
    public String getDatabaseName() {
        return databaseName;
    }

    /**
     * 接続先データベース名を設定する。
     *
     * @param databaseName 接続先データベース名
     */
    public void setDatabaseName(final String databaseName) {
        this.databaseName = databaseName;
    }
}
