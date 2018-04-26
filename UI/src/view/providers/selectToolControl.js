(function(root) {
    root.SelectToolControl = function(application, layer, map, isMultipleLinkSelectionAllowed, options) {

        var mapDoubleClickEventKey;
        var enabled = false;
        var initialized = false;
        var isPolygonActive = false;
        var isRectangleActive = false;
        var multiSelectInitialized = false;
        var selectedFeatures = [];

        var settings = _.extend({
            onDragStart: function(){},
            onInteractionEnd: function(){},
            onSelect: function() {},
            onMultipleSelect: function() {},
            style: function(){},
            enableSelect: function(){ return true; },
            enableBoxSelect: function(){ return false; },
            backgroundOpacity: 0.15,
            draggable : true,
            filterGeometry : function(feature){
                return feature.getGeometry() instanceof ol.geom.LineString;
            },
            layers: []
        }, options);

        var dragBoxInteraction = new ol.interaction.DragBox({
            condition: function(event){ return ol.events.condition.platformModifierKeyOnly(event) && settings.enableBoxSelect(); }
        });

        var drawInteraction = new ol.interaction.Draw({
            condition: function(event){ return ol.events.condition.noModifierKeys(event) || isPolygonActive; },
            type: ('Polygon'),
            style: drawStyle()
        });

        var drawSquare = new ol.interaction.Draw({
            condition: function(event){ return ol.events.condition.noModifierKeys(event) || isRectangleActive; },
            type: ('Circle'),
            style: drawStyle(),
            geometryFunction: ol.interaction.Draw.createBox()
        });

        var selectInteraction = new ol.interaction.Select({
            layers: [layer],
            condition: function(events){
                return !isPolygonActive && !isRectangleActive && enabled && !ol.events.condition.platformModifierKeyOnly(events) && (ol.events.condition.doubleClick(events) || ol.events.condition.singleClick(events));
            },
            style: settings.style,
            filter : settings.filterGeometry
        });

        selectInteraction.set('name', layer.get('name'));

        dragBoxInteraction.on('boxstart', settings.onDragStart);
        dragBoxInteraction.on('boxend', function() {
            var extent = dragBoxInteraction.getGeometry().getExtent();
            interactionEnd(extent);
        });

        var multiSelectInteraction = new ol.interaction.Select({
            layers: [layer],
            condition: function (events) {
                return enabled && ol.events.condition.click(events) && isMultipleLinkSelectionAllowed && ol.events.condition.platformModifierKeyOnly(events) && !application.isReadOnly();
            },
            toggleCondition: function (events) {
                return true;
            },
            style: settings.style,
            filter : settings.filterGeometry
        });

        multiSelectInteraction.set('name', layer.get('name'));

        function interactionEnd(extent) {
            selectedFeatures = [];
            layer.getSource().forEachFeatureIntersectingExtent(extent, function (feature) {
                selectedFeatures.push(feature.getProperties());
            });
            settings.onInteractionEnd(selectedFeatures);
        }

        drawSquare.on('drawend', function(evt) {
            evt.preventDefault();
            var extent = evt.feature.getGeometry().getExtent();
            interactionEnd(extent);
        });

        drawInteraction.on('drawend', function(evt){
            evt.preventDefault();
            var polygonGeometry = evt.feature.getGeometry();
            var features =  layer.getSource().getFeatures();
            var selected = _.filter(features, function(feature) {
                return _.some(feature.getGeometry().getCoordinates(), function(coordinate) {
                    return polygonGeometry.intersectsCoordinate(coordinate);
                });
              });
            var selectedProperties = _.map(selected, function(select) { return select.getProperties(); });
            settings.onInteractionEnd(selectedProperties);
        });

        selectInteraction.on('select',  function(evt){
            if(evt.selected.length > 0 && settings.enableSelect(evt))
                unhighlightLayer();
            else
                highlightLayer();

            settings.onSelect(evt);
        });

        multiSelectInteraction.on('select', function (evt) {
            if (evt.selected.length > 0 && settings.enableSelect(evt) && _.isUndefined(evt.selected[0].getProperties().value)) {
                selectedFeatures.push(evt.selected[0].getProperties());
                multiSelectInitialized = true;
                unhighlightLayer();
            }
            else if (evt.deselected.length > 0) {
                selectedFeatures = _.filter(selectedFeatures, function (sf) {
                    return sf.linkId !== evt.deselected[0].getProperties().linkId;
                });
            }

            settings.onMultipleSelect(evt);
        });

        $(window).keyup(function (e) {
            if (e.keyCode === 17 && multiSelectInitialized && enabled) {
                clear();
                multiSelectInitialized = false;
                settings.onInteractionEnd(selectedFeatures);
                selectedFeatures = [];
            }
        });

        var toggleDragBox = function() {
          if (!application.isReadOnly() && enabled && settings.draggable && settings.enableBoxSelect()) {
            destroyDragBoxInteraction();
            map.addInteraction(dragBoxInteraction);
          } else {
            if ((!settings.draggable && enabled) || application.isReadOnly() || !settings.enableBoxSelect())
              destroyDragBoxInteraction();
          }
        };


        var highlightLayer = function(){
          layer.setOpacity(1);

          _.each(settings.layers, function(cLayer){
            if(cLayer.setOpacity)
              cLayer.setOpacity(1);

            if(cLayer.highLightLayer)
              cLayer.highLightLayer();
          });
        };

        var unhighlightLayer = function(){
          layer.setOpacity(settings.backgroundOpacity);

          _.each(settings.layers, function(cLayer){
            if(cLayer.setOpacity)
              cLayer.setOpacity(settings.backgroundOpacity);
            if(cLayer.unHighLightLayer)
              cLayer.unHighLightLayer();
          });
        };

        var activate = function() {
            enabled = true;

            if(!initialized){
                map.addInteraction(selectInteraction);
                map.addInteraction(multiSelectInteraction);
                initialized = true;
            }
            mapDoubleClickEventKey = map.on('dblclick', function () {
                _.defer(function(){
                    if(selectInteraction.getFeatures().getLength() < 1 && zoomlevels.getViewZoom(map) <= 13 && enabled){
                        map.getView().setZoom(zoomlevels.getViewZoom(map)+1);
                    }
                });
            });
            toggleDragBox();
        };

        var deactivate = function() {
            enabled = false;
            isPolygonActive = false;
            isRectangleActive = false;
            map.removeInteraction(drawSquare);
            map.removeInteraction(drawInteraction);
            map.unByKey(mapDoubleClickEventKey);

        };

        var activePolygon = function(){
            isPolygonActive = true;
            isRectangleActive = false;
            map.removeInteraction(drawSquare);
            map.addInteraction(selectInteraction);
            map.addInteraction(drawInteraction);
        };

        var activeRectangle = function(){
            isRectangleActive = true;
            isPolygonActive = false;
            map.removeInteraction(drawInteraction);
            map.addInteraction(selectInteraction);
            map.addInteraction(drawSquare);
        };

        var deactivateDraw = function () {
            isPolygonActive = false;
            isRectangleActive = false;
            map.removeInteraction(drawSquare);
            map.removeInteraction(drawInteraction);
        };

        var clear = function(){
            selectInteraction.getFeatures().clear();
            multiSelectInteraction.getFeatures().clear();
            highlightLayer();
        };

        var removeFeatures = function (match) {
            var selectInteractionFeatures = selectInteraction.getFeatures().getArray();
            var multiSelectInteractionFeatures = multiSelectInteraction.getFeatures().getArray();
            var featuresToRemove = selectInteractionFeatures.concat(multiSelectInteractionFeatures);

            _.each(featuresToRemove, function (feature) {
                if (match(feature)) {
                    selectInteraction.getFeatures().remove(feature);
                    multiSelectInteraction.getFeatures().remove(feature);
                }
            });
        };

        var addSelectionFeatures = function(features){
            clear();
            addNewFeature(features);
        };

        var addNewFeature = function (features, highlightLayer) {
            _.each(features, function(feature){
                selectInteraction.getFeatures().push(feature);
                multiSelectInteraction.getFeatures().push(feature);
            });

            if(!highlightLayer)
                unhighlightLayer();
        };

        var destroyDragBoxInteraction = function () {
            _.each(map.getInteractions().getArray(), function (interaction) {
                if(!(interaction instanceof ol.interaction.DragZoom) && (interaction instanceof ol.interaction.DragBox) || (interaction instanceof ol.interaction.Draw))
                    map.removeInteraction(interaction);
            });
        };

        function drawStyle() {
            return new ol.style.Style({
                fill: new ol.style.Fill({
                    color: 'rgba(255, 255, 255, 0.5)'
                }),
                stroke: new ol.style.Stroke({
                    color: 'red',
                    width: 2
                }),
                image: new ol.style.Circle({
                    radius: 7,
                    fill: new ol.style.Fill({
                        color: 'red'
                    })
                })
            });
        }

        eventbus.on('application:readOnly', toggleDragBox);

        return {
            getSelectInteraction: function(){ return selectInteraction; },
            addSelectionFeatures: addSelectionFeatures,
            addNewFeature : addNewFeature,
            toggleDragBox: toggleDragBox,
            activate: activate,
            deactivate: deactivate,
            activePolygon: activePolygon,
            activeRectangle: activeRectangle,
            clear : clear,
            removeFeatures : removeFeatures,
            deactivateDraw: deactivateDraw
        };
    };
})(this);
