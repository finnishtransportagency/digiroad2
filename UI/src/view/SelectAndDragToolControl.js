(function(root) {
    root.SelectAndDragToolControl = function(application, layer, map, options) {

        var mapDoubleClickEventKey;
        var enabled = false;
        var initialized = false;

        var settings = _.extend({
            onDragStart: function(){},
            onDragEnd: function(){},
            onSelect: function() {},
            style: function(){},
            enableSelect: function(){ return true; },
            backgroundOpacity: 0.15,
            draggable : true,
            isPoint : false
        }, options);

        var dragBoxInteraction = new ol.interaction.DragBox({
            condition: ol.events.condition.platformModifierKeyOnly
        });

        var selectInteraction = new ol.interaction.Select({
            layer: layer,
            condition: function(events){
                return enabled &&(ol.events.condition.doubleClick(events) || ol.events.condition.singleClick(events));
            },
            style: settings.style,
            filter : function (feature, layer) {
                return ((feature.getGeometry() instanceof ol.geom.Point && settings.isPoint) || (feature.getGeometry() instanceof ol.geom.LineString && !settings.isPoint));
            }
        });

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
                if(!settings.draggable && enabled)
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
                    if(selectInteraction.getFeatures().getLength() < 1 && map.getView().getZoom() <= 13){
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

        var clear = function(type){
            if(!type){
                selectInteraction.getFeatures().clear();
                highlightLayer();
                return;
            }
            _.each(selectInteraction.getFeatures().getArray(), function(feature){
                if(feature.getProperties().type === type) {
                    selectInteraction.getFeatures().remove(feature);
                }
            });

        };

        var addSelectionFeatures = function(features, type, hasAdjacent){
            if(!hasAdjacent)
                clear(type);

            _.each(features, function(feature){
                selectInteraction.getFeatures().push(feature);
            });
            if(!type)
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
            toggleDragBox: toggleDragBox,
            activate: activate,
            deactivate: deactivate,
            clear : clear
        };
    };
})(this);
