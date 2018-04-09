(function(root) {

  root.WeightLimitLabel = function(){
    AssetLabel.call(this);
    var me = this;
    var IMAGE_HEIGHT = 23;

    var backgroundStyle = function (value, index) {
      return new ol.style.Style({
        image: new ol.style.Icon(({
          src: getImage(value),
          anchor : [0.5, 1.5 + index]
        }))
      });
    };

    this.getStyle = function(value){
      return createMultiStyles(value);
    };

    var createMultiStyles = function(values){
      var styles = [];
      _.forEach(values, function(value, index){
        styles.push(backgroundStyle(value.typeId, index), textStyle(value, index));
      });
      return styles;
    };


    this.getValue = function(weightAsset) {
      return  _.sortBy(weightAsset.assets, function(asset) { return asset.typeId; });
    };

    var textStyle = function(asset, index) {
      return new ol.style.Style({
        text: new ol.style.Text({
          text: getTextValue(asset.limit),
          fill: new ol.style.Fill({
            color: '#ffffff'
          }),
          font: '14px sans-serif',
          offsetY:   -IMAGE_HEIGHT - (index * IMAGE_HEIGHT)
        })
      });
    };


    var getTextValue = function (value) {
      if (_.isUndefined(value))
        return '';
      return '' + value;
    };

    var getImage = function (typeId) {
      var images = {
        320: 'images/blueLabeling.png'   ,
        330: 'images/greenLabeling.png',
        340: 'images/orangeLabeling.png',
        350: 'images/yellowLabeling.png'
      };
      return images[typeId];
    };
  };
})(this);