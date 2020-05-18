(function(root) {

  root.MassTransitMarkerStyle = function(data, collection, map){
    var IMAGE_HEIGHT = 17;
    var IMAGE_WIDTH = 28;
    var IMAGE_MARGIN = 2;
    var IMAGE_PADDING = 4;
    var STICK_HEIGHT = 15;
    var NATIONAL_ID_WIDTH = 45;
    var EMPTY_IMAGE_TYPE = '99';
    var styleScale = 1;

    var SERVICE_POINT_IMAGES = [
        {value: 5, imgUrl: 'images/service_points/railwayStation2.png'},
        {value: 6, imgUrl: 'images/service_points/railwayStation.png'},
        {value: 7, imgUrl: 'images/service_points/subwayStation.png'},
        {value: 8, imgUrl: 'images/service_points/airport.png' },
        {value: 9, imgUrl: 'images/service_points/ferry.png' }
    ];

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

    var createStopBackgroundImage = function(busStopsNumber, validityPeriod){
      var canvasFillColor;

      switch (validityPeriod) {
        case "future":
          canvasFillColor = '#117400';
          break;
        case "past":
          canvasFillColor = '#880000';
          break;
        default:
          canvasFillColor = '#fff';
      }

      var cachedImageKey = 'image_'+busStopsNumber+'_'+validityPeriod;
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
      canvasContext.fillStyle = canvasFillColor;
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

    function extractServicePointsAuxiliarsValues() {
      var resultValues = {palvelu: undefined, tarkenne: undefined};
      var auxiliarData = data.payload ? data.payload.properties : data.propertyData;

      if (!_.isUndefined(auxiliarData)) {
        resultValues.palvelu = _.find(auxiliarData, function(prop) {return prop.publicId == "palvelu";});
        resultValues.tarkenne = _.find(auxiliarData, function(prop) {return prop.publicId == "tarkenne";});
      }

      if (!_.isUndefined(resultValues.palvelu)) {
        if (resultValues.palvelu.values.length > 0) {
          resultValues.palvelu = resultValues.palvelu.values[0].propertyValue;
        } else {
          resultValues.palvelu = undefined;
        }
      }

      if (!_.isUndefined(resultValues.tarkenne)) {
        if (resultValues.tarkenne.values.length > 0) {
          resultValues.tarkenne = resultValues.tarkenne.values[0].propertyValue;
        } else {
          resultValues.tarkenne = undefined;
        }
      }

      return resultValues;
    }

    var createStopTypeStyles = function(stopTypes, margin){
      var groupOffset = groupOffsetForAsset();
      var imgMargin = margin ? margin : 0;
      stopTypes.sort();
      var i = 0;
      var srcImg;
      var anchor;

      return _.map(_.isEmpty(stopTypes) ? [EMPTY_IMAGE_TYPE] : stopTypes, function (stopType) {
        i++;
        if (selectedMassTransitStopModel.isServicePointType(stopType)) {
          var auxServicePointInfo = extractServicePointsAuxiliarsValues();
          anchor = [0, (i * IMAGE_HEIGHT) + IMAGE_PADDING + STICK_HEIGHT + imgMargin + groupOffset + 13];

          if (auxServicePointInfo.palvelu !== "11") {
            srcImg = SERVICE_POINT_IMAGES.find( function(spi) { if (spi.value == auxServicePointInfo.palvelu) return spi;});
            srcImg = srcImg === undefined ? [EMPTY_IMAGE_TYPE] : srcImg.imgUrl;
          }
          else if (!_.isUndefined(auxServicePointInfo.tarkenne)) {
            srcImg = SERVICE_POINT_IMAGES.find( function(spi) { if (spi.value == auxServicePointInfo.tarkenne ) return spi;});
            srcImg = srcImg === undefined ? [EMPTY_IMAGE_TYPE] : srcImg.imgUrl;
          }
        } else {
          anchor = [-(IMAGE_PADDING + 1 + imgMargin), (i * IMAGE_HEIGHT) + IMAGE_PADDING + STICK_HEIGHT + imgMargin + groupOffset];
          srcImg = 'images/mass-transit-stops/' + stopType + '.png';
        }

        return new ol.style.Style({
          image: new ol.style.Icon(({
            anchor: anchor,
            anchorXUnits: 'pixels',
            anchorYUnits: 'pixels',
            src: srcImg,
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

    var createStopBackgroundStyle = function(stopTypes, margin, validityPeriod){
      var groupOffset = groupOffsetForAsset();
      var imgMargin = margin ? margin : 0;
      var types = _.isEmpty(stopTypes) ? 1 : stopTypes.length;
      var background = createStopBackgroundImage(types, validityPeriod);
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

    var createQuestionIconStyle = function(data, margin){
      var totalStopTypes = !_.isUndefined(data.group) ?
          _.map(data.group.assetGroup, function(group) {
            return group.stopTypes.length;
          }).reduce(function(a,b){return a + b;},0) : 1;
      var numberOfGroup = !_.isUndefined(data.group) ?  data.group.assetGroup.length : 1;
      var imgMargin = margin ? margin : 0;
      return new ol.style.Style({
        image: new ol.style.Icon(({
          src: 'images/icons/questionMarkerIcon.png',
          anchor :  [0-imgMargin , (totalStopTypes * IMAGE_HEIGHT) + STICK_HEIGHT + (numberOfGroup * IMAGE_PADDING * 2) + 45],
          anchorXUnits: 'pixels',
          anchorYUnits: "pixels"
        }))
      });
    };

    var createDirectionArrowStyle = function() {
      var basePath = 'src/resources/digiroad2/bundle/assetlayer/images/';
      var directionArrowSrc, rotation;
      var stopType = _.head(data.stopTypes);
      if (selectedMassTransitStopModel.isTerminalType(stopType) || selectedMassTransitStopModel.isServicePointType(stopType)) {
        directionArrowSrc = basePath + (data.floating ? 'no-direction-warning.svg' : 'no-direction.svg');
        rotation = 0;
      } else {
        directionArrowSrc = basePath + (data.floating ? 'direction-arrow-warning.svg' : 'direction-arrow.svg');
        rotation = validitydirections.calculateRotation(data.bearing, data.validityDirection);
      }
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
            textAlign: 'left',
            offsetX: scale(beginOffset),
            offsetY: scale(-(offsetY + groupOffset)),
            fill: new ol.style.Fill({ color: '#fff'}),
            scale: styleScale
          }))
        }),
        new ol.style.Style({
          text: new ol.style.Text(({
            text: ''+name,
            textAlign: 'left',
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
            textAlign: 'left',
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
      var validityPeriod = !_.isUndefined(data.validityPeriod) ? data.validityPeriod : '';
      if(selectedMassTransitStopModel.exists()){
        if(selectedMassTransitStopModel.getId() == data.id){
            name = selectedMassTransitStopModel.getName();
            direction = selectedMassTransitStopModel.getDirection();
        }
        else
        {
          var asset = collection.getAsset(data.id);
          if(asset)
            name = (asset.data && asset.data.name) ? asset.data.name : getPropertyValue({ propertyData: asset.data.propertyData }, 'nimi_suomeksi');
        }
      }else
      {
        if(data.propertyData){
          name = selectedMassTransitStopModel.getName(data.propertyData);
          direction = selectedMassTransitStopModel.getDirection(data.propertyData);
        }
      }

      if (selectedMassTransitStopModel.isTerminalType(data.stopTypes[0]) || selectedMassTransitStopModel.isServicePointType(data.stopTypes[0]))
        direction = '';

      var styles = [];
      styles = styles.concat(createDirectionArrowStyle());
      styles = styles.concat(createStickStyle());

      /* Due the impact of the order of the concats*/
      if (!_.isEmpty(data.stopTypes) && selectedMassTransitStopModel.isServicePointType(data.stopTypes[0])) {
        styles = styles.concat(createStopTypeStyles(data.stopTypes));
      }
      else {
        styles = styles.concat(createSelectionBackgroundStyle(data.stopTypes, name+direction));
        styles = styles.concat(createStopBackgroundStyle(data.stopTypes, IMAGE_MARGIN, validityPeriod));
        styles = styles.concat(createStopTypeStyles(data.stopTypes, IMAGE_MARGIN));
        styles = styles.concat(createTextStyles(data.stopTypes, nationalId, name, direction, IMAGE_MARGIN));
      }

      styles = selectedMassTransitStopModel.isSuggested(data) ? styles.concat(createQuestionIconStyle(data, 5)) : styles;
      return styles;
    };

    var createDefaultMarkerStyles = function(){
      var validityPeriod = !_.isUndefined(data.validityPeriod) ? data.validityPeriod : '';
      var styles = [];
      styles = styles.concat(createDirectionArrowStyle());
      styles = styles.concat(createStickStyle());

      /* if it is a servicePoint don't add the background */
      if (!_.isEmpty(data.stopTypes) && !selectedMassTransitStopModel.isServicePointType(data.stopTypes[0])) {
        styles = styles.concat(createStopBackgroundStyle(data.stopTypes, 0, validityPeriod));
      }
      styles = styles.concat(createStopTypeStyles(data.stopTypes));
      styles = selectedMassTransitStopModel.isSuggested(data) ? styles.concat(createQuestionIconStyle(data, 5)) : styles;
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
        if(massTransitStopsCollection.selectedValidityPeriodsContain(asset.validityPeriod))
          height += (asset.stopTypes.length * IMAGE_HEIGHT) + (IMAGE_MARGIN * 2) + (IMAGE_PADDING * 2) - 2;
      });
      return height;
    };

    function getPropertyValue(asset, propertyName) {
      return _.chain(asset.propertyData)
        .find(function (property) { return property.publicId === propertyName; })
        .pick('values')
        .values()
        .flatten()
        .map(extractDisplayValue)
        .value()
        .join(', ');
    }

    function extractDisplayValue(value) {
        if(_.has(value, 'propertyDisplayValue')) {
            return value.propertyDisplayValue;
        } else {
            return value.propertyValue;
        }
    }

    return {
      createSelectionMarkerStyles: createSelectionMarkerStyles,
      createDefaultMarkerStyles: createDefaultMarkerStyles,
      createFeature: createFeature
    };
  };

}(this));
