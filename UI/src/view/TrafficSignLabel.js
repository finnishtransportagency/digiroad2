(function(root) {

  root.TrafficSignLabel = function(){
    AssetLabel.call(this);
    var me = this;

    var propertyText = '';

    var backgroundStyle = function (value) {

      return new ol.style.Style({
        image: new ol.style.Icon(({
          src: getImage(value),
          anchor : [0.5, 1]
        }))
      });
    };

    var getImage = function(value) {
      var images = {
        'images/speedLimitSign.png':  {signValue:[1, 2, 3, 4, 5, 6, 8]},
        'images/crossingSign.png':    {signValue:[7]},
        'images/warningSign.png':     {signValue:[9]},
        'images/turningSign.png':     {signValue:[10, 11, 12]}
      };
      return _.findKey(images, function(image) {
        return _.contains(image.signValue, value);
      });
    };

    var textStyle = function(value) {
      if (!correctValue(value))
        return '';
      return "" + value;
    };

    var correctValue = function(value){
      var valueLength = value.toString().length;
      if(!value || (valueLength > 3 || value < 0))
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