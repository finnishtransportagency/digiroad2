(function(root) {
  root.TileMapCollection = function(map, arcgisConfig) {
    var layerConfig = {
      // minResolution: ?,
      // maxResolution: ?,
      visible: false,
      extent: [-548576, 6291456, 1548576, 8388608]
    };

    var sourceConfig = {
      cacheSize: 4096,
      projection: 'EPSG:3067',
      tileSize: [256,256]
    };

    var tileGridConfig = {
      extent: [-548576, 6291456, 1548576, 8388608],
      origin: [-548576, 8388608],
      projection: 'EPSG:3067',
      // maxZoom: 22,
      resolutions: [8192, 4096, 2048, 1024, 512, 256, 128, 64, 32, 16, 8, 4, 2, 1, 0.5]
    };

    var aerialMapConfig = _.merge({}, sourceConfig, {
      url: 'maasto/wmts/1.0.0/ortokuva/default/ETRS-TM35FIN/{z}/{y}/{x}.jpg'
    });

    var backgroundMapConfig = _.merge({}, sourceConfig, {
      url: 'maasto/wmts/1.0.0/taustakartta/default/ETRS-TM35FIN/{z}/{y}/{x}.png'
    });

    var terrainMapConfig = _.merge({}, sourceConfig, {
      url: 'maasto/wmts/1.0.0/maastokartta/default/ETRS-TM35FIN/{z}/{y}/{x}.png'
    });

//    var greyscaleMapConfig = JSON.parse(arcgisConfig);
    // TODO: Take it from actual server, now getting 502 BAD GATEWAY
    var greyscaleMapConfig = {"currentVersion":10.31,"serviceDescription":"Harmaasävykarttapalvelu perustuu Maanmittauslaitoksen avoimeen maastotietokanta-aineistoon. Aineiston visualisoinnissa on keskitytty pelkistämään merkittävästi kohteiden kuvaustekniikkaa ja kaikki kohteet esitetään harmaan eri sävyillä. Pelkistetty tausta korostaa sen päälle tuotavia asiakaskohtaisia aineistoja, joiden visualisoinnissa voidaan käyttää vapaammin eri värejä harmaasävyisen taustan päällä. \n.\n\n\n\nKoordinaattijärjestelmä: TM35FIN (ESPG: 3067)\n\n","mapName":"Layers","description":"","copyrightText":"(c) MML, Esri Finland","supportsDynamicLayers":false,"layers":[{"id":0,"name":"Raja","parentLayerId":-1,"defaultVisibility":true,"subLayerIds":null,"minScale":0,"maxScale":0},{"id":1,"name":"Rakennus","parentLayerId":-1,"defaultVisibility":true,"subLayerIds":null,"minScale":120000,"maxScale":0},{"id":2,"name":"Liikenne1","parentLayerId":-1,"defaultVisibility":true,"subLayerIds":null,"minScale":0,"maxScale":0},{"id":3,"name":"Liikenne2","parentLayerId":-1,"defaultVisibility":true,"subLayerIds":null,"minScale":200000,"maxScale":0},{"id":4,"name":"Liikenne3","parentLayerId":-1,"defaultVisibility":true,"subLayerIds":null,"minScale":80000,"maxScale":0},{"id":5,"name":"Maastokohde1","parentLayerId":-1,"defaultVisibility":true,"subLayerIds":null,"minScale":0,"maxScale":0},{"id":6,"name":"Maastokohde2","parentLayerId":-1,"defaultVisibility":true,"subLayerIds":null,"minScale":200000,"maxScale":0},{"id":7,"name":"Maastokohde N","parentLayerId":-1,"defaultVisibility":true,"subLayerIds":null,"minScale":200000,"maxScale":0},{"id":8,"name":"Suojelualue","parentLayerId":-1,"defaultVisibility":true,"subLayerIds":null,"minScale":120000,"maxScale":0},{"id":9,"name":"Rakennettu alue","parentLayerId":-1,"defaultVisibility":true,"subLayerIds":null,"minScale":200000,"maxScale":0},{"id":10,"name":"Tausta","parentLayerId":-1,"defaultVisibility":true,"subLayerIds":null,"minScale":0,"maxScale":0}],"tables":[],"spatialReference":{"wkid":102139,"latestWkid":3067},"singleFusedMapCache":true,"tileInfo":{"rows":256,"cols":256,"dpi":96,"format":"MIXED","compressionQuality":75,"origin":{"x":-5120900,"y":9998100},"spatialReference":{"wkid":102139,"latestWkid":3067},"lods":[{"level":0,"resolution":2116.670900008467,"scale":8000000},{"level":1,"resolution":1058.3354500042335,"scale":4000000},{"level":2,"resolution":529.1677250021168,"scale":2000000},{"level":3,"resolution":264.5838625010584,"scale":1000000},{"level":4,"resolution":132.2919312505292,"scale":500000},{"level":5,"resolution":66.1459656252646,"scale":250000},{"level":6,"resolution":31.750063500127002,"scale":120000},{"level":7,"resolution":21.16670900008467,"scale":80000},{"level":8,"resolution":15.875031750063501,"scale":60000},{"level":9,"resolution":10.583354500042335,"scale":40000},{"level":10,"resolution":5.291677250021167,"scale":20000},{"level":11,"resolution":2.6458386250105836,"scale":10000},{"level":12,"resolution":1.3229193125052918,"scale":5000}]},"initialExtent":{"xmin":381623.37886772526,"ymin":6671611.055860482,"xmax":388829.3261064397,"ymax":6673774.517133858,"spatialReference":{"wkid":102139,"latestWkid":3067}},"fullExtent":{"xmin":17670.28849999979,"ymin":6560797.8247,"xmax":775438.4706999998,"ymax":7824450.352,"spatialReference":{"wkid":102139,"latestWkid":3067}},"minScale":8000000,"maxScale":5000,"units":"esriMeters","supportedImageFormatTypes":"PNG32,PNG24,PNG,JPG,DIB,TIFF,EMF,PS,PDF,GIF,SVG,SVGZ,BMP","documentInfo":{"Title":"Harmaasavy","Author":"Janne Saarikko","Comments":"Harmaasävyinen taustakartta","Subject":"","Category":"","AntialiasingMode":"Normal","TextAntialiasingMode":"Force","Keywords":"grayscale,harmaasävy,taustakartta. Esri Finland,aineistot.esri.fi"},"capabilities":"Map,Query,Data","supportedQueryFormats":"JSON, AMF","exportTilesAllowed":false,"maxRecordCount":1000,"maxImageHeight":4096,"maxImageWidth":4096,"supportedExtensions":""};
    //
    // var restrictedExtent = new OpenLayers.Bounds(
    //   greyscaleMapConfig.initialExtent.xmin,
    //   greyscaleMapConfig.initialExtent.ymin,
    //   greyscaleMapConfig.initialExtent.xmax,
    //   greyscaleMapConfig.initialExtent.ymax
    // );
    // var layerMaxExtent = new OpenLayers.Bounds(
    //   greyscaleMapConfig.fullExtent.xmin,
    //   greyscaleMapConfig.fullExtent.ymin,
    //   greyscaleMapConfig.fullExtent.xmax,
    //   greyscaleMapConfig.fullExtent.ymax
    // );
    //
    // var resolutions = [];
    // for (var i=0; i<greyscaleMapConfig.tileInfo.lods.length; i++) {
    //   resolutions.push(greyscaleMapConfig.tileInfo.lods[i].resolution);
    // }
    //
    // var greyscaleLayer = new OpenLayers.Layer.ArcGISCache( "AGSCache",
    //   "arcgis/rest/services/Taustakartat/Harmaasavy/MapServer", {
    //     isBaseLayer: true,
    //
    //     //From greyscaleMapConfig above
    //     resolutions: resolutions,
    //     tileSize: new OpenLayers.Size(greyscaleMapConfig.tileInfo.cols, greyscaleMapConfig.tileInfo.rows),
    //     tileOrigin: new OpenLayers.LonLat(greyscaleMapConfig.tileInfo.origin.x , greyscaleMapConfig.tileInfo.origin.y),
    //     maxExtent: layerMaxExtent,
    //     restrictedExtent: restrictedExtent,
    //     projection: 'EPSG:' + greyscaleMapConfig.spatialReference.wkid,
    //     layerInfo: greyscaleMapConfig
    //   });
    //
    var greyscaleLayer = new ol.layer.Tile(_.merge({
      source: new ol.source.XYZ(_.merge({
        url: "arcgis/rest/services/Taustakartat/Harmaasavy/MapServer/tile/{z}/{y}/{x}",
        projection: 'EPSG:3067',
        tileGrid: new ol.tilegrid.TileGrid(_.merge({origin: [greyscaleMapConfig.tileInfo.origin.x, greyscaleMapConfig.tileInfo.origin.y]}, tileGridConfig))
      }, arcgisConfig))
    }, layerConfig));

    var aerialMapLayer = new ol.layer.Tile(_.merge({
      source: new ol.source.XYZ(_.merge({
        tileGrid: new ol.tilegrid.TileGrid(tileGridConfig)
      }, aerialMapConfig))
    }, layerConfig));

    var backgroundMapLayer = new ol.layer.Tile(_.merge({
      source: new ol.source.XYZ(_.merge({
        tileGrid: new ol.tilegrid.TileGrid(tileGridConfig)
      }, backgroundMapConfig))
    }, layerConfig));

    var terrainMapLayer = new ol.layer.Tile(_.merge({
      source: new ol.source.XYZ(_.merge({
        tileGrid: new ol.tilegrid.TileGrid(tileGridConfig)
      }, terrainMapConfig))
    }, layerConfig));
    var tileMapLayers = {
      background: backgroundMapLayer,
      greyscale: greyscaleLayer,
      aerial: aerialMapLayer,
      terrain: terrainMapLayer
    };

    var selectMap = function(tileMap) {
      _.forEach(tileMapLayers, function(layer, key) {
        if (key === tileMap) {
          layer.setVisible(true);
        } else {
          layer.setVisible(false);
        }
      });
    };

    selectMap('background');
    eventbus.on('tileMap:selected', selectMap);

    return {
      layers: [backgroundMapLayer, aerialMapLayer, terrainMapLayer, greyscaleLayer]
    };
  };
})(this);