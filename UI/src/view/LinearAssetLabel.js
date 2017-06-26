(function(root) {

    root.LinearAssetLabel = function(){
        AssetLabel.call(this);
        var me = this;

        var backgroundStyle = new ol.style.Style({
            image: new ol.style.Icon(({
                src: 'images/linearLabel_background.png'
            }))
        });

        this.getStyle = function(value){
            return [backgroundStyle, new ol.style.Style({
                text : new ol.style.Text({
                    text : ""+value,
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

    root.LinearAssetLabelMultiValues = function(){

        AssetLabel.call(this);

        var me = this;
        var IMAGE_HEIGHT = 27;

        this.getStyle = function(value){
          return createMultiStyles(value);
        };

        var createMultiStyles = function(values){
          var i = 0;
          var splitValues = values.replace(/\s/g,'').split(/[\n,]+/);
          var styles = [];
          _.forEach(splitValues, function(value){
            i++;
            styles.push(backgroundStyle(value, i), textStyle(value, i));
          });
          return styles;
        };

        var backgroundStyle = function(value, i){
          var image = 'images/linearLabel_background.png';
          if(!correctValues(value))
            image = 'images/warningLabel.png';

          return new ol.style.Style({
            image: new ol.style.Icon(({
              anchor: [17, (i * IMAGE_HEIGHT) - 15],
              anchorXUnits: 'pixels',
              anchorYUnits: 'pixels',
              src: image
            }))
          });
        };

        var textStyle = function(value, i) {

          return new ol.style.Style({
            text: new ol.style.Text(({
              text: getTextValue(value),
              offsetX: 0,
              offsetY: (-i*IMAGE_HEIGHT)+IMAGE_HEIGHT,
              textAlign: 'center',
              fill: new ol.style.Fill({
                color: '#ffffff'
              }),
              font : '12px sans-serif'
            }))
          });
        };

        var getTextValue = function(value) {
          if(!correctValues(value))
            return '';
          return "" + value;
        };

        var correctValues = function(value){
          var valueLength = value.toString().length;
          if(value)
            if(valueLength > 3 || !value.match(/^[0-9|Ee]/))
              return false;
          return true;
        };

        this.getValue = function(asset){
            return asset.value;
        };

    };
})(this);