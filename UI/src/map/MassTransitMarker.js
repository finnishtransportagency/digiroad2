(function(root) {

  root.MassTransitMarkerStyle = function(data, map){
    var IMAGE_HEIGHT = 17;
    var IMAGE_WIDTH = 28;
    var IMAGE_MARGIN = 2;
    var IMAGE_PADDING = 4;
    var STICK_HEIGHT = 15;
    var NATIONAL_ID_WIDTH = 45;
    var EMPTY_IMAGE_TYPE = '99';
    var styleScale = 1;

    var roundRect = function(canvasContext, x, y, width, height, radius) {
      canvasContext.beginPath();
      canvasContext.moveTo(x + radius, y);
      canvasContext.lineTo(x + width - radius, y);
      canvasContext.quadraticCurveTo(x + width, y, x + width, y + radius);
      canvasContext.lineTo(x + width, y + height - radius);
      canvasContext.quadraticCurveTo(x + width, y + height, x + width - radius, y + height);
      canvasContext.lineTo(x + radius, y + height);
      canvasContext.quadraticCurveTo(x, y + height, x, y + height - radius);
      canvasContext.lineTo(x, y + radius);
      canvasContext.quadraticCurveTo(x, y, x + radius, y);
      canvasContext.closePath();
    };

    var createDivOffeset = function(){
      var div = document.createElement('div');
      div.setAttribute('style', 'font: 10px sans-serif; position: absolute; visibility: hidden; height: auto; width: auto; white-space: nowrap;');
      div.setAttribute('id', 'offset-text-width');
      document.getElementsByTagName('body')[0].appendChild(div);
      return div;
    };

    var getOffset = function(text){
      var textMeasure = document.getElementById('offset-text-width');
      if(!textMeasure)
        textMeasure = createDivOffeset();
      textMeasure.innerHTML = text;
      return textMeasure.clientWidth + 10;
    };

    var scale = function(value){
      return value * styleScale;
    };

    var createSelectionBackgroundImage = function(busStopsNumber, text){
      var canvas = document.createElement('canvas');
      var canvasContext = canvas.getContext('2d');
      var textOffset = getOffset(text) + 10;
      canvas.width = ''+(IMAGE_MARGIN + (IMAGE_PADDING * 2) + IMAGE_WIDTH + NATIONAL_ID_WIDTH + textOffset + 15);
      canvas.height = ''+((IMAGE_MARGIN * 2) + (IMAGE_PADDING * 2) + (IMAGE_HEIGHT * busStopsNumber) + 5);

      roundRect(canvasContext, 1,1, IMAGE_MARGIN + (IMAGE_PADDING * 2) + IMAGE_WIDTH + NATIONAL_ID_WIDTH + textOffset, (IMAGE_MARGIN * 2) + (IMAGE_PADDING * 2) + (IMAGE_HEIGHT * busStopsNumber), 0);
      canvasContext.lineWidth = 2;
      canvasContext.stroke();
      canvasContext.fillStyle = '#383836';
      canvasContext.fill();

      var image = new Image();
      image.src = canvas.toDataURL("image/png");
      return { img:image, width: canvas.width, height: canvas.height };
    };

    var getCachedImage = function(key){
      if(window.cachedStopsBackgroundImage && window.cachedStopsBackgroundImage[key])
        return window.cachedStopsBackgroundImage[key];
      return false;
    };

    var cacheImage = function(key, image, width, height){
      var imageInfo = { img: image, width: width, height: height };
      if(!window.cachedStopsBackgroundImage)
        window.cachedStopsBackgroundImage = {};
      window.cachedStopsBackgroundImage[key] = imageInfo;
      return imageInfo;
    };

    var createStickImage = function(){
      var cachedImageKey = 'stick';
      var cachedImage = getCachedImage(cachedImageKey);

      if(cachedImage)
        return cachedImage;

      var canvas = document.createElement('canvas');
      var canvasContext = canvas.getContext('2d');

      canvas.width = '5';
      canvas.height = '15';

      canvasContext.beginPath();
      canvasContext.moveTo(1, 1);
      canvasContext.lineTo(1, 15);
      canvasContext.lineWidth = 5;
      canvasContext.strokeStyle = '#5a5a57';
      canvasContext.stroke();

      var image = new Image();
      image.src = canvas.toDataURL("image/png");

      return cacheImage(cachedImageKey, image, canvas.width, canvas.height );
    };

    var createStopBackgroundImage = function(busStopsNumber){
      var cachedImageKey = 'image_'+busStopsNumber;
      var cachedImage = getCachedImage(cachedImageKey);

      if(cachedImage)
        return cachedImage;

      var canvas = document.createElement('canvas');
      var canvasContext = canvas.getContext('2d');

      canvas.width = '' + (IMAGE_WIDTH + (IMAGE_PADDING * 2) + 5);
      canvas.height = '' + ((IMAGE_PADDING * 2) + (IMAGE_HEIGHT * busStopsNumber) + 5);

      roundRect(canvasContext, 1,1, IMAGE_WIDTH + (IMAGE_PADDING * 2), (IMAGE_PADDING * 2) + (IMAGE_HEIGHT * busStopsNumber), 3);
      canvasContext.lineWidth = 2;
      canvasContext.stroke();
      canvasContext.fillStyle = '#fff';
      canvasContext.fill();

      roundRect(canvasContext, 1 + IMAGE_PADDING, 1 + IMAGE_PADDING, IMAGE_WIDTH, IMAGE_HEIGHT * busStopsNumber, 3);
      canvasContext.lineWidth = 2;
      canvasContext.stroke();
      canvasContext.fillStyle = '#000';
      canvasContext.fill();

      var image = new Image();
      image.src = canvas.toDataURL("image/png");

      return cacheImage(cachedImageKey, image, canvas.width, canvas.height );
    };

    var createStopTypeStyles = function(stopTypes, margin){
      var groupOffset = groupOffsetForAsset();
      var imgMargin = margin ? margin : 0;
      stopTypes.sort();
      var i = 0;
      return _.map(_.isEmpty(stopTypes) ? [EMPTY_IMAGE_TYPE] : stopTypes, function(stopType) {
        i++;
        return new ol.style.Style({
          image: new ol.style.Icon(({
            anchor: [-(IMAGE_PADDING+1+imgMargin), (i * IMAGE_HEIGHT)+ IMAGE_PADDING + STICK_HEIGHT + imgMargin + groupOffset],
            anchorXUnits: 'pixels',
            anchorYUnits: 'pixels',
            src: 'images/mass-transit-stops/' + stopType + '.png',
            scale: styleScale
          }))
        });
      });
    };

    var createSelectionBackgroundStyle = function(stopTypes, text){
      var groupOffset = groupOffsetForAsset();
      var types = _.isEmpty(stopTypes) ? 1 : stopTypes.length;
      var background = createSelectionBackgroundImage(types, text);
      return new ol.style.Style({
        image: new ol.style.Icon(({
          anchor: [0, (types * IMAGE_HEIGHT) + STICK_HEIGHT + (IMAGE_PADDING * 2) + (IMAGE_MARGIN * 2) + 1 + groupOffset],
          anchorXUnits: 'pixels',
          anchorYUnits: 'pixels',
          img: background.img,
          imgSize: [background.width,background.height],
          scale: styleScale
        }))
      });
    };

    var createStopBackgroundStyle = function(stopTypes, margin){
      var groupOffset = groupOffsetForAsset();
      var imgMargin = margin ? margin : 0;
      var types = _.isEmpty(stopTypes) ? 1 : stopTypes.length;
      var background = createStopBackgroundImage(types);
      return new ol.style.Style({
        image: new ol.style.Icon(({
          anchor: [0-imgMargin, (types * IMAGE_HEIGHT) + STICK_HEIGHT + (IMAGE_PADDING * 2) + 1 + imgMargin + groupOffset],
          anchorXUnits: 'pixels',
          anchorYUnits: 'pixels',
          img: background.img,
          imgSize: [background.width,background.height],
          scale: styleScale
        }))
      });
    };

    var createStickStyle = function(){
      var stickImage = createStickImage();
      return new ol.style.Style({
        image: new ol.style.Icon(({
          anchor: [0, STICK_HEIGHT+1],
          anchorXUnits: 'pixels',
          anchorYUnits: 'pixels',
          img: stickImage.img,
          imgSize: [stickImage.width,stickImage.height],
          scale: styleScale
        }))
      });
    };

    var createDirectionArrowStyle = function() {
      var directionArrowSrc = data.floating ? 'src/resources/digiroad2/bundle/assetlayer/images/direction-arrow-warning.svg' : 'src/resources/digiroad2/bundle/assetlayer/images/direction-arrow.svg';
      var rotation = validitydirections.calculateRotation(data.bearing, data.validityDirection);
      return new ol.style.Style({
        image: new ol.style.Icon(({
          src: directionArrowSrc,
          rotation: rotation,
          scale: styleScale
        }))
      });
    };

    var createTextStyles = function(stopTypes, nationalId, name, direction, margin){
      var groupOffset = groupOffsetForAsset();
      var imgMargin = margin ? margin : 0;
      var types = _.isEmpty(stopTypes) ? 1 : stopTypes.length;
      var beginOffset = IMAGE_WIDTH + (IMAGE_PADDING * 2) + (IMAGE_MARGIN * 2) + 5;
      var offsetY = (types * IMAGE_HEIGHT) + STICK_HEIGHT + (IMAGE_PADDING * 2) + 1 + imgMargin - 10; //minus font size
      return [
        new ol.style.Style({
          text: new ol.style.Text(({
            text: ''+nationalId,
            textAlign: 'start',
            offsetX: scale(beginOffset),
            offsetY: scale(-(offsetY + groupOffset)),
            fill: new ol.style.Fill({ color: '#fff'}),
            scale: styleScale
          }))
        }),
        new ol.style.Style({
          text: new ol.style.Text(({
            text: ''+name,
            textAlign: 'start',
            offsetX: scale(beginOffset + NATIONAL_ID_WIDTH),
            offsetY: scale(-(offsetY + groupOffset)),
            fill: new ol.style.Fill({ color: '#a4a4a2'}),
            scale: styleScale
          }))
        }),
        new ol.style.Style({
          text: new ol.style.Text(({
            text: ''+direction,
            offsetX: scale(beginOffset + NATIONAL_ID_WIDTH + getOffset(name)),
            offsetY: scale(-(offsetY + groupOffset)),
            textAlign: 'start',
            fill: new ol.style.Fill({ color: '#fff'}),
            scale: styleScale
          }))
        })
      ];
    };

    var createSelectionMarkerStyles = function(){
      var name = '';
      var direction = '';
      var nationalId = data.nationalId ? data.nationalId : '';
      if(selectedMassTransitStopModel.exists()){
        name = selectedMassTransitStopModel.getName();
        direction = selectedMassTransitStopModel.getDirection();
      }else
      {
        if(data.propertyData){
          name = selectedMassTransitStopModel.getName(data.propertyData);
          direction = selectedMassTransitStopModel.getDirection(data.propertyData);
        }
      }

      var styles = [];
      styles = styles.concat(createDirectionArrowStyle());
      styles = styles.concat(createStickStyle());
      styles = styles.concat(createSelectionBackgroundStyle(data.stopTypes, name+direction));
      styles = styles.concat(createStopBackgroundStyle(data.stopTypes, IMAGE_MARGIN));
      styles = styles.concat(createStopTypeStyles(data.stopTypes, IMAGE_MARGIN));
      styles = styles.concat(createTextStyles(data.stopTypes, nationalId, name, direction, IMAGE_MARGIN));
      //feature.setStyle(styles);
      //return feature;
      return styles;
    };

    var createDefaultMarkerStyles = function(){
      var styles = [];
      styles = styles.concat(createDirectionArrowStyle());
      styles = styles.concat(createStickStyle());
      styles = styles.concat(createStopBackgroundStyle(data.stopTypes));
      styles = styles.concat(createStopTypeStyles(data.stopTypes));
      //feature.setStyle(styles);
      //return feature;
      return styles;
    };

    var createFeature = function(){
      return new ol.Feature({geometry : new ol.geom.Point([data.group.lon, data.group.lat])});
    };

    var groupOffsetForAsset = function() {
      var height = 0;
      _.each(data.group.assetGroup, function(asset){
        if(asset.id == data.id)
          return false;
        height += (asset.stopTypes.length * IMAGE_HEIGHT) + (IMAGE_MARGIN * 2) + (IMAGE_PADDING * 2) - 2;
      });
      return height;
    };

    //TODO we can do something like this to be like the rest of the application
    //var cachedDefaultStyle = {};
    //
    //var styleProviders = {
    //  default: {
    //    getStyle: function(asset, zoomLevel){
    //      var style = createDefaultMarkerStyles(zoomLevel);
    //      cachedDefaultStyle['zoom_'+zoomLevel] = style;
    //      if(style)
    //        return style;
    //
    //
    //
    //    }
    //  },
    //  selection: {
    //    getStyle: function(){
    //
    //    }
    //  }
    //};

    ////TODO add the offset if the bus stop is on a group of busstops
    //var extractStopTypes = function(properties) {
    //  return _.chain(properties)
    //      .where({ publicId: 'pysakin_tyyppi' })
    //      .pluck('values')
    //      .flatten()
    //      .pluck('propertyValue')
    //      .value();
    //};

    return {
      createSelectionMarkerStyles: createSelectionMarkerStyles,
      createDefaultMarkerStyles: createDefaultMarkerStyles,
      createFeature: createFeature
    };
  };


  /*
  root.MassTransitMarker = function(data) {

    var GROUP_ASSET_PADDING = 8;
    var IMAGE_HEIGHT = 17;
    var EMPTY_IMAGE_TYPE = '99';
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

    var getSelectedContent = function(asset, stopTypes) {
      var busStopImages = mapBusStopTypesToImages(stopTypes);
      var name = selectedMassTransitStopModel.getName();
      var direction = selectedMassTransitStopModel.getDirection();

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
        return massTransitStopsCollection.selectedValidityPeriodsContain(asset.validityPeriod);
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
              .map(function(x) { return x.stopTypes.length * IMAGE_HEIGHT; })
              .map(function(x) { return GROUP_ASSET_PADDING + x; })
              .reduce(function(acc, x) { return acc + x; }, 0)
              .value();
    };

    var setYPositionForAssetOnGroup = function() {
      var yPositionInGroup = -1 * calculateOffsetForMarker(data);
      $(box.div).css("-webkit-transform", "translate(0px," + yPositionInGroup + "px)")
        .css("transform", "translate(0px," + yPositionInGroup + "px)");
    };

    var mapBusStopTypesToImages = function(stopTypes) {
      stopTypes.sort();
      return _.map(_.isEmpty(stopTypes) ? [EMPTY_IMAGE_TYPE] : stopTypes, function(stopType) {
        return '<img src="images/mass-transit-stops/' + stopType + '.png">';
      });
    };

    var extractStopTypes = function(properties) {
      return _.chain(properties)
              .where({ publicId: 'pysakin_tyyppi' })
              .pluck('values')
              .flatten()
              .pluck('propertyValue')
              .value();
    };

    var handleAssetPropertyValueChanged = function(simpleAsset) {
      if (simpleAsset.id === data.id && _.contains(['pysakin_tyyppi', 'nimi_suomeksi'], simpleAsset.propertyData.publicId)) {
        var properties = selectedMassTransitStopModel.getProperties();
        var stopTypes = extractStopTypes(properties);
        data.stopTypes = stopTypes;
        var assetWithProperties = _.merge({}, data, {propertyData: properties});
        $(box.div).html(getSelectedContent(assetWithProperties, stopTypes));
      }
    };

    var renderDefaultState = function() {
      var filteredGroup = filterByValidityPeriod(data.group.assetGroup);
      var groupIndex = findGroupIndexForAsset(filteredGroup, data);
      var defaultMarker = $('<div class="bus-basic-marker" />')
        .addClass(data.floating ? 'floating' : '')
        .append($('<div class="images" />').append(mapBusStopTypesToImages(data.stopTypes)))
        .addClass(groupIndex === 0 && 'root');
      $(box.div).html(defaultMarker);
      $(box.div).removeClass('selected-asset');
      setYPositionForAssetOnGroup();
    };

    var renderSelectedState = function() {
      var stopTypes = extractStopTypes(selectedMassTransitStopModel.getProperties());
      $(box.div).html(getSelectedContent(data, stopTypes))
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
  */
}(this));
