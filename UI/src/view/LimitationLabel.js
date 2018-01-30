(function(root) {

  root.LimitationLabel = function(){
    AssetLabel.call(this);
    var me = this;
    var IMAGE_HEIGHT = 23;

    var backgroundStyle = function (value, counter) {
      return new ol.style.Style({
        image: new ol.style.Icon(({
          src: me.getImage(value),
          anchor : [0.5, 1.5 + counter]
        }))
      });
    };

    var textStyle = function (value) {
      if (_.isUndefined(value))
        return '';
      return '' + value;
    };

    this.getStyle = function (asset, counter) {
      return [backgroundStyle(asset.typeId, counter),
        new ol.style.Style({
          text: new ol.style.Text({
            text: textStyle(asset.limit),
            fill: new ol.style.Fill({
              color: '#ffffff'
            }),
            font: '14px sans-serif',
            offsetY:  -IMAGE_HEIGHT - (counter * IMAGE_HEIGHT)
          })
        })];
    };

    this.getImage = function () {};

    this.renderFeatures = function (assets, zoomLevel, getPoint) {
      if (!me.isVisibleZoom(zoomLevel))
        return [];

      var sortedAssets = _.sortBy(assets, function(asset) { return asset.typeId; }).reverse();
      return _.chain(sortedAssets).
      map(function(asset, index){
        var style = me.getStyle(asset, index);
        var feature = me.createFeature(getPoint(asset));
        feature.setStyle(style);
        return feature;
      }).
      filter(function(feature){
        return !_.isUndefined(feature);
      }).
      value();
    };
  };
})(this);