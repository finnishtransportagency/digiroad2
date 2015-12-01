package fi.liikennevirasto.digiroad2.oracle.collections;

import oracle.jdbc.OracleConnection;
import oracle.jdbc.OraclePreparedStatement;
import oracle.sql.ARRAY;
import org.joda.time.DateTime;
import scala.Double;
import scala.*;
import scala.Long;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unchecked")
public class OracleArray {
    private static interface RowToElement<T> {
        T convert(ResultSet row) throws SQLException;
    }

    private static <T> List<T> queryWithIdArray(List ids, Connection connection, String query, RowToElement<T> rowToElement) throws SQLException {
        OracleConnection oracleConnection = (OracleConnection) connection;
        ARRAY oracleArray = oracleConnection.createARRAY("ROAD_LINK_VARRAY", ids.toArray());
        PreparedStatement statement = oracleConnection.prepareStatement(query);
        try {
            OraclePreparedStatement oraclePreparedStatement = (OraclePreparedStatement) statement;
            oraclePreparedStatement.setArray(1, oracleArray);
            ResultSet resultSet = oraclePreparedStatement.executeQuery();
            try {
                ArrayList<T> assetLinks = new ArrayList<T>();
                while (resultSet.next()) {
                   assetLinks.add(rowToElement.convert(resultSet));
                }
                return assetLinks;
            } finally {
                resultSet.close();
            }
        } finally {
            statement.close();
        }
    }

    private static class RowToManoeuvreException implements RowToElement<Tuple2<Long, Int>> {
        @Override
        public Tuple2<Long, Int> convert(ResultSet row) throws SQLException {
            long manoeuvreId = row.getLong(1);
            int exceptionType = row.getInt(2);
            return new Tuple2(manoeuvreId, exceptionType);
        }
    }

    private static class RowToRoadLinkData implements RowToElement<Tuple3<Long, Long, Int>>  {
        @Override
        public Tuple3<Long, Long, Int> convert(ResultSet row) throws SQLException {
            long id = row.getLong(1);
            long mmlId = row.getLong(2);
            int administrativeClass = row.getInt(3);
            if(row.wasNull()) { administrativeClass = 99; }
            return new Tuple3(id, mmlId, administrativeClass);
        }
    }

    public static List<Tuple2<Long, Int>> fetchManoeuvreExceptionsByIds(List ids, Connection connection) throws SQLException {
        String query = "SELECT m.manoeuvre_id, m.exception_type " +
                "FROM MANOEUVRE_EXCEPTIONS m " +
                "WHERE m.manoeuvre_id IN (SELECT COLUMN_VALUE FROM TABLE(?))";

        return queryWithIdArray(ids, connection, query, new RowToManoeuvreException());
    }

    public static List<Tuple3<Long, Long, Int>> fetchRoadLinkDataByMmlIds(List ids, Connection connection) throws SQLException {
        String query = "select dr1_id, mml_id, omistaja from tielinkki_ctas  WHERE mml_id IN (SELECT COLUMN_VALUE FROM TABLE(?))";
        return queryWithIdArray(ids, connection, query, new RowToRoadLinkData());
    }
}
