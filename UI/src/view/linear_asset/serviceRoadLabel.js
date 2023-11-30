(function(root) {

  root.ServiceRoadLabel = function() {
    AssetLabel.call(this);
    var me = this;

    var backgroundStyle = function (value) {
      var imageSrc = validateValue(value);

      return new ol.style.Style({
        image: new ol.style.Icon(({
          src: imageSrc
        }))
      });
    };
    
    this.defaultStyle = function(value){
      return [backgroundStyle(value), new ol.style.Style({
        text : new ol.style.Text({
          text : validateText(value),
          fill: new ol.style.Fill({
            color: '#ffffff'
          }),
          font : '12px sans-serif'
        })
      })];
    };

    this.getValue = function(asset){
      return asset.value;
    };

    var obtainValue = function(value){
      var property = _.find(value.properties, function(val) { return val.publicId === 'huoltotie_tarkistettu'; });
      if(property && !_.isEmpty(property.values))
        return _.head(property.values).value;
      return 0;
    };

    var validateValue = function (value) {
      return (obtainValue(value) == 1) ? 'images/linearLabel_largeText_blue.png' : 'images/linearLabel_largeText.png';
    };

    var validateText = function(value){
      return (obtainValue(value) == 1) ?  'Tarkistettu' : 'Ei tarkistettu';
    };

    this.isVisibleZoom = function(zoomLevel){
      return zoomLevel >= 10;
    };

    this.getSuggestionStyle = function (yPosition) {
      return new ol.style.Style({
        image: new ol.style.Icon(({
          src: 'images/icons/questionMarkerIcon.png',
          anchor : [0.5, yPosition]
        }))
      });
    };

    this.renderFeatures = function (assets, zoomLevel, getPoint) {
      if (!me.isVisibleZoom(zoomLevel))
        return [];

      return [].concat.apply([], _.chain(assets).map(function (asset) {
        var values = me.getValue(asset);
        return _.map(values, function () {
          var style = me.defaultStyle(values);

          if(me.isSuggested(asset)) {
            style = style.concat(me.getSuggestionStyle( 1.5));
          }
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