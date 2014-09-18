(function(root) {
  root.TileMapCollection = function(map) {
    var aerialMapConfig = {
      name: 'layer_24',
      tileSize: new OpenLayers.Size(256, 256),
      buffer: 0,
      requestEncoding: 'REST',
      url: 'maasto/wmts/1.0.0/ortokuva/default/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}.jpg',
      layer: 'aerialmap',
      matrixSet: 'ETRS-TM35FIN',
      style: 'default',
      format: 'image/jpeg',
      tileOrigin: new OpenLayers.LonLat(-548576, 8388608),
      matrixIds: [
        { identifier: '0', scaleDenominator: 29257142.85714286 },
        { identifier: '1', scaleDenominator: 14628571.42857143 },
        { identifier: '2', scaleDenominator: 7314285.714285715 },
        { identifier: '3', scaleDenominator: 3657142.8571428573 },
        { identifier: '4', scaleDenominator: 1828571.4285714286 },
        { identifier: '5', scaleDenominator: 914285.7142857143 },
        { identifier: '6', scaleDenominator: 457142.85714285716 },
        { identifier: '7', scaleDenominator: 228571.42857142858 },
        { identifier: '8', scaleDenominator: 114285.71428571429 },
        { identifier: '9', scaleDenominator: 57142.857142857145 },
        { identifier: '10', scaleDenominator: 28571.428571428572 },
        { identifier: '11', scaleDenominator: 14285.714285714286 },
        { identifier: '12', scaleDenominator: 7142.857142857143 },
        { identifier: '13', scaleDenominator: 3571.4285714285716 },
        { identifier: '14', scaleDenominator: 1785.7142857142858 }
      ]
    };

    var backgroundMapConfig = {
      name: 'layer_base_35',
      tileSize: new OpenLayers.Size(256, 256),
      buffer: 0,
      requestEncoding: 'REST',
      url: 'maasto/wmts/1.0.0/taustakartta/default/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}.png',
      layer: 'taustakartta',
      matrixSet: 'ETRS-TM35FIN',
      style: 'default',
      format: 'image/png',
      tileOrigin: new OpenLayers.LonLat(-548576, 8388608),
      matrixIds: [
        { identifier: '0', scaleDenominator: 29257142.85714286 },
        { identifier: '1', scaleDenominator: 14628571.42857143 },
        { identifier: '2', scaleDenominator: 7314285.714285715 },
        { identifier: '3', scaleDenominator: 3657142.8571428573 },
        { identifier: '4', scaleDenominator: 1828571.4285714286 },
        { identifier: '5', scaleDenominator: 914285.7142857143 },
        { identifier: '6', scaleDenominator: 457142.85714285716 },
        { identifier: '7', scaleDenominator: 228571.42857142858 },
        { identifier: '8', scaleDenominator: 114285.71428571429 },
        { identifier: '9', scaleDenominator: 57142.857142857145 },
        { identifier: '10', scaleDenominator: 28571.428571428572 },
        { identifier: '11', scaleDenominator: 14285.714285714286 },
        { identifier: '12', scaleDenominator: 7142.857142857143 },
        { identifier: '13', scaleDenominator: 3571.4285714285716 },
        { identifier: '14', scaleDenominator: 1785.7142857142858 }
      ]
    };

    var backgroundMapLayer = new OpenLayers.Layer.WMTS(backgroundMapConfig);
    var aerialMapLayer = new OpenLayers.Layer.WMTS(aerialMapConfig);
    var tileMapLayers = {
      background: backgroundMapLayer,
      aerial: aerialMapLayer
    };
    map.addLayers([backgroundMapLayer, aerialMapLayer]);

    backgroundMapLayer.setVisibility(true);
    aerialMapLayer.setVisibility(false);
    map.setBaseLayer(backgroundMapLayer);

    eventbus.on('tileMap:selected', function(tileMap) {
      _.forEach(tileMapLayers, function(layer, key) {
        if (key === tileMap) {
          layer.setVisibility(true);
          map.setBaseLayer(layer);
        } else {
          layer.setVisibility(false);
        }
      });
    });
  };
})(this);