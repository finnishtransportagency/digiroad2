(function(root) {
    root.MassTransitMarker = function(data) {

        var GROUP_ASSET_PADDING = -25;
        var EMPTY_IMAGE_TYPE = '99_';
        var getBounds = function(lon, lat) {
            return OpenLayers.Bounds.fromArray([lon, lat, lon, lat]);
        };

        var bounds = getBounds(data.group.lon, data.group.lat);
        var box = new OpenLayers.Marker.Box(bounds, "ffffff00", 0);
        var selected = false; // keeping track of the selected state while assetlayer refactoring is ongoing TODO: move to selected model

        var createMarker = function() {
            $(box.div).css("overflow", "visible !important");
            renderDefaultState();
            return box;
        };

        var createNewMarker = function() {
            $(box.div).css("overflow", "visible !important");
            renderNewState(data);
            return box;
        };

        var moveTo = function(lonlat) {
            box.bounds = {
                bottom: lonlat.lat,
                left: lonlat.lon,
                right: lonlat.lon,
                top: lonlat.lat
            };
            if (data.group.groupIndex > 0) {
                detachAssetFromGroup();
                renderNewState(data);
            }
        };

        var getSelectedContent = function(asset, imageIds){
            var busStopImages = mapBusStopImageIdsToImages(imageIds);
            var name = assetutils.getPropertyValue(asset, 'nimi_suomeksi');
            var direction = assetutils.getPropertyValue(asset, 'liikennointisuuntima');

            return $('<div class="expanded-bus-stop" />').addClass(data.group.groupIndex === 0 && 'root')
                       .append($('<div class="images field" />').html(busStopImages))
                       .append($('<div class="bus-stop-id field"/>').html($('<div class="padder">').text(asset.externalId)))
                       .append($('<div class="bus-stop-name field"/>').text(name))
                       .append($('<div class="bus-stop-direction field"/>').text(direction));
        };

        var setYPositionForAssetOnGroup = function() {
          var yPositionInGroup = (data.group.groupIndex) ? GROUP_ASSET_PADDING * data.group.groupIndex : 0;
          $(box.div).css("-webkit-transform", "translate(0px," + yPositionInGroup + "px)");
        };

        var mapBusStopImageIdsToImages =  function (imageIds) {
            imageIds.sort();
            return _.map(_.isEmpty(imageIds) ? [EMPTY_IMAGE_TYPE] : imageIds, function (imageId) {
                return '<img src="api/images/' + imageId + '.png">';
            });
        };

        var createImageIds = function(properties) {
            var assetType = _.find(properties, function(property) { return property.publicId === "pysakin_tyyppi" });
            return _.map(assetType.values, function(value) { return value.propertyValue + '_'; });
        };

        var handleAssetPropertyValueChanged = function(simpleAsset) {
            if (simpleAsset.id === data.id && _.contains(['pysakin_tyyppi', 'nimi_suomeksi'], simpleAsset.propertyData.publicId)) {
                var properties = selectedAssetModel.getProperties();
                var imageIds = createImageIds(properties);
                var assetWithProperties = _.merge({}, data, {propertyData: properties});
                $(box.div).html(getSelectedContent(assetWithProperties, imageIds));
            }
        };

        var renderDefaultState = function() {
            var busImages = $('<div class="bus-basic-marker" />').addClass(data.group && data.group.groupIndex === 0 && 'root');
            busImages.append($('<div class="images" />').append(mapBusStopImageIdsToImages(data.imageIds)));
            $(box.div).html(busImages);
            $(box.div).removeClass('selected-asset');
            setYPositionForAssetOnGroup();
        };        
        
        var renderNewState = function(assetWithProperties) {
            box.bounds = getBounds(assetWithProperties.lon, assetWithProperties.lat);
            $(box.div).html(getSelectedContent(assetWithProperties, assetWithProperties.imageIds));
            $(box.div).addClass('selected-asset');
            setYPositionForAssetOnGroup();
        };

        var deselect = function() {
            if (selected) {
                renderDefaultState();
                selected = false;
            }
        };

        var detachAssetFromGroup = function() {
          eventbus.trigger('asset:removed-from-group', data);
          data.group = {
            groupIndex : 0
          };
        };

        eventbus.on('asset:closed tool:changed asset:placed', deselect);

        eventbus.on('asset:fetched asset:selected', function (asset) {
          if (asset.id === data.id) {
            if (data.group.size > 0) {
                detachAssetFromGroup();
            }
            _.merge(data, asset);
            renderNewState(asset);
            selected = true;
          } else {
            deselect();
          }
        });

        eventbus.on('asset:removed-from-group', function (asset) {
          if (data.group.id === asset.group.id) {
            if (data.group.groupIndex > asset.group.groupIndex) {
              data.group.groupIndex--;
              renderDefaultState();
            }
          }
        });

        eventbus.on('assetPropertyValue:changed', handleAssetPropertyValueChanged, this);

        return {
            createMarker: createMarker,
            createNewMarker : createNewMarker,
            moveTo: moveTo
        };
    };
}(this));