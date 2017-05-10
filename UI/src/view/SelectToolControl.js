(function(root) {
    root.SelectToolControl = function(application, layer, map, options) {

        var mapDoubleClickEventKey;
        var enabled = false;
        var initialized = false;
        var activeDragBox = false;

        var settings = _.extend({
            onDragStart: function(){},
            onDragEnd: function(){},
            onSelect: function() {},
            style: function(){},
            enableSelect: function(){ return true; },
            backgroundOpacity: 0.15,
            draggable : true,
            filterGeometry : function(feature){
                return feature.getGeometry() instanceof ol.geom.LineString;
            }
        }, options);

        var dragBoxInteraction = new ol.interaction.DragBox({
            condition: function(event){ return ol.events.condition.platformModifierKeyOnly(event) || activeDragBox; }
        });

        var selectInteraction = new ol.interaction.Select({
            layer: layer,
            condition: function(events){
                return enabled &&(ol.events.condition.doubleClick(events) || ol.events.condition.singleClick(events));
            },
            style: settings.style,
            filter : settings.filterGeometry
        });

        selectInteraction.set('name', layer.get('name'));

        dragBoxInteraction.on('boxstart', settings.onDragStart);
        dragBoxInteraction.on('boxend', function() {
            var extent = dragBoxInteraction.getGeometry().getExtent();
            var selectedFeatures = [];
            layer.getSource().forEachFeatureIntersectingExtent(extent, function (feature) {
                selectedFeatures.push(feature.getProperties());
            });
            settings.onDragEnd(selectedFeatures);
        });

        selectInteraction.on('select',  function(evt){
            if(evt.selected.length > 0 && settings.enableSelect(evt))
                unhighlightLayer();
            else
                highlightLayer();

            settings.onSelect(evt);
        });

        var toggleDragBox = function() {
            if (!application.isReadOnly() && enabled && settings.draggable) {
                destroyDragBoxInteraction();
                map.addInteraction(dragBoxInteraction);
            }
            else{
                if((!settings.draggable && enabled) || application.isReadOnly() )
                    destroyDragBoxInteraction();
            }
        };

        var highlightLayer = function(){
            layer.setOpacity(1);
        };

        var unhighlightLayer = function(){
            layer.setOpacity(settings.backgroundOpacity);
        };

        var activate = function() {
            enabled = true;

            if(!initialized){
                map.addInteraction(selectInteraction);
                initialized = true;
            }
            mapDoubleClickEventKey = map.on('dblclick', function () {
                _.defer(function(){
                    if(selectInteraction.getFeatures().getLength() < 1 && map.getView().getZoom() <= 13 && enabled){
                        map.getView().setZoom(map.getView().getZoom()+1);
                    }
                });
            });
            toggleDragBox();
        };

        var deactivate = function() {
            enabled = false;
            map.unByKey(mapDoubleClickEventKey);
        };

        var activePoligon = function() {
            activeDragBox = true;
        };

        var deactivePoligon = function() {
            activeDragBox = false;
        };

        var clear = function(){
            selectInteraction.getFeatures().clear();
            highlightLayer();
        };

        var removeFeatures = function (match) {
            _.each(selectInteraction.getFeatures().getArray(), function(feature){
                if(match(feature)) {
                    selectInteraction.getFeatures().remove(feature);
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
            });

            if(!highlightLayer)
                unhighlightLayer();
        };

        var destroyDragBoxInteraction = function () {
            _.each(map.getInteractions().getArray(), function (interaction) {
                if(!(interaction instanceof ol.interaction.DragZoom) && (interaction instanceof ol.interaction.DragBox))
                    map.removeInteraction(interaction);
            });
        };

        eventbus.on('application:readOnly', toggleDragBox);

        return {
            getSelectInteraction: function(){ return selectInteraction; },
            getDragBoxInteraction: function(){ return dragBoxInteraction; },
            addSelectionFeatures: addSelectionFeatures,
            addNewFeature : addNewFeature,
            toggleDragBox: toggleDragBox,
            activate: activate,
            deactivate: deactivate,
            activePoligon: activePoligon,
            deactivePoligon: deactivePoligon,
            clear : clear,
            removeFeatures : removeFeatures
        };
    };
})(this);
