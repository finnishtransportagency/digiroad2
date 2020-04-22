(function(root) {
  root.LaneModellingLabel = function(){
    LinearAssetLabel.call(this);
    var me = this;

    var backgroundStyle = function () {
      var image = 'images/smallBlueLabeling.png';
      return new ol.style.Style({
        image: new ol.style.Icon(({
          src: image
        }))
      });
    };

    this.defaultStyle = function(value){
      return [backgroundStyle(), new ol.style.Style({
        text : new ol.style.Text({
          text : value,
          fill: new ol.style.Fill({
            color: '#ffffff'
          }),
          font : '12px sans-serif'
        })
      })];
    };

    this.getValue = function (asset) {
      var properties = _.isUndefined(asset.value) ? asset.properties: asset.value.properties;
      return _.head(Property.getPropertyByPublicId(properties, 'lane_code').values).value.toString();
    };

    this.renderFeaturesByLinearAssets = function(linearAssets, zoomLevel){
      return me.renderFeatures(linearAssets, zoomLevel, function(asset){
        var coordinates = me.getCoordinates(me.getPoints(asset));
        var lineString = new ol.geom.LineString(coordinates);
        var value = me.getValue(asset);
        return GeometryUtils.calculateMidpointOfLineString(lineString, (value[1] == '1' ? (value[0] == '1' ? 2 : 3) : 4));
      });
    };

    this.renderFeatures = function (assets, zoomLevel, getPoint) {
      if (!me.isVisibleZoom(zoomLevel))
        return [];

      return [].concat.apply([], _.chain(assets).map(function (asset) {
        var values = me.getValue(asset);
        return _.map(values, function () {
          var style = me.defaultStyle(me.getValue(asset));
          var feature = me.createFeature(getPoint(asset));
          feature.setProperties(_.omit(asset, 'geometry'));
          feature.setStyle(style);
          return feature;
        });
      }).filter(function (feature) {
        return !_.isUndefined(feature);
      }).value());
    };
  };
})(this);
