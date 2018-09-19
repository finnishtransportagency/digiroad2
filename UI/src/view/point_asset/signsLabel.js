(function(root) {

  root.SignsLabel = function(groupingDistance) {
    AssetLabel.call(this, this.MIN_DISTANCE);
    var me = this;

    this.MIN_DISTANCE = groupingDistance;

    var backgroundStyle = function (sign, counter) {
      return new ol.style.Style({
        image: new ol.style.Icon(({
          src:
            me.getLabelProperty(sign, counter).findImage(),
          anchor : [0.48, 1.75 + (counter)]
        }))
      });
    };

    this.getStickStyle = function () {
      return new ol.style.Style({
        image: new ol.style.Icon(({
          src: 'images/traffic-signs/trafficSignStick.png',
          anchor : [0.5, 1]
        }))
      });
    };

    this.getPropertiesConfiguration = function (counter) {};

    this.getSignType = function (sign) {};

    me.getLabelProperty = function (sign, counter) {

      var labelProperty = _.find(me.getPropertiesConfiguration(counter), function(properties) {
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

    me.textStyle = function (sign) {
      if (!me.getLabelProperty(sign).getValidation())
        return '';
      return me.getLabelProperty(sign).getValue() + me.getLabelProperty(sign).getUnit();
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

    me.getStyle = function (sign, counter) {
      return [backgroundStyle(sign, counter), new ol.style.Style({
        text: new ol.style.Text({
          text: me.textStyle(sign),
          fill: new ol.style.Fill({
            color: '#000000'
          }),
          font: '12px sans-serif',
          offsetX: 0,
          offsetY: me.getLabelProperty(sign, counter).getTextOffset()
        })
      })];
    };

    this.renderFeaturesByPointAssets = function(pointAssets, zoomLevel){
      return me.renderGroupedFeatures(pointAssets, zoomLevel, function(asset){
        return me.getCoordinate(asset);
      });
    };

    this.renderGroupedFeatures = function(assets, zoomLevel, getPoint){
      if(!this.isVisibleZoom(zoomLevel))
        return [];
      var groupedAssets = me.getGroupedFeatures(assets, zoomLevel);
      return _.flatten(_.chain(groupedAssets).map(function(assets){
        return _.map(assets, function(asset, index){
          var value = me.getValue(asset);
          if(value !== undefined){
            var styles = [];
            styles = styles.concat(me.getStickStyle());
            styles = styles.concat(me.getStyle(value, index));
            var feature = me.createFeature(getPoint(asset));
            feature.setStyle(styles);
            feature.setProperties(_.omit(asset, 'geometry'));
            return feature;
          }
        });
      }).filter(function(feature){ return !_.isUndefined(feature); }).value());
    };

    this.createFeature = function(point){
      return new ol.Feature(new ol.geom.Point(point));
    };

    me.getValue = function (asset) {};
  };
})(this);
