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
        var IMAGE_HEIGHT = 17;
        var IMAGE_WIDTH = 28;
        var IMAGE_MARGIN = 2;
        var IMAGE_PADDING = 4;
        var STICK_HEIGHT = 15;
        var NATIONAL_ID_WIDTH = 45;
        var EMPTY_IMAGE_TYPE = '99';
        var styleScale = 1;
        var imgMargin = 0;
        var groupOffset = 0;



        AssetLabel.call(this);
        var me = this;

        var i = 0;
        //return _.map(_isEmpty())
        //TODO Use something similar to code existant in MassTransitMarkesr.js, function createStopTypeStyles

        var backgroundStyle = new ol.style.Style({
            image: new ol.style.Icon(({
                anchor: [20,15],
                anchorXUnits: 'pixels',
                anchorYUnits: 'pixels',
                src: 'images/linearLabel_background.png',
                scale: styleScale
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
})(this);