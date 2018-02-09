(function(root) {

  root.WidthLimitLabel = function(){
    AssetLabel.call(this, this.MIN_DISTANCE, this.populatedPoints);
    var me = this;
    var IMAGE_HEIGHT = 23;
    this.MIN_DISTANCE = 5;
    this.populatedPoints = [];

    me.renderFeaturesByPointAssets = function(pointAssets, zoomLevel){
      me.clearPoints();
      return me.renderGroupedFeatures(pointAssets, zoomLevel, function(asset){
        return me.getCoordinateForGrouping(asset);
      });
    };

    var backgroundStyle = function (counter) {
      return new ol.style.Style({
        image: new ol.style.Icon(({
          src: getImage(),
          anchor : [0.5, 0.5 + (counter)]
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
          offsetX: 0,
          offsetY: -IMAGE_HEIGHT*counter
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