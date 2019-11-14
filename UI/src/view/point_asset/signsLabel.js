(function(root) {

  root.SignsLabel = function(groupingDistance) {
    AssetLabel.call(this, this.MIN_DISTANCE);
    var me = this;
    me.stickPosition = {x: 0, y: 30 };

    this.MIN_DISTANCE = groupingDistance;

    this.backgroundStyle = function (trafficSign, position) {
      return new ol.style.Style({
        image: new ol.style.Icon(({
          src: me.getLabelProperty(trafficSign).findImage(),
          anchor : [0.48, position.y],
          anchorYUnits: "pixels"
        }))
      });
    };

    this.getStickStyle = function () {
      return new ol.style.Style({
        image: new ol.style.Icon(({
          src: 'images/traffic-signs/trafficSignStick.png',
          anchor : [0.5, me.stickPosition.y],
          anchorYUnits: "pixels"
        }))
      });
    };

    this.getPropertiesConfiguration = function (counter) {};

    this.getSignType = function (sign) {};

    this.getLabel = function(sign){
      return _.find(me.getPropertiesConfiguration(), function(properties) {
        return _.includes(properties.signValue, me.getSignType(sign));
      });
    };

    me.getLabelProperty = function (sign, counter) {

      var labelProperty = me.getLabel(sign);

      function findImage() {
        return labelProperty && labelProperty.image ? labelProperty.image : 'images/traffic-signs/badValue.png';
      }

      function getTextOffsetX(){
        return labelProperty && labelProperty.offsetX ? labelProperty.offsetX :  0;
      }

      function getTextOffsetY(){
        var offsetY =  labelProperty && labelProperty.offsetY ? labelProperty.offsetY :  1;
        return parseInt(''+ getHeight() / 2) + offsetY;
      }

      function getValidation(){
        return labelProperty && labelProperty.validation ? labelProperty.validation.call(sign) : false ;
      }

      function getValue(){
        return labelProperty && labelProperty.convertion ? labelProperty.convertion.call(sign) : sign.value;
      }

      function getAdditionalInfo(){
        return labelProperty && labelProperty.additionalInfo ? labelProperty.additionalInfo.call(sign) : '';
      }

      function getUnit() {
        return labelProperty && labelProperty.unit ? labelProperty.unit.call(sign) : '';
      }

      function getMaxLength() {
        return labelProperty && labelProperty.maxLabelLength ? labelProperty.maxLabelLength : 20;
      }

      function getHeight() {
        return labelProperty && labelProperty.height ? labelProperty.height : 35;
      }

      function getTextColor(){
        return labelProperty && labelProperty.textColor ? labelProperty.textColor :  '#000000';
      }

      return {
        findImage: findImage,
        getTextOffsetX: getTextOffsetX,
        getTextOffsetY: getTextOffsetY,
        getValidation: getValidation,
        getValue : getValue,
        getUnit : getUnit,
        getAdditionalInfo: getAdditionalInfo,
        getMaxLength: getMaxLength,
        getHeight: getHeight,
        getTextColor: getTextColor
      };
    };

    this.textStyle = function (trafficSign) {
      if (!me.getLabelProperty(trafficSign).getValidation())
        return '';
      return me.getLabelProperty(trafficSign).getValue() + me.getLabelProperty(trafficSign).getAdditionalInfo() + me.getLabelProperty(trafficSign).getUnit();
    };

    me.addTons = function () {
      return ''.concat('t');
    };

    me.addMeters = function() {
      return ''.concat(' m');
    };

    me.convertToTons = function(){
      return this.value / 1000;
    };

    me.convertToMeters = function(){
      return this.value / 100;
    };

    this.getStyle = function (trafficSign, position) {
      return [me.backgroundStyle(trafficSign, position), new ol.style.Style({
        text: new ol.style.Text({
          text: me.textStyle(trafficSign),
          fill: new ol.style.Fill({
            color: trafficSign.textColor ?  trafficSign.textColor : me.getLabelProperty(trafficSign).getTextColor()
          }),
          font: '12px sans-serif',
          offsetX: me.getLabelProperty(trafficSign).getTextOffsetX(),
          offsetY: me.getLabelProperty(trafficSign).getTextOffsetY() - position.y
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
