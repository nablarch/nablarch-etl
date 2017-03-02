package nablarch.etl.generator;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import nablarch.core.db.connection.TransactionManagerConnection;

/**
 * MERGE文のジェネレータのファクトリクラス。
 * <p>
 * {@link DatabaseMetaData#getURL()}を元に、接続さきデータベース製品を判断し、MERGE文のジェネレータを生成する。
 * <p>
 * MERGE文に対応するデータベースは以下の通り。
 * <ul>
 * <li>Oracle</li>
 * <li>H2</li>
 * <li>SQL Server</li>
 * </ul>
 *
 * @author siosio
 */
public final class MergeSqlGeneratorFactory {

    /**
     * 隠蔽コンストラクタ。
     */
    private MergeSqlGeneratorFactory() {
    }

    /**
     * MERGE文を生成するジェネレータを生成する。
     *
     * @param connection データベース接続
     * @return MERGE文のジェネレータ
     */
    public static MergeSqlGenerator create(final TransactionManagerConnection connection) {
        final String url = getUrl(connection);
        if (url.startsWith("jdbc:oracle")) {
            return new OracleMergeSqlGenerator();
        } else if (url.startsWith("jdbc:h2")) {
            return new H2MergeSqlGenerator();
        } else if (url.startsWith("jdbc:sqlserver")) {
            return new SqlServerMergeSqlGenerator();
        } else {
            throw new IllegalStateException("database that can not use merge. database url: " + url);
        }
    }

    /**
     * データベース接続からURLを取得する。
     *
     * @param connection データベース接続
     * @return URL
     */
    private static String getUrl(final TransactionManagerConnection connection) {
        try {
            final DatabaseMetaData metaData = connection.getConnection()
                                                        .getMetaData();
            final String url = metaData.getURL();
            return url == null ? "" : url.toLowerCase();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}