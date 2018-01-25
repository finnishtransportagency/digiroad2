(function(root) {

  root.WeightLimitLabel = function(){
    AssetLabel.call(this);
    var me = this;

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
      return [backgroundStyle(getTypeId(asset), counter),
        new ol.style.Style({
          text: new ol.style.Text({
            text: textStyle(me.getValue(asset)),
            fill: new ol.style.Fill({
              color: '#ffffff'
            }),
            font: '14px sans-serif',
            offsetY: -27
          })
        })];
    };

    this.getImage = function (typeId) {
      var images = {
        //TODO check if the type id 320 is relative to the totalWeightLimit and check if the images are according to the typeID
        320: 'images/bluelabeling.png'   ,
        330: 'images/greenlabeling.png',
        340: 'images/orangelabeling.png',
        350: 'images/yellowlabeling.png'
      };
      return images[typeId];
    };


    // var getTextOffset = function (asset, counter) {
    //   var offsets = { 320: -17 - (counter * 35), 330: -12 - (counter * 35), 340: -20 - (counter * 35), 350: -20 - (counter * 35)};
    //   return offsets[getTypeId(asset)];
    // };

    var getValues = function (asset) {
      //TODO check json format to return values
    };

    this.getValue = function (asset) {
      //TODO check json format to return value
    };

    var getTypeId = function (asset) {
      //TODO check json format to return typeId
    };


    this.renderFeatures = function (assets, zoomLevel, getPoint) {
      if (!me.isVisibleZoom(zoomLevel))
        return [];

      return [].concat.apply([], _.chain(assets).map(function (asset) {
        // var values = getValues(asset);
        var values = [asset];
        return _.map(values, function (asset, index) {
          var style = me.getStyle(asset, index);
          var feature = me.createFeature(getPoint(asset));
          feature.setStyle(style);
          return feature;
        });
      }).filter(function (feature) {
        return !_.isUndefined(feature);
      }).value());
    };
  };
})(this);