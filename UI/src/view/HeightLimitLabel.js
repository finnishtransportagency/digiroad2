(function(root) {

  root.HeightLimitLabel = function(groupingDistance){
    AssetLabel.call(this, this.MIN_DISTANCE);
    var me = this;
    var IMAGE_HEIGHT = 23;
    this.MIN_DISTANCE = groupingDistance;

    me.renderFeaturesByPointAssets = function(pointAssets, zoomLevel){
      return me.renderGroupedFeatures(pointAssets, zoomLevel, function(asset){
        return me.getCoordinate(asset);
      });
    };

    var backgroundStyle = function (counter) {
      return new ol.style.Style({
        image: new ol.style.Icon(({
          src: getImage(),
          anchor : [0.5, 1.5 + (counter)]
        }))
      });
    };

    this.getStyle = function (value, counter) {
      return [backgroundStyle(counter),
        new ol.style.Style({
          text: new ol.style.Text({
            text: textStyle(value),
            fill: new ol.style.Fill({
              color: '#ffffff'
            }),
            font: '12px sans-serif',
            offsetY: -IMAGE_HEIGHT*(counter+1)
          })
        })];
    };

    this.getValue = function(asset) {
      return asset.limit;
    };

    var textStyle = function (value) {
      if (_.isUndefined(value))
        return '';
      return '' + value;
    };

    var getImage = function () {
      return 'images/greenLabeling.png';
    };
  };
})(this);