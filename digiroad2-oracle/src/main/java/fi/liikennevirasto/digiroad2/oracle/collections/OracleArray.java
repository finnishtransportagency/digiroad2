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

    private static class RowToLinearAsset implements RowToElement<Tuple6<Long,Long,Int,Int,Double,Double>> {
        @Override
        public Tuple6<Long, Long, Int, Int, Double, Double> convert(ResultSet row) throws SQLException {
            long id = row.getLong(1);
            long roadLinkId = row.getLong(2);
            int sideCode = row.getInt(3);
            int limitValue = row.getInt(4);
            double startMeasure = row.getDouble(5);
            double endMeasure = row.getDouble(6);
            return new Tuple6(id, roadLinkId, sideCode, limitValue, startMeasure, endMeasure);
        }
    }

    private static class RowToRoadLinkAdjustment implements RowToElement<Tuple4<Long, Int, DateTime, String>> {
        @Override
        public Tuple4<Long, Int, DateTime, String> convert(ResultSet row) throws SQLException {
            long mmlId = row.getLong(1);
            int value = row.getInt(2);
            DateTime modifiedAt = DateTime.parse(row.getString(3));
            String modifiedBy = row.getString(4);
            return new Tuple4(mmlId, value, modifiedAt, modifiedBy);
        }
    }

    private static class RowToManoeuvre implements RowToElement<Tuple6<Long, Int, Long, Int, DateTime, String>> {
        @Override
        public Tuple6<Long, Int, Long, Int, DateTime, String> convert(ResultSet row) throws SQLException {
            long manoeuvreId = row.getLong(1);
            int type = row.getInt(2);
            long roadLinkId = row.getLong(3);
            int elementType = row.getInt(4);
            DateTime createdAt = DateTime.parse(row.getString(5));
            String createdBy = row.getString(6);
            return new Tuple6(manoeuvreId, type, roadLinkId, elementType, createdAt, createdBy);
        }
    }

    public static List<Tuple6<Long, Long, Int, Int, Double, Double>> fetchAssetLinksByRoadLinkIds(List ids, Connection connection) throws SQLException {
        String query = "SELECT a.id, pos.road_link_id, pos.side_code, e.name_fi as speed_limit, pos.start_measure, pos.end_measure " +
                "FROM ASSET a " +
                "JOIN ASSET_LINK al ON a.id = al.asset_id " +
                "JOIN LRM_POSITION pos ON al.position_id = pos.id " +
                "JOIN PROPERTY p ON a.asset_type_id = p.asset_type_id AND p.public_id = 'rajoitus' " +
                "JOIN SINGLE_CHOICE_VALUE s ON s.asset_id = a.id AND s.property_id = p.id " +
                "JOIN ENUMERATED_VALUE e ON s.enumerated_value_id = e.id " +
                "WHERE a.asset_type_id = 20 AND pos.road_link_id IN (SELECT COLUMN_VALUE FROM TABLE(?))";
        return queryWithIdArray(ids, connection, query, new RowToLinearAsset());
    }

    public static List<Tuple6<Long, Long, Int, Int, Double, Double>> fetchNumericalLimitsByRoadLinkIds(List ids, int assetTypeId, String valuePropertyId, Connection connection) throws SQLException {
        String query = "SELECT a.id, pos.road_link_id, pos.side_code, s.value as total_weight_limit, pos.start_measure, pos.end_measure " +
                "FROM ASSET a " +
                "JOIN ASSET_LINK al ON a.id = al.asset_id " +
                "JOIN LRM_POSITION pos ON al.position_id = pos.id " +
                "JOIN PROPERTY p ON p.public_id = '" + valuePropertyId + "' " +
                "JOIN NUMBER_PROPERTY_VALUE s ON s.asset_id = a.id AND s.property_id = p.id " +
                "WHERE a.asset_type_id = " + String.valueOf(assetTypeId) + " AND pos.road_link_id IN (SELECT COLUMN_VALUE FROM TABLE(?))" +
                "AND (a.valid_to >= sysdate OR a.valid_to is null)";
        return queryWithIdArray(ids, connection, query, new RowToLinearAsset());
    }

    public static List<Tuple6<Long, Int, Long, Int, DateTime, String>> fetchManoeuvresByRoadLinkIds(List ids, Connection connection) throws SQLException {
        String query = "SELECT m.id, m.type, m.road_link_id, m.element_type, to_char(m.created_date, 'YYYY-MM-DD\"T\"HH24:MI:SS'), m.created_by " +
                "FROM MANOEUVRE m " +
                "WHERE m.id in (" +
                "SELECT distinct(k.id) " +
                "FROM MANOEUVRE k " +
                "WHERE k.road_link_id IN (SELECT COLUMN_VALUE FROM TABLE(?)))";

        return queryWithIdArray(ids, connection, query, new RowToManoeuvre());
    }

    public static List<Tuple4<Long, Int, DateTime, String>> fetchAdjustedTrafficDirectionsByMMLId(List ids, Connection connection) throws SQLException {
        String query = "SELECT mml_id, traffic_direction, to_char(modified_date, 'YYYY-MM-DD\"T\"HH24:MI:SS'), modified_by FROM ADJUSTED_TRAFFIC_DIRECTION where mml_id IN (SELECT COLUMN_VALUE FROM TABLE(?))";
        return queryWithIdArray(ids, connection, query, new RowToRoadLinkAdjustment());
    }

    public static List<Tuple4<Long, Int, DateTime, String>> fetchAdjustedFunctionalClassesByMMLId(List ids, Connection connection) throws SQLException {
        String query = "SELECT mml_id, functional_class, to_char(modified_date, 'YYYY-MM-DD\"T\"HH24:MI:SS'), modified_by FROM ADJUSTED_FUNCTIONAL_CLASS where mml_id IN (SELECT COLUMN_VALUE FROM TABLE(?))";
        return queryWithIdArray(ids, connection, query, new RowToRoadLinkAdjustment());
    }

    public static List<Tuple4<Long, Int, DateTime, String>> fetchAdjustedLinkTypesMMLId(List ids, Connection connection) throws SQLException {
        String query = "SELECT mml_id, link_type, to_char(modified_date, 'YYYY-MM-DD\"T\"HH24:MI:SS'), modified_by FROM ADJUSTED_LINK_TYPE where mml_id IN (SELECT COLUMN_VALUE FROM TABLE(?))";
        return queryWithIdArray(ids, connection, query, new RowToRoadLinkAdjustment());
    }
}
