package nablarch.etl;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import nablarch.common.dao.DatabaseUtil;
import nablarch.core.db.connection.DbConnectionContext;
import nablarch.core.db.connection.TransactionManagerConnection;
import nablarch.core.util.StringUtil;
import nablarch.core.util.annotation.Published;
import nablarch.etl.config.DbToDbStepConfig;

/**
 * ETL機能をサポートするユーティリティクラス。
 *
 * @author Hisaaki Shioiri
 */
@Published(tag = "architect")
public final class EtlUtil {

    /** 隠蔽コンストラクタ */
    private EtlUtil() {
    }

    /**
     * テーブルが持つカラムの名前リストを取得する。
     *
     * @param tableName テーブル名
     * @return カラム名リスト
     * @throws {@link RuntimeException} データベース関連の例外が発生した場合
     */
    public static List<String> getAllColumns(final String tableName) {
        final String convertedTableName = DatabaseUtil.convertIdentifiers(tableName);

        final TransactionManagerConnection connection = DbConnectionContext.getTransactionManagerConnection();

        try {
            final DatabaseMetaData metaData = getMetaData(connection);
            final ResultSet columns = metaData.getColumns(null, null, convertedTableName, null);
            try {
                return toColumnNameList(columns);
            } finally {
                columns.close();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * {@link ResultSet}をカラム名リストに変換する。
     *
     * @param rs {@link ResultSet}
     * @return カラム名のリスト
     * @throws {@link SQLException} データベース関連の例外
     */
    private static List<String> toColumnNameList(final ResultSet rs) throws SQLException {
        final Map<Integer, String> columnNames = new TreeMap<Integer, String>();
        while (rs.next()) {
            columnNames.put(rs.getInt("ORDINAL_POSITION"), rs.getString("COLUMN_NAME"));
        }
        return new ArrayList<String>(columnNames.values());
    }

    /**
     * {@link DatabaseMetaData}を取得する。
     *
     * @param connection データベース接続
     * @return {@link DatabaseMetaData}
     * @throws {@link SQLException} データベースに関する例外
     */
    private static DatabaseMetaData getMetaData(
            final TransactionManagerConnection connection) throws SQLException {
        return connection.getConnection()
                .getMetaData();
    }

    /**
     * 必須の設定項目を検証する。
     * <p/>
     * 値がnullの場合は、{@link IllegalArgumentException}を送出する。
     * 
     * @param jobId ジョブID
     * @param stepId ステップID
     * @param key キー
     * @param value 値
     * @throws {@link IllegalArgumentException} 値がnullの場合
     */
    public static void verifyRequired(
            final String jobId, final String stepId, final String key, final Object value) {
        if (value == null) {
            throw new IllegalArgumentException(
                String.format(
                    "%s is required. jobId = [%s], stepId = [%s]",
                    key, jobId, stepId));
        }
    }

    /**
     * SQL文に範囲を指定する2つのINパラメータが含まれていることを検証する。
     * <p/>
     * 含まれていない場合は、{@link IllegalArgumentException}を送出する。
     * @param config {@link DbToDbStepConfig}
     * @throws {@link IllegalArgumentException} SQL文に2つのINパラメータが含まれていなかった場合
     */
    public static void verifySqlRangeParameter(final DbToDbStepConfig config) {
        final String sql = config.getSql();
        if (StringUtil.isNullOrEmpty(sql)) {
            return;
        }
        final int count = sql.length() - sql.replace("?", "").length();
        if (count != 2) {
            throw new IllegalArgumentException(
                    "sql is invalid. "
                            + "please include a range of data on the condition of the sql statement "
                            + "using the two input parameters. "
                            + "ex) \"where line_number between ? and ?\" "
                            + "sqlId = [" + config.getSqlId() + "]");
        }
    }
}
