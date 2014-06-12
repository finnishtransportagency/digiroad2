(function(root) {
    root.MassTransitMarker = function(data) {

        var EMPTY_IMAGE_TYPE = '99_';

        var getBounds = function(lon, lat) {
            return OpenLayers.Bounds.fromArray([lon, lat, lon, lat]);
        };

        var bounds = getBounds(data.lon, data.lat);
        var box = new OpenLayers.Marker.Box(bounds, "ffffff00", 0);
        var selected = false; // keeping track of the selected state while assetlayer refactoring is ongoing TODO: move to selected model

        var createMarker = function() {
            $(box.div).css("overflow", "visible !important");
            createDefaultState();
            return box;
        };

        var createNewMarker = function() {
            $(box.div).css("overflow", "visible !important");
            renderNewState(data);
            return box;
        };

        var getSelectedContent = function(asset, imageIds){
            var expandedBusStop = $('<div class="expanded-bus-stop" />');
            var busImages = $('<div class="images" />');
            busImages.append(createBusStopsImages(imageIds));
            expandedBusStop.append(busImages);
            expandedBusStop.append($('<div class="bus-stop-id"/>').append(asset.id));
            var name = assetutils.getPropertyValue(asset, 'nimi_suomeksi');
            expandedBusStop.append($('<div class="bus-stop-name"/>').append(name));
            var direction = assetutils.getPropertyValue(asset, 'liikennointisuuntima');
            expandedBusStop.append($('<div class="bus-stop-direction"/>').append(direction));
            return expandedBusStop;
        };

        var createDefaultState = function() {
            var busImages = $('<div class="bus-basic-marker" />');
            busImages.append($('<div class="images" />').append(createBusStopsImages(data.imageIds)));
            $(box.div).html(busImages);

        };

        var createBusStopsImages =  function (imageIds) {
            return _.map(_.isEmpty(imageIds) ? [EMPTY_IMAGE_TYPE] : imageIds, function (imageId) {
                return '<img src="api/images/' + imageId + '.png">';
            });
        };

        var handleAssetPropertyValueChanged = function(assetData) {
            if (assetData.id === data.id && assetData.propertyData.publicId === "pysakin_tyyppi") {
                var imageIds = _.map(assetData.propertyData.values, function(propertyValue) {
                   return propertyValue.propertyValue + '_'+ new Date();
                });
                $(box.div).html(getSelectedContent(assetData, imageIds));
            }
        };

        var renderNewState = function(asset) {
            box.bounds = getBounds(asset.lon, asset.lat);
            $(box.div).html(getSelectedContent(asset, asset.imageIds));
        };

        var unSelectState = function() {
            if (selected) {
                createDefaultState();
                selected  = false;
            }
        };

        eventbus.on('asset:closed tool:changed asset:placed', unSelectState);

        eventbus.on('asset:fetched asset:selected', function (asset) {
            if (asset.id === data.id) {
                data = asset; // TODO: use data model when it's ready
                renderNewState(asset);
                selected = true;
            } else {
                unSelectState();
            }
        });

        eventbus.on('assetPropertyValue:changed', handleAssetPropertyValueChanged, this);

        return {
            createMarker: createMarker,
            createNewMarker : createNewMarker
        };
    };
}(this));