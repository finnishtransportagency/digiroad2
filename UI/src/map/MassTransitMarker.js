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
    box.id = data.id;

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
      renderSelectedState();
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
      var name = selectedAssetModel.getName();
      var direction = selectedAssetModel.getDirection();

      var filteredGroup = filterByValidityPeriod(data.group.assetGroup);
      var groupIndex = findGroupIndexForAsset(filteredGroup, data);

      return $('<div class="expanded-bus-stop" />')
        .addClass(groupIndex === 0 && 'root')
        .addClass(data.floating ? 'floating' : '')
        .append($('<div class="images field" />').html(busStopImages))
        .append($('<div class="bus-stop-id field"/>').html($('<div class="padder">').text(asset.nationalId)))
        .append($('<div class="bus-stop-name field"/>').text(name))
        .append($('<div class="bus-stop-direction field"/>').text(direction));
    };

    var filterByValidityPeriod = function(group) {
      return _.filter(group, function(asset) {
        return assetsModel.selectedValidityPeriodsContain(asset.validityPeriod);
      });
    };

    var findGroupIndexForAsset = function(group, asset) {
      return Math.max(_.findIndex(group, { id: asset.id }), 0);
    };

    var calculateOffsetForMarker = function(dataForCalculation) {
      var filteredGroup = filterByValidityPeriod(dataForCalculation.group.assetGroup);
      var groupIndex = findGroupIndexForAsset(filteredGroup, dataForCalculation);

      return _.chain(filteredGroup)
              .take(groupIndex)
              .map(function(x) { return x.imageIds.length * IMAGE_HEIGHT; })
              .map(function(x) { return GROUP_ASSET_PADDING + x; })
              .reduce(function(acc, x) { return acc + x; }, 0)
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
        var id = imageId.split('_')[0];
        return '<img src="/images/mass-transit-stops/' + id + '.png">';
      });
    };

    var createImageIds = function(properties) {
      return _.chain(properties)
              .where(function(property) { return property.publicId === 'pysakin_tyyppi'; })
              .pluck('values')
              .flatten()
              .map(function(value) { return value.propertyValue + '_'; })
              .value();
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
      var filteredGroup = filterByValidityPeriod(data.group.assetGroup);
      var groupIndex = findGroupIndexForAsset(filteredGroup, data);
      var defaultMarker = $('<div class="bus-basic-marker" />')
        .addClass(data.floating ? 'floating' : '')
        .append($('<div class="images" />').append(mapBusStopImageIdsToImages(data.imageIds)))
        .addClass(groupIndex === 0 && 'root');
      $(box.div).html(defaultMarker);
      $(box.div).removeClass('selected-asset');
      setYPositionForAssetOnGroup();
    };

    var renderSelectedState = function() {
      var imageIds = createImageIds(selectedAssetModel.getProperties());
      $(box.div).html(getSelectedContent(data, imageIds))
                .addClass('selected-asset');
      setYPositionForAssetOnGroup();
    };

    var select = function() { renderSelectedState(); };

    var deselect = function() { renderDefaultState(); };

    var detachAssetFromGroup = function() {
      _.remove(data.group.assetGroup, function(asset) {
        return asset.id == data.id;
      });
      data.group.moved = true;
    };

    eventbus.on('tool:changed', deselect);

    var finalizeMove = function(asset) {
      _.merge(data, asset);
      if (data.group.moved) {
        data.group.moved = false;
        data.group.assetGroup = [data];
        renderSelectedState();
      }
    };

    var rePlaceInGroup = function() {
      var filteredGroup = filterByValidityPeriod(data.group.assetGroup);
      var groupIndex = findGroupIndexForAsset(filteredGroup, data);
      var addOrRemoveClass = groupIndex === 0 ? 'addClass' : 'removeClass';
      $(box.div).find('.expanded-bus-stop, .bus-basic-marker')[addOrRemoveClass]('root');
      setYPositionForAssetOnGroup();
    };

    eventbus.on('assetPropertyValue:changed', handleAssetPropertyValueChanged, this);

    eventbus.on('validityPeriod:changed', function() {
      rePlaceInGroup();
    });

    return {
      createMarker: createMarker,
      createNewMarker: createNewMarker,
      moveTo: moveTo,
      select: select,
      deselect: deselect,
      finalizeMove: finalizeMove,
      rePlaceInGroup: rePlaceInGroup
    };
  };
}(this));
