(function(root) {

    root.LinearAssetLabel = function(){
        AssetLabel.call(this);
        var me = this;

        var backgroundStyle = function (value) {

          var valueLength = value.toString().length;
          var image = 'images/linearLabel_background.png';

          if (!correctValue(value)) {
            image = 'images/warningLabel.png';
          }else if (valueLength > 4 && valueLength < 7) {
            image = 'images/linearLabel_background_large.png';
          }

          return new ol.style.Style({
            image: new ol.style.Icon(({
              src: image
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
            if(valueLength > 6 || value < 0)
              return false;
            return true;
        };

        this.getStyle = function(value){
          return [backgroundStyle(value), new ol.style.Style({
            text : new ol.style.Text({
              text : textStyle(value),
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

    };
})(this);