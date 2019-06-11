(function(root) {
  root.RoadDamagedByThawLabel = function () {
    AssetLabel.call(this);

    var IMAGE_SIGN_HEIGHT = 33;
    var IMAGE_SIGN_ADJUSTMENT = 15;
    var IMAGE_LABEL_HEIGHT = 58;
    var IMAGE_LABEL_ADJUSTMENT = 43;
    
    this.defaultStyle = function(value) {
      var weightLimitValue = _.find(value.properties, function (value) {return value.publicId === "kelirikko";});
      var validWeightLimitValue = (!_.isUndefined(weightLimitValue) && !_.isEmpty(weightLimitValue.values)) ? weightLimitValue.values[0].value : '';
      
      return createMultiStyles(validWeightLimitValue);
    };

    var createMultiStyles = function (weightLimitValue) {
      var stylePositions = [1,2];
      return _.flatten(_.map(stylePositions, function(position){
        return [backgroundStyle(weightLimitValue, position), textStyle(weightLimitValue, position)];
      }));
    };

    var backgroundStyle = function(value, pos){
      var image = getImage(pos);
      var anchor = pos > 1 ? [IMAGE_SIGN_ADJUSTMENT + 2, (pos * IMAGE_SIGN_HEIGHT) - IMAGE_SIGN_ADJUSTMENT] : [IMAGE_LABEL_ADJUSTMENT, IMAGE_LABEL_HEIGHT - IMAGE_LABEL_ADJUSTMENT];

      if (!isValidValue(value) && (pos > 1))
        image = 'images/warningLabel_red_yellow.png';

      return new ol.style.Style({
        image: new ol.style.Icon(({
          anchor: anchor,
          anchorXUnits: 'pixels',
          anchorYUnits: 'pixels',
          src: image
        }))
      });
    };

    var textStyle = function(value, pos) {
      var textValue;
      if (pos === 1) {
        textValue = 'Kelirikko';
      } else {
        textValue = getTextValue(value);
      }

      return new ol.style.Style({
        text: new ol.style.Text(({
          text: textValue,
          offsetX: 0,
          offsetY:(-pos*IMAGE_SIGN_HEIGHT)+IMAGE_SIGN_HEIGHT,
          textAlign: 'center',
          fill: new ol.style.Fill({
            color: '#000000'
          }),
          font: 'bold 14px sans-serif'
        }))
      });
    };

    var getTextValue = function (value) {
      if (_.isUndefined(value) || !isValidValue(value))
        return '';

      return ''.concat(value/1000, 't');
    };

    var isValidValue = function (value) {
      var valueLength = value.toString().length;
      if (valueLength === 0 || value < 0)
        return false;
      return true;
    };

    this.getValue = function (asset) {
      return asset.value;
    };

    var getImage = function (position) {
      var images = {
        1: 'images/linearLabel_largeText_yellow_red.png',
        2: 'images/mass-limitations/totalWeightLimit.png'
      };
      return images[position];
    };
  };
})(this);

