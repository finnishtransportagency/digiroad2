(function(root) {
  root.MassTransitMarker = function(data) {

    var GROUP_ASSET_PADDING = 8;
    var IMAGE_HEIGHT = 17;
    var EMPTY_IMAGE_TYPE = '99_';
    var getBounds = function(lon, lat) {
      return OpenLayers.Bounds.fromArray([lon, lat, lon, lat]);
    };

    var bounds = getBounds(data.group.lon, data.group.lat);
    var box = new OpenLayers.Marker.Box(bounds, "ffffff00", 0);
    var selected = false; // keeping track of the selected state while assetlayer refactoring is ongoing TODO: move to selected model

    var configureMarkerDiv = function(id) {
      $(box.div).css('overflow', 'visible !important')
                .attr('data-asset-id', id);
    };

    var createMarker = function() {
      configureMarkerDiv(data.id);
      renderDefaultState();
      return box;
    };

    var createNewMarker = function() {
      configureMarkerDiv(data.id);
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
      if (!data.group.moved) {
        detachAssetFromGroup();
        setYPositionForAssetOnGroup();
        $(box.div).find('.expanded-bus-stop').addClass('root');
      }
    };

    var getSelectedContent = function(asset, imageIds) {
      var busStopImages = mapBusStopImageIdsToImages(imageIds);
      var name = assetutils.getPropertyValue(asset, 'nimi_suomeksi');
      var direction = assetutils.getPropertyValue(asset, 'liikennointisuuntima');

      return $('<div class="expanded-bus-stop" />').addClass(data.group.groupIndex === 0 && 'root')
        .append($('<div class="images field" />').html(busStopImages))
        .append($('<div class="bus-stop-id field"/>').html($('<div class="padder">').text(asset.externalId)))
        .append($('<div class="bus-stop-name field"/>').text(name))
        .append($('<div class="bus-stop-direction field"/>').text(direction));
    };

    var calculateOffsetForMarker = function(dataForCalculation) {
      return _.chain(dataForCalculation.group.assetGroup)
        .take(dataForCalculation.group.groupIndex)
        .map(function(x) {
          return x.imageIds.length * IMAGE_HEIGHT;
        })
        .map(function(x) {
          return GROUP_ASSET_PADDING + x;
        })
        .reduce(function(acc, x) {
          return acc + x;
        }, 0)
        .value();
    };

    var setYPositionForAssetOnGroup = function() {
      var yPositionInGroup = -1 * calculateOffsetForMarker(data);
      $(box.div).css("-webkit-transform", "translate(0px," + yPositionInGroup + "px)")
        .css("transform", "translate(0px," + yPositionInGroup + "px)");
    };

    var mapBusStopImageIdsToImages = function(imageIds) {
      imageIds.sort();
      return _.map(_.isEmpty(imageIds) ? [EMPTY_IMAGE_TYPE] : imageIds, function(imageId) {
        return '<img src="api/images/' + imageId + '.png">';
      });
    };

    var createImageIds = function(properties) {
      var assetType = _.find(properties, function(property) {
        return property.publicId === "pysakin_tyyppi";
      });
      return _.map(assetType.values, function(value) {
        return value.propertyValue + '_';
      });
    };

    var handleAssetPropertyValueChanged = function(simpleAsset) {
      if (simpleAsset.id === data.id && _.contains(['pysakin_tyyppi', 'nimi_suomeksi'], simpleAsset.propertyData.publicId)) {
        var properties = selectedAssetModel.getProperties();
        var imageIds = createImageIds(properties);
        data.imageIds = imageIds;
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
      _.remove(data.group.assetGroup, function(asset) {
        return asset.id == data.id;
      });
      data.group.oldGroupIndex = data.group.groupIndex;
      data.group.groupIndex = 0;
      data.group.moved = true;
    };

    var rollbackToGroup = function(asset) {
      data.group.groupIndex = (data.group.oldGroupIndex) ? data.group.oldGroupIndex : 0;
      data.group.moved = false;
      data.imageIds = asset.imageIds;
      asset.group = data.group;
      data.group.assetGroup.push(asset);
      renderNewState(asset);
      eventbus.trigger('asset:new-state-rendered', new OpenLayers.LonLat(data.group.lon, data.group.lat));
    };

    var handleFetchedAsset = function(asset) {
      data.imageIds = asset.imageIds;
      data.group.lon = asset.lon;
      data.group.lat = asset.lat;
      asset.group = data.group;
      renderNewState(asset);
    };

    eventbus.on('asset:closed tool:changed asset:placed', deselect);

    eventbus.on('asset:fetched ', function(asset) {
      if (asset.id === data.id) {
        if (data.group.moved) {
          rollbackToGroup(asset);
        } else {
          handleFetchedAsset(asset);
        }
        selected = true;
      } else {
        deselect();
      }
    });

    eventbus.on('asset:saved', function(asset) {
      if (data.id === asset.id && data.group.moved) {
        data.group.moved = false;
        eventbus.trigger('asset:removed-from-group', { assetGroupId: data.group.id, groupIndex: (data.group.oldGroupIndex) ? data.group.oldGroupIndex : 0});
        data.group.groupIndex = 0;
        data.group.oldGroupIndex = null;
        data.group.id = new Date().getTime();
        renderNewState(asset);
      }
    });

    eventbus.on('asset:removed-from-group', function(group) {
      if (data.group.id === group.assetGroupId) {
        if (data.group.groupIndex > group.groupIndex) {
          data.group.groupIndex--;
          renderDefaultState();
        }
      }
    });

    eventbus.on('assetPropertyValue:changed', handleAssetPropertyValueChanged, this);

    return {
      createMarker: createMarker,
      createNewMarker: createNewMarker,
      moveTo: moveTo
    };
  };
}(this));