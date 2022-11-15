(function(root) {

  root.SignsLabel = function(groupingDistance) {
    AssetLabel.call(this, this.MIN_DISTANCE);
    var me = this;
    me.stickPosition = {x: 0, y: 30 };

    this.MIN_DISTANCE = groupingDistance;

    this.backgroundStyle = function (trafficSign, position, linkOutOfUse) {
      return new ol.style.Style({
        image: new ol.style.Icon(({
          src: me.getLabelProperty(trafficSign).findImage(),
          anchor : [0.48, position.y],
          anchorYUnits: "pixels",
          opacity: linkOutOfUse ? 0.3 : 1.0
        }))
      });
    };

    this.getStickStyle = function (linkOutOfUse) {
      return new ol.style.Style({
        image: new ol.style.Icon(({
          src: 'images/traffic-signs/trafficSignStick.png',
          anchor : [0.5, me.stickPosition.y],
          anchorYUnits: "pixels",
          opacity: linkOutOfUse ? 0.3 : 1.0
        }))
      });
    };

    this.getSuggestionStyle = function (yPosition) {
      return new ol.style.Style({
        image: new ol.style.Icon(({
          src: 'images/icons/questionMarkerIcon.png',
          anchor : [0.5, yPosition],
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

    me.getLabelProperty = function (sign) {

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

    me.addKilometers = function() {
      return ''.concat(' km');
    };

    me.addMinutes = function() {
      return ''.concat(' min');
    };

    me.convertToSaturdayDatePeriod = function () {
      return ('(') + this.value + (')');
    };

    me.convertToTons = function(){
      return this.value / 1000;
    };

    me.convertToMeters = function(){
      return this.value / 100;
    };

    this.getStyle = function (trafficSign, position, linkOutOfUse) {
      var labelProperty = me.getLabelProperty(trafficSign);
      return [me.backgroundStyle(trafficSign, position, linkOutOfUse), new ol.style.Style({
        text: new ol.style.Text({
          text: me.textStyle(trafficSign),
          fill: new ol.style.Fill({
            color: trafficSign.textColor ?  trafficSign.textColor : labelProperty.getTextColor()
          }),
          font: '12px sans-serif',
          offsetX: labelProperty.getTextOffsetX(),
          offsetY: labelProperty.getTextOffsetY() - position.y
        })
      })];
    };

    this.renderGroupedFeatures = function(assets, zoomLevel, getPoint){ };

    this.createFeature = function(point){
      return new ol.Feature(new ol.geom.Point(point));
    };

    me.getValue = function (asset) {};
  };
})(this);
