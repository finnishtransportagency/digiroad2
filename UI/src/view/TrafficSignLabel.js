(function(root) {

  root.TrafficSignLabel = function(){
    AssetLabel.call(this);
    var me = this;

    var propertyText = '';

    var backgroundStyle = function (value) {

      var image = 'images/speedLimitSign.png';

      if(value == 7){
        image = 'images/crossingSign.png';
      }
      if(value == 9){
        image = 'images/warningSign.png';
      }
      if(value > 9 ){
        image = 'images/turningSign.png';
      }

      return new ol.style.Style({
        image: new ol.style.Icon(({
          src: image,
          anchor : [0.5, 1]
        }))
      });
    };

    var textStyle = function(value) {
      if (!correctValue(value))
        return '';
      return "" + value;
    };

    var correctValue = function(value){
      var valueLength = value.toString().length;
      if(value)
        if(valueLength > 3 || value < 0)
          return false;
      return true;
    };

    this.getStyle = function(value){
      return [backgroundStyle(value), new ol.style.Style({
        text : new ol.style.Text({
          text : textStyle(propertyText),
          fill: new ol.style.Fill({
            color: '#000000'
          }),
          font : 'bold 12px sans-serif',
          offsetX: 0,
          offsetY : -45
        })
      })];
    };

    var getProperty = function(asset, publicId){
      return _.first(_.find(asset.propertyData, function(prop){return prop.publicId === publicId;}).values);
    };

    var handleValue = function(asset){
      propertyText = '';
      if(_.isUndefined(getProperty(asset, "trafficSigns_type")))
        return;
      var trafficSignType = parseInt(getProperty(asset, "trafficSigns_type").propertyValue);
        if(trafficSignType < 7 || trafficSignType == 8)
          setProperty(asset);
      return trafficSignType;
    };

    var setProperty = function(asset) {
      var existingValue = getProperty(asset, "trafficSigns_value");
      if(existingValue)
        propertyText = existingValue.propertyValue;
    };

    this.getValue = function(asset){
     return handleValue(asset);
    };
  };

})(this);