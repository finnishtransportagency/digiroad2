(function(root) {
    root.AssetLabel = function() {
        var me = this;

        this.getCoordinates = function(points){
            return _.map(points, function(point) {
                return [point.x, point.y];
            });
        };

        this.getCoordinate = function(point){
            return [point.x, point.y];
        };

        this.createFeature = function(point){
            return new ol.Feature(new ol.geom.Point(me.getCoordinate(point)));
        };

        this.renderFeaturesByLinearAssets = function(linearAssets){
            return me.renderFeatures(linearAssets, function(asset){
                var coordinates = me.getCoordinates(me.getPoints(asset));
                var lineString = new ol.geom.LineString(coordinates);
                return GeometryUtils.calculateMidpointOfLineString(lineString);
            });
        };

        this.renderFeatures = function(assets, getPoint){
            return _.chain(assets).
                map(function(asset){
                    var assetValue = me.getValue(asset);
                    if(assetValue){
                        var style = me.getStyle(assetValue);
                        var feature = me.createFeature(getPoint(asset));
                        feature.setStyle(style);
                        return feature;
                    }
                }).
                filter(function(feature){ return feature != undefined; }).
                value();
        };

        this.getPoints = function(asset){ return asset.points; };

        this.getValue = function(asset){};

        this.getStyle = function(value){};

    };
})(this);
