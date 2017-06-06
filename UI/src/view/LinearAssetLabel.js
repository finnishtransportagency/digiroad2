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
})(this);