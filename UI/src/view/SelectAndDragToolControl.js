(function(root) {
    root.SelectAndDragToolControl = function(application, layer, map, options) {

        var mapDoubleClickEventKey;
        var enabled = false;

        var settings = _.extend({
            onDragStart: function(){},
            onDragEnd: function(){},
            onSelect: function() {},
            style: function(){},
            backgroundOpacity: 0.15
        }, options);

        var dragBoxInteraction = new ol.interaction.DragBox({
            condition: ol.events.condition.platformModifierKeyOnly
        });

        var selectInteraction = new ol.interaction.Select({
            layer: layer,
            condition: function(events){
                return enabled && (ol.events.condition.doubleClick(events) || ol.events.condition.singleClick(events));
            },
            style: settings.style
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
            if(evt.selected.length > 0)
                unhighlightLayer();
            else
                highlightLayer();

            settings.onSelect(evt);
        });

        var toggleDragBox = function() {
            if (!application.isReadOnly() && enabled)
                map.addInteraction(dragBoxInteraction);
            else
                map.removeInteraction(dragBoxInteraction);
        };

        var highlightLayer = function(){
            layer.setOpacity(1);
        };

        var unhighlightLayer = function(){
            layer.setOpacity(settings.backgroundOpacity);
        };

        var activate = function() {
            enabled = true;
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

        var clear = function(){
            selectInteraction.getFeatures().clear();
            highlightLayer();
        };

        var addSelectionFeatures = function(features){
            clear();
            _.each(features, function(feature){
                selectInteraction.getFeatures().push(feature);
            });
            unhighlightLayer();
        };

        map.addInteraction(selectInteraction);
        eventbus.on('application:readOnly', toggleDragBox);

        return {
            getSelectInteraction: function(){ return selectInteraction; },
            getDragBoxInteraction: function(){ return dragBoxInteraction; },
            addSelectionFeatures: addSelectionFeatures,
            toggleDragBox: toggleDragBox,
            activate: activate,
            deactivate: deactivate,
            clear: clear
        };
    };
})(this);
