(function(root) {

    root.LinearAssetLabel = function(){
        AssetLabel.call(this);
        var me = this;

        var backgroundStyle = function (value) {

          var valueLength = value.toString().length;

          if (valueLength > 6 || value < 0) {
            return new ol.style.Style({
              image: new ol.style.Icon(({
                src: 'images/warningLabel.png'
              }))
            });
          }else if (valueLength > 4 && valueLength < 7) {
            return new ol.style.Style({
              image: new ol.style.Icon(({
                src: 'images/linearLabel_background_large.png'
              }))
            });
          }
          return new ol.style.Style({
            image: new ol.style.Icon(({
              src: 'images/linearLabel_background.png'
            }))
          });
        };

        var textStyle = function(value) {
          if (value.toString().length > 6  || value < 0){
            return '';
          }
          return "" + value;
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