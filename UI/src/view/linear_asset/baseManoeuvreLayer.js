(function(root){
    root.BaseManoeuvreLayer = function(map, roadLayer, manoeuvresCollection, suggestionLabel) {
        var me = this;
        var layerName = 'manoeuvre';
        var indicatorVector = new ol.source.Vector({});
        this.indicatorLayer = new ol.layer.Vector({
            source : indicatorVector
        });
        map.addLayer(me.indicatorLayer);
        me.indicatorLayer.setVisible(false);

        this.manoeuvreStyle = ManoeuvreStyle(roadLayer);

        this.minZoomForContent = zoomlevels.minZoomForAssets;
        Layer.call(this, layerName, roadLayer);
        roadLayer.setLayerSpecificMinContentZoomLevel(layerName, me.minZoomForContent);
        roadLayer.setLayerSpecificStyleProvider(layerName, me.manoeuvreStyle.getDefaultStyle);

        /*
         * ------------------------------------------
         *  Public methods
         * ------------------------------------------
         */

        this.hideLayer = function() {
            me.stop();
            me.hide();
            me.indicatorLayer.setVisible(false);

        };


        /*
         * ------------------------------------------
         *  Utility functions
         * ------------------------------------------
         */

        this.createDashedLineFeatures = function(roadLinks) {
            return _.flatten(_.map(roadLinks, function(roadLink) {
                var points = _.map(roadLink.points, function(point) {
                    return [point.x, point.y];
                });
                var attributes = _.merge({}, roadLink, {
                    type: 'overlay'
                });
                var feature = new ol.Feature(new ol.geom.LineString(points));
                feature.setProperties(attributes);
                return feature;
            }));
        };

        this.createIntermediateFeatures = function(roadLinks) {
            return _.flatten(_.map(roadLinks, function(roadLink) {
                var points = _.map(roadLink.points, function(point) {
                    return [point.x, point.y];
                });
                var attributes = _.merge({}, roadLink, {
                    type: 'intermediate'
                });
                var feature = new ol.Feature(new ol.geom.LineString(points));
                feature.setProperties(attributes);
                return feature;
            }));
        };

        var createMultipleFeatures = function(roadLinks) {
            return _.flatten(_.map(roadLinks, function(roadLink) {
                var points = _.map(roadLink.points, function(point) {
                    return [point.x, point.y];
                });
                var attributes = _.merge({}, roadLink, {
                    type: 'multiple'
                });
                var feature = new ol.Feature(new ol.geom.LineString(points));
                feature.setProperties(attributes);
                return feature;
            }));
        };

        var createSourceDestinationFeatures = function(roadLinks) {
            return _.flatten(_.map(roadLinks, function(roadLink) {
                var points = _.map(roadLink.points, function(point) {
                    return [point.x, point.y];
                });
                var attributes = _.merge({}, roadLink, {
                    type: 'sourceDestination'
                });
                var feature = new ol.Feature(new ol.geom.LineString(points));
                feature.setProperties(attributes);
                return feature;
            }));
        };

        var createSourceSuggestionFeatures = function(roadLinks) {
            return _.flatten(_.map(roadLinks, function(roadLink) {
                var points = _.map(roadLink.points, function(point) {
                    return [point.x, point.y];
                });
                var lineString = new ol.geom.LineString(points);

                var style = suggestionLabel.suggestionStyle(_.head(roadLink.manoeuvres).isSuggested, {x: 0, y: 0});

                var middlePoint = GeometryUtils.calculateMidpointOfLineString(lineString);
                var attributes = _.merge({}, roadLink, {
                    type: 'suggestion'
                });
                var feature = suggestionLabel.createFeature([middlePoint.x, middlePoint.y]);
                feature.setProperties(attributes);
                feature.setStyle(style);

                return feature;
            }));
        };

        var drawDashedLineFeatures = function(roadLinks) {
            var dashedRoadLinks = _.filter(roadLinks, function(roadLink) {
                return !_.isEmpty(roadLink.destinationOfManoeuvres);
            });
            roadLayer.layer.getSource().addFeatures(me.createDashedLineFeatures(dashedRoadLinks));
        };

        var drawIntermediateFeatures = function(roadLinks) {
            var intermediateRoadLinks = _.filter(roadLinks, function(roadLink) {
                return !_.isEmpty(roadLink.intermediateManoeuvres) && _.isEmpty(roadLink.destinationOfManoeuvres) &&
                    _.isEmpty(roadLink.manoeuvreSource);
            });
            roadLayer.layer.getSource().addFeatures(me.createIntermediateFeatures(intermediateRoadLinks));
        };

        var drawMultipleSourceFeatures = function(roadLinks) {
            var multipleSourceRoadLinks = _.filter(roadLinks, function(roadLink) {
                return !_.isEmpty(roadLink.multipleSourceManoeuvres) && _.isEmpty(roadLink.sourceDestinationManoeuvres);
            });
            roadLayer.layer.getSource().addFeatures(createMultipleFeatures(multipleSourceRoadLinks));
        };

        var drawMultipleIntermediateFeatures = function(roadLinks) {
            var multipleIntermediateRoadLinks = _.filter(roadLinks, function(roadLink) {
                return !_.isEmpty(roadLink.multipleIntermediateManoeuvres) && _.isEmpty(roadLink.destinationOfManoeuvres) &&
                    _.isEmpty(roadLink.manoeuvreSource);
            });
            roadLayer.layer.getSource().addFeatures(createMultipleFeatures(multipleIntermediateRoadLinks));
        };

        var drawMultipleDestinationFeatures = function(roadLinks) {
            var multipleDestinationRoadLinks = _.filter(roadLinks, function(roadLink) {
                return !_.isEmpty(roadLink.multipleDestinationManoeuvres) &&
                    _.isEmpty(roadLink.manoeuvreSource);
            });
            roadLayer.layer.getSource().addFeatures(createMultipleFeatures(multipleDestinationRoadLinks));
        };

        var drawSourceDestinationFeatures = function(roadLinks) {
            var sourceDestinationRoadLinks = _.filter(roadLinks, function(roadLink) {
                return !_.isEmpty(roadLink.sourceDestinationManoeuvres);
            });
            roadLayer.layer.getSource().addFeatures(createSourceDestinationFeatures(sourceDestinationRoadLinks));
        };

        var drawSourceSuggestionFeatures = function(roadLinks) {
            var sourceRoadLinks = _.filter(roadLinks, function(roadLink) {
                return !_.isEmpty(roadLink.manoeuvres);
            });
            roadLayer.layer.getSource().addFeatures(createSourceSuggestionFeatures(sourceRoadLinks));
        };

        this.draw = function() {
            var linksWithManoeuvres = manoeuvresCollection.getAll();
            roadLayer.drawRoadLinks(linksWithManoeuvres, zoomlevels.getViewZoom(map));
            drawDashedLineFeatures(linksWithManoeuvres);
            drawIntermediateFeatures(linksWithManoeuvres);
            drawMultipleSourceFeatures(linksWithManoeuvres);
            drawMultipleIntermediateFeatures(linksWithManoeuvres);
            drawMultipleDestinationFeatures(linksWithManoeuvres);
            drawSourceDestinationFeatures(linksWithManoeuvres);
            me.drawOneWaySigns(roadLayer.layer, linksWithManoeuvres);
            drawSourceSuggestionFeatures(linksWithManoeuvres);
        };
    };
})(this);