(function(root) {
    root.MassTransitMarker = function(data) {
        var EMPTY_IMAGE_TYPE = '99_';
        var getBounds = function(lon, lat) {
            return OpenLayers.Bounds.fromArray([lon, lat, lon, lat]);
        };

        var bounds = getBounds(data.group ? data.group.lon : data.lon, data.group ? data.group.lat : data.lat);
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

        var moveTo = function(lonlat) {
            box.bounds =  {
                bottom: lonlat.lat,
                left: lonlat.lon,
                right: lonlat.lon,
                top: lonlat.lat
            };
        };

        var getSelectedContent = function(asset, imageIds){
            var busStopImages = mapBusStopImageIdsToImages(imageIds);
            var name = assetutils.getPropertyValue(asset, 'nimi_suomeksi');
            var direction = assetutils.getPropertyValue(asset, 'liikennointisuuntima');

            return $('<div class="expanded-bus-stop" />').addClass(data.group && data.group.positionIndex === 0 && 'root')
                       .append($('<div class="images field" />').html(busStopImages))
                       .append($('<div class="bus-stop-id field"/>').html($('<div class="padder">').text(asset.externalId)))
                       .append($('<div class="bus-stop-name field"/>').text(name))
                       .append($('<div class="bus-stop-direction field"/>').text(direction));
        };

        var createDefaultState = function() {
            var busImages = $('<div class="bus-basic-marker" />').addClass(data.group && data.group.positionIndex === 0 && 'root');
            busImages.append($('<div class="images" />').append(mapBusStopImageIdsToImages(data.imageIds)));
            $(box.div).html(busImages);
            setPositionByIndex();
        };
        var padding = -25;
        var setPositionByIndex = function() {
          var positionIndex = (data.group && data.group.positionIndex) ? padding*data.group.positionIndex : 0;
          $(box.div).css("-webkit-transform", "translate(0px,"+positionIndex+"px)");
        };

        var mapBusStopImageIdsToImages =  function (imageIds) {
            imageIds.sort();
            return _.map(_.isEmpty(imageIds) ? [EMPTY_IMAGE_TYPE] : imageIds, function (imageId) {
                return '<img src="api/images/' + imageId + '.png">';
            });
        };

        var handleAssetPropertyValueChanged = function(simpleAsset) {
            if (simpleAsset.id === data.id && simpleAsset.propertyData.publicId === "pysakin_tyyppi") {
                var imageIds = _.map(simpleAsset.propertyData.values, function(propertyValue) {
                   return propertyValue.propertyValue + '_'+ new Date();
                });
                $(box.div).html(getSelectedContent(data, imageIds));
            }
        };

        var renderNewState = function(asset) {
          box.bounds = getBounds(asset.lon, asset.lat);
          $(box.div).html(getSelectedContent(asset, asset.imageIds));
          setPositionByIndex();
        };

        var deselectState = function() {
            if (selected) {
                createDefaultState();
                selected  = false;
            }
        };

        var pullFetchedAssetFromStack = function() {
          var fetchedPositionIndex = data.group.positionIndex;
          var fetchedGroupId = data.group.id;
          data.group = {
            positionIndex : 0
          };
          eventbus.trigger('asset:fetched-from-group', {id : fetchedGroupId, positionIndex : fetchedPositionIndex});
        };

        eventbus.on('asset:closed tool:changed asset:placed', deselectState);

        eventbus.on('asset:fetched asset:selected', function (asset) {
          if (asset.id === data.id) {
            asset.group = data.group;
            renderNewState(asset);
            selected = true;
          } else {
            deselectState();
          }
        });

        eventbus.on('asset:fetched-from-group', function (groupInfo) {
          if (data.group && data.group.id === groupInfo.id) {
            if (data.group.positionIndex > groupInfo.positionIndex) {
              data.group.positionIndex--;
              createDefaultState();
            }
          }
        });

        eventbus.on('assetPropertyValue:changed', handleAssetPropertyValueChanged, this);

        eventbus.on('asset:moved', function(e) {
          if (selected && data.group.positionIndex > 0) {
              pullFetchedAssetFromStack();
              renderNewState(data);
          }
        });

        return {
            createMarker: createMarker,
            createNewMarker : createNewMarker,
            moveTo: moveTo
        };
    };
}(this));