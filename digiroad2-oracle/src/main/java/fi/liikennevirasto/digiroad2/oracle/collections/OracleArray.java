package fi.liikennevirasto.digiroad2.oracle.collections;

import oracle.jdbc.OracleConnection;
import oracle.jdbc.OraclePreparedStatement;
import oracle.sql.ARRAY;
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
    private static interface ResultSetToElement<T> {
        T convert(ResultSet resultSet) throws SQLException;
    }

    private static <T> List<T> queryWithIdArray(List ids, Connection connection, String query, ResultSetToElement<T> resultSetToElement) throws SQLException {
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
                   assetLinks.add(resultSetToElement.convert(resultSet));
                }
                return assetLinks;
            } finally {
                resultSet.close();
            }
        } finally {
            statement.close();
        }
    }

    private static class ResultSetToLinearAsset implements ResultSetToElement<Tuple6<Long,Long,Int,Int,Double,Double>> {
        public Tuple6<Long, Long, Int, Int, Double, Double> convert(ResultSet resultSet) throws SQLException {
            long id = resultSet.getLong(1);
            long roadLinkId = resultSet.getLong(2);
            int sideCode = resultSet.getInt(3);
            int limitValue = resultSet.getInt(4);
            double startMeasure = resultSet.getDouble(5);
            double endMeasure = resultSet.getDouble(6);
            return new Tuple6(id, roadLinkId, sideCode, limitValue, startMeasure, endMeasure);
        }
    }

    private static class ResultSetToAdjustedTrafficDirection implements ResultSetToElement<Tuple2<Long, Int>> {
        public Tuple2<Long, Int> convert(ResultSet resultSet) throws SQLException {
            long mmlId = resultSet.getLong(1);
            int trafficDirection = resultSet.getInt(2);
            return new Tuple2(mmlId, trafficDirection);
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
        return queryWithIdArray(ids, connection, query, new ResultSetToLinearAsset());
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
        return queryWithIdArray(ids, connection, query, new ResultSetToLinearAsset());
    }

    public static List<Tuple2<Long, Int>> fetchAdjustedTrafficDirectionsByMMLId(List ids, Connection connection) throws SQLException {
        String query = "SELECT mml_id, traffic_direction FROM ADJUSTED_TRAFFIC_DIRECTION where mml_id IN (SELECT COLUMN_VALUE FROM TABLE(?))";
        return queryWithIdArray(ids, connection, query, new ResultSetToAdjustedTrafficDirection());
    }
}
