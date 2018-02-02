(function(root) {

  root.WidthLimitLabel = function(){
    AssetLabel.call(this);
    var me = this;
    var IMAGE_HEIGHT = 23;

    var backgroundStyle = function () {
      return new ol.style.Style({
        image: new ol.style.Icon(({
          src: getImage(),
          anchor : [0.5, 1.5]
        }))
      });
    };

    this.getStyle = function (value) {
      return [backgroundStyle(),
        new ol.style.Style({
          text: new ol.style.Text({
            text: textStyle(value),
            fill: new ol.style.Fill({
              color: '#ffffff'
            }),
            font: '14px sans-serif',
            offsetY:  -IMAGE_HEIGHT
          })
        })];
    };

    this.getValue = function(asset) {
      return asset.limit;
    };

    var textStyle = function (value) {
      if (_.isUndefined(value))
        return '';
      return '' + value;
    };


    var getImage = function () {
      return 'images/greenLabeling.png';
    };
  };
})(this);