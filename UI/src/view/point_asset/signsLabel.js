(function(root) {

  root.SignsLabel = function(groupingDistance) {
    AssetLabel.call(this, this.MIN_DISTANCE);
    var me = this;
    var stickPosition = {x: 0, y: 30 };

    this.MIN_DISTANCE = groupingDistance;

    var backgroundStyle = function (trafficSign, position) {
      return new ol.style.Style({
        image: new ol.style.Icon(({
          src: getLabelProperty(trafficSign).findImage(),
          anchor : [0.48, position.y],
          anchorYUnits: "pixels"
        }))
      });
    };

    this.getStickStyle = function () {
      return new ol.style.Style({
        image: new ol.style.Icon(({
          src: 'images/traffic-signs/trafficSignStick.png',
          anchor : [0.5, stickPosition.y],
          anchorYUnits: "pixels"
        }))
      });
    };

    this.getPropertiesConfiguration = function (counter) {};

    this.getSignType = function (sign) {};

    me.getLabelProperty = function (sign, counter) {

      var labelProperty = _.find(me.getPropertiesConfiguration(), function(properties) {
        return _.includes(properties.signValue, me.getSignType(sign));
      });

      function findImage() {
        return labelProperty && labelProperty.image ? labelProperty.image : 'images/traffic-signs/badValue.png';
      }

      function getTextOffset(){
        return labelProperty && labelProperty.offset ? labelProperty.offset :  -45 - (counter * 35);
      }

      function getValidation(){
        return labelProperty && labelProperty.validation ? labelProperty.validation.call(sign) : false ;
      }

      function getValue(){
        return labelProperty && labelProperty.convertion ? labelProperty.convertion.call(sign) : sign.value;
      }

      function getUnit() {
        return labelProperty && labelProperty.unit ? labelProperty.unit.call(sign) : '';
      }

      return {
        findImage: findImage,
        getTextOffset: getTextOffset,
        getValidation: getValidation,
        getValue : getValue,
        getUnit : getUnit
      };
    };

    var textStyle = function (trafficSign) {
      if (!getLabelProperty(trafficSign).getValidation())
        return '';
      return getLabelProperty(trafficSign).getValue() + getLabelProperty(trafficSign).getAdditionalInfo() + getLabelProperty(trafficSign).getUnit();
    };

    me.addTons = function () {
      return ''.concat('t');
    };

    me.addMeters = function() {
      return ''.concat('m');
    };

    me.convertToTons = function(){
      return this.value / 1000;
    };

    me.convertToMeters = function(){
      return this.value / 100;
    };

    this.getStyle = function (trafficSign, position) {
      return [backgroundStyle(trafficSign, position), new ol.style.Style({
        text: new ol.style.Text({
          text: textStyle(trafficSign),
          fill: new ol.style.Fill({
            color: '#000000'
          }),
          font: '12px sans-serif',
          offsetX: getLabelProperty(trafficSign).getTextOffsetX(),
          offsetY: getLabelProperty(trafficSign).getTextOffsetY() - position.y
        })
      })];
    };

    this.renderFeaturesByPointAssets = function(pointAssets, zoomLevel){
      return me.renderGroupedFeatures(pointAssets, zoomLevel, function(asset){
        return me.getCoordinate(asset);
      });
    };

    this.renderGroupedFeatures = function(assets, zoomLevel, getPoint){ };


    this.createFeature = function(point){
      return new ol.Feature(new ol.geom.Point(point));
    };

    me.getValue = function (asset) {};
  };
})(this);
