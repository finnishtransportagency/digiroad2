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

        this.renderFeaturesByLinearAssets = function(linearAssets, zoomLevel){
            return me.renderFeatures(linearAssets, zoomLevel, function(asset){
                var coordinates = me.getCoordinates(me.getPoints(asset));
                var lineString = new ol.geom.LineString(coordinates);
                return GeometryUtils.calculateMidpointOfLineString(lineString);
            });
        };

        this.renderFeatures = function(assets, zoomLevel, getPoint){
            if(!me.isVisibleZoom(zoomLevel))
                return [];

            return _.chain(assets).
                map(function(asset){
                    var assetValue = me.getValue(asset);
                    if(assetValue !== undefined){
                        var style = me.getStyle(assetValue);
                        var feature = me.createFeature(getPoint(asset));
                        feature.setStyle(style);
                        return feature;
                    }
                }).
                filter(function(feature){ return feature !== undefined; }).
                value();
        };

        this.getMarkerOffset = function(zoomLevel){
            if(me.isVisibleZoom(zoomLevel))
                return [23, 9];
        };

        this.getMarkerAnchor = function(zoomLevel){
            if(me.isVisibleZoom(zoomLevel))
                return [-0.45, 0.15];
        };

        this.isVisibleZoom = function(zoomLevel){
            return zoomLevel >= 12;
        };

        this.getPoints = function(asset){ return asset.points; };

        this.getValue = function(asset){};

        this.getStyle = function(value){};

    };
})(this);
