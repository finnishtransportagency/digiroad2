(function(selectedMassTransitStop) {
  selectedMassTransitStop.initialize = function(backend, roadCollection) {
    var usedKeysFromFetchedAsset = [
      'bearing',
      'lat',
      'lon',
      'roadLinkId',
      'linkId',
      'nationalId',
      'validityDirection',
      'floating'];
    var massTransitStopTypePublicId = "pysakin_tyyppi";
    var administratorInfoPublicId = "tietojen_yllapitaja";
    var PRECONDITION_FAILED_412 = 412;
    var NON_AUTHORITATIVE_INFORMATION_203 = 203;
    var FAILED_DEPENDENCY_424 = 424;
    var assetHasBeenModified = false;
    var currentAsset = {};
    var changedProps = [];
    var assetPosition;

    var close = function() {
      assetHasBeenModified = false;
      currentAsset = {};
      changedProps = [];
      eventbus.trigger('asset:closed');
    };

    eventbus.on('tool:changed', function(tool) {
      if ((tool !== 'Add' && tool !== 'AddTerminal')  && exists()) {
        backend.getMassTransitStopByNationalId(currentAsset.payload.nationalId, function(asset) {
          if (exists()) { eventbus.trigger('asset:fetched', asset); }
        });
      }
    });

    var transformPropertyData = function(propertyData) {
      var transformValues = function(publicId, values) {
        var transformValue = function(value) {
          return {
            propertyValue: value.propertyValue,
            propertyDisplayValue: value.propertyDisplayValue,
            checked: value.checked
          };
        };

        return _.map(values.values, transformValue);
      };
      var transformProperty = function(property) {
        return _.merge(
          {},
          _.pick(property, 'publicId', 'propertyType', 'required'),
          {
            values: transformValues(_.pick(property, 'publicId'), _.pick(property, 'values'))
          });
      };
      return {
        properties: _.map(propertyData, transformProperty)
      };
    };

    var extractPublicIds = function(properties) {
      return _.map(properties, function(property) { return property.publicId; });
    };

    var updatePropertyData = function(properties, propertyData) {
      return _.reject(properties, function(property) { return property.publicId === propertyData.publicId; }).concat([propertyData]);
    };

    var pickProperties = function(properties, publicIds) {
      return _.filter(properties, function(property) { return _.contains(publicIds, property.publicId); });
    };

    var payloadWithProperties = function(payload, publicIds) {

      if(_.some(publicIds, function(publicId){ return publicId === massTransitStopTypePublicId || publicId === administratorInfoPublicId; }))
        publicIds = _.union(publicIds, [massTransitStopTypePublicId, administratorInfoPublicId]) ;

      return _.merge(
        {},
        _.pick(payload, function(value, key) { return key != 'properties'; }),
        {
          properties: pickProperties(payload.properties, publicIds)
        });
    };

    var place = function(asset, other) {
      eventbus.trigger('asset:placed', asset);
      currentAsset = asset;
      currentAsset.payload = {};
      assetHasBeenModified = true;
      var assetPosition = asset.stopTypes && asset.stopTypes.length > 0 ? { lon: asset.lon, lat: asset.lat } : undefined;
      backend.getAssetTypeProperties(assetPosition, function(properties) {
        _.find(properties, function (property) {
          return property.publicId === 'vaikutussuunta';
        }).values.map(function (value) {
          value.propertyValue = String(currentAsset.validityDirection);
          value.propertyDisplayValue = String(currentAsset.validityDirection);
          return value;
        });
        currentAsset.propertyMetadata = properties;
        currentAsset.payload = _.merge({}, _.pick(currentAsset, usedKeysFromFetchedAsset), transformPropertyData(properties));
        changedProps = extractPublicIds(currentAsset.payload.properties);
        if(other) {
          copyDataFromOtherMasTransitStop(other);
        }
        eventbus.trigger('asset:modified');
      });
    };

    var move = function(position) {
      currentAsset.payload.bearing = position.bearing;
      currentAsset.payload.lon = position.lon;
      currentAsset.payload.lat = position.lat;
      currentAsset.payload.roadLinkId = position.roadLinkId;
      currentAsset.payload.linkId = position.linkId;
      currentAsset.payload.validityDirection = position.validityDirection;
      assetHasBeenModified = true;
      changedProps = _.union(changedProps, ['bearing', 'lon', 'lat', 'roadLinkId']);
      eventbus.trigger('asset:moved', position);
    };

    var cancel = function() {
      changedProps = [];
      assetHasBeenModified = false;
      if (currentAsset.id) {
        backend.getMassTransitStopByNationalId(currentAsset.payload.nationalId, function(asset) {
          eventbus.trigger('asset:updateCancelled', asset);
        });
      } else {
        currentAsset = {};
        eventbus.trigger('asset:creationCancelled');
      }
      eventbus.trigger('terminalBusStop:selected', false);
    };

    eventbus.on('application:readOnly', function() {
      if (exists()) {
        backend.getMassTransitStopByNationalId(currentAsset.payload.nationalId, function(asset) {
          if (exists()) { eventbus.trigger('asset:fetched', asset); }
        });
      }
    });

    eventbus.on('validityPeriod:changed', function(validityPeriods) {
      if (currentAsset && (!_.contains(validityPeriods, currentAsset.validityPeriod) &&
        currentAsset.validityPeriod !== undefined)) {
        close();
      }
    });

    eventbus.on('asset:saved asset:created', function() {
      changedProps = [];
      assetHasBeenModified = false;
    });
    eventbus.on('asset:created', function(asset) {
      currentAsset.id = asset.id;
      open(asset);
    });

    var open = function(asset) {
      currentAsset.id = asset.id;
      currentAsset.propertyMetadata = asset.propertyData;
      currentAsset.payload = _.merge({}, _.pick(asset, usedKeysFromFetchedAsset), transformPropertyData(asset.propertyData));
      currentAsset.validityPeriod = asset.validityPeriod;
      eventbus.trigger('terminalBusStop:selected', asset.stopTypes[0] == 6);
      eventbus.trigger('asset:modified');
    };

    eventbus.on('asset:fetched', open, this);

    var getProperties = function() {
      return currentAsset.payload.properties;
    };

    var getPropertyMetadata = function(publicId) {
      return _.find(currentAsset.propertyMetadata, function(metadata) {
        return metadata.publicId === publicId;
      });
    };

    var pikavuoroIsAlone = function()
    {
      return _.some(currentAsset.payload.properties, function(property)
      {
        if (property.publicId == "pysakin_tyyppi") {
          return _.some(property.values, function(propertyValue){
            return (propertyValue.propertyValue == 4 && property.values.length<2) ;
          });
        }
        return false;
      });
    };

    var hasMixedVirtualAndRealStops = function()
    {
      return _.some(currentAsset.payload.properties, function(property)
      {
        if (property.publicId == massTransitStopTypePublicId) {
          return _.some(property.values, function(propertyValue){
            return (propertyValue.propertyValue == 5 && property.values.length>1) ;
          });
        }
        return false;
      });
    };

    var requiredPropertiesMissing = function() {
      var isRequiredProperty = function(publicId) {
        //ignore if it is a terminal
        //TODO we need to get a way to know the mandatory fields depending on the bus stop type (this was code after merging)
        if(currentAsset.stopTypes && currentAsset.stopTypes[0] == 6 && _.some())
          return 'liitetyt_pysakit' == publicId;
        if(currentAsset.payload && isTerminalBusStop(currentAsset.payload.properties))
          return 'liitetyt_pysakit' == publicId;
        return getPropertyMetadata(publicId).required;
      };
      var isChoicePropertyWithUnknownValue = function(property) {
        var propertyType = getPropertyMetadata(property.publicId).propertyType;
        return _.some((propertyType === "single_choice" || propertyType === "multiple_choice") && property.values, function(value) { return value.propertyValue == 99; });
      };

      return _.some(currentAsset.payload.properties, function(property) {
        return isRequiredProperty(property.publicId) && (
                isChoicePropertyWithUnknownValue(property) ||
                  _.all(property.values, function(value) { return $.trim(value.propertyValue) === ""; })
          );
      });
    };

    var save = function () {
      if (currentAsset.id === undefined) {
        backend.createAsset(currentAsset.payload, function (errorObject) {
          if (errorObject.status == FAILED_DEPENDENCY_424) {
            eventbus.trigger('asset:creationTierekisteriFailed');
          } else if (errorObject.status == PRECONDITION_FAILED_412) {
            eventbus.trigger('asset:creationNotFoundRoadAddressVKM');
          } else {
            eventbus.trigger('asset:creationFailed');
          }
          close();
        });
      } else {
        currentAsset.payload.id = currentAsset.id;
        changedProps = _.union(changedProps, ["tietojen_yllapitaja"], ["inventointipaiva"] , ["osoite_suomeksi"], ["osoite_ruotsiksi"]);
        var payload = payloadWithProperties(currentAsset.payload, changedProps);
        var positionUpdated = !_.isEmpty(_.intersection(changedProps, ['lon', 'lat']));
        backend.updateAsset(currentAsset.id, payload, function (asset) {
          changedProps = [];
          assetHasBeenModified = false;
          if (currentAsset.id != asset.id) {
            eventbus.trigger('massTransitStop:expired', currentAsset);
            eventbus.trigger('asset:created', asset);
          } else {
            open(asset);
            eventbus.trigger('asset:saved', asset, positionUpdated);
          }
        }, function (errorObject) {
          backend.getMassTransitStopByNationalId(currentAsset.payload.nationalId, function (asset) {
            open(asset);
            if (errorObject.status == FAILED_DEPENDENCY_424) {
              eventbus.trigger('asset:updateTierekisteriFailed', asset);
            } else if (errorObject.status == PRECONDITION_FAILED_412) {
              eventbus.trigger('asset:updateNotFoundRoadAddressVKM', asset);
            } else {
              eventbus.trigger('asset:updateFailed', asset);
            }
          });
        });
      }
    };

    var validateDirectionsForSave = function () {
      if(roadCollection){
        var roadLinkDirection = getRoadLinkDirection();
        var massTransitStopDirection =  currentAsset.payload.validityDirection;
        return isTerminalBusStop(currentAsset.payload.properties) || roadLinkDirection === 1 || roadLinkDirection === massTransitStopDirection;
      }else{
        return false;
      }
    };

    var validateDirectionsForCreation = function () {
      if(roadCollection){
        var roadLinkDirection = getRoadLinkDirection();
        var massTransitStopDirection = currentAsset.payload.validityDirection;
        if(roadLinkDirection != 1)
          return massTransitStopDirection != roadLinkDirection;
        return true;
      }else{
        return false;
      }
    };

    var getRoadLinkDirection = function(){
      var nearestLine = geometrycalculator.findNearestLine(roadCollection.getRoadsForMassTransitStops(), currentAsset.payload.lon, currentAsset.payload.lat);
      var linkId = nearestLine.linkId;
      if (!currentAsset.linkId)
        currentAsset.linkId = linkId;
      var directions_decode = {BothDirections: 1, TowardsDigitizing: 2, AgainstDigitizing: 3};
      return directions_decode[nearestLine.trafficDirection];
    };

    var switchDirection = function() {
      var validityDirection = validitydirections.switchDirection(get('validityDirection'));
      setProperty('vaikutussuunta', [{ propertyValue: validityDirection }]);
      currentAsset.payload.linkId = currentAsset.payload.linkId ? currentAsset.payload.linkId : currentAsset.linkId;
      currentAsset.payload.validityDirection = validityDirection;
    };

    var setProperty = function(publicId, values, propertyType, required) {
      var propertyData = {};
      if(propertyType){
        propertyData = {publicId: publicId, values: values, propertyType: propertyType, required: required};
      }
      else{
        propertyData = {publicId: publicId, values: values};
      }
      changedProps = _.union(changedProps, [publicId]);
      currentAsset.payload.properties = updatePropertyData(currentAsset.payload.properties, propertyData);
      assetHasBeenModified = true;
      eventbus.trigger('assetPropertyValue:changed', { propertyData: propertyData, id: currentAsset.id });
    };

    var getCurrentAsset = function() {
      return currentAsset;
    };

    var copyDataFromOtherMasTransitStop = function (other) {
      currentAsset.payload = other.payload;
      currentAsset.propertyMetadata = other.propertyMetadata;
    };

    var exists = function() {
      return !_.isEmpty(currentAsset);
    };

    var change = function(asset) {
      changeByNationalId(asset.nationalId);
    };

    var changeByNationalId = function(assetNationalId) {
      var anotherAssetIsSelectedAndHasNotBeenModified = exists() && currentAsset.payload.nationalId !== assetNationalId && !assetHasBeenModified;
      if (!exists() || anotherAssetIsSelectedAndHasNotBeenModified) {
        if (exists()) { close(); }
        backend.getMassTransitStopByNationalId(assetNationalId, function (asset, statusMessage, errorObject) {
          if (errorObject !== undefined) {
            if (errorObject.status == NON_AUTHORITATIVE_INFORMATION_203) {
              eventbus.trigger('asset:notFoundInTierekisteri', errorObject);
            }
          }
          eventbus.trigger('asset:fetched', asset);
        });
      }
    };

    var getId = function() {
      return currentAsset.id;
    };

    var getName = function(properties) {
      if(properties)
        return getPropertyValue({ propertyData: properties }, 'nimi_suomeksi');
      return getPropertyValue({ propertyData: getProperties() }, 'nimi_suomeksi');
    };

    var getDirection = function(properties) {
      if(properties)
        return getPropertyValue({ propertyData: properties }, 'liikennointisuuntima');
      return getPropertyValue({ propertyData: getProperties() }, 'liikennointisuuntima');
    };

    var getFloatingReason = function(){
      return getPropertyValue({ propertyData: getProperties() }, 'kellumisen_syy');
    };

    var getEndDate = function(){
      return getPropertyValue({ propertyData: getProperties() }, 'viimeinen_voimassaolopaiva');
    };

    var get = function(key) {
      if (exists()) {
        return currentAsset.payload[key];
      }
    };

    var getRoadLink = function(){
      if(_.isEmpty(currentAsset))
        return {};
      return roadCollection.getRoadLinkByLinkId(currentAsset.roadLinkId ? currentAsset.roadLinkId : currentAsset.linkId);
    };

    var deleteMassTransitStop = function (poistaSelected) {
      if (poistaSelected) {
        var currAsset = this.getCurrentAsset();
        backend.deleteAllMassTransitStopData(currAsset.id, function () {
          eventbus.trigger('massTransitStopDeleted', currAsset);
        }, function (errorObject) {
          cancel();
          if (errorObject.status == FAILED_DEPENDENCY_424) {
            eventbus.trigger('asset:deleteTierekisteriFailed');
          }
        });
      }
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

    function isAdminClassState(properties){
      if(!properties)
        properties = getProperties();

      var adminClassProperty = _.find(properties, function(property){
        return property.publicId === 'linkin_hallinnollinen_luokka';
      });

      if (adminClassProperty && !_.isEmpty(adminClassProperty.values))
        return _.some(adminClassProperty.values, function(value){ return value.propertyValue === '1'; });

      //Get administration class from roadlink
      var stopRoadlink = getRoadLink();
      return stopRoadlink ? (stopRoadlink.getData().administrativeClass === 'State') : false;
    }

    function isAdministratorHSL(properties){
      if(!properties)
        properties = getProperties();

      return _.some(properties, function(property){
        return property.publicId === 'tietojen_yllapitaja' &&
            _.some(property.values, function(value){return value.propertyValue ==='3'; });
      });
    }

    function isAdministratorELY(properties){
      if(!properties)
        properties = getProperties();

      return _.some(properties, function(property){
        return property.publicId === 'tietojen_yllapitaja' &&
            _.some(property.values, function(value){return value.propertyValue ==='2'; });
      });
    }

    function isRoadNameDif(newRoadName, publicId) {
      var properties = getProperties();

      return _.some(properties, function (property) {
        return property.publicId === publicId &&
            _.some(property.values, function (value) {
              return value.propertyValue !== newRoadName;
            });
      });
    }

    function setRoadNameFields(newRoadLinkData, public_ids) {
      eventbus.trigger('textElementValue:set', newRoadLinkData.roadNameFi, public_ids.roadNameFi);
      eventbus.trigger('textElementValue:set', newRoadLinkData.roadNameSe, public_ids.roadNameSe);
    }

    function isTerminalBusStop(properties) {
      return _.some(properties, function(property) {
        return property.publicId == 'pysakin_tyyppi' && _.some(property.values, function(value){
          return value.propertyValue == "6";
        });
      });
    }

    function isTerminalChild(properties) {
      if (!properties)
        properties = getProperties();

      return _.some(properties, function (property) {
        return property.publicId === 'liitetty_terminaaliin' && !_.isEmpty(property.values);
      });
    }

    return {
      close: close,
      save: save,
      isDirty: function() { return assetHasBeenModified; },
      setProperty: setProperty,
      cancel: cancel,
      exists: exists,
      change: change,
      changeByExternalId: changeByNationalId,
      getId: getId,
      getName: getName,
      getDirection: getDirection,
      getFloatingReason: getFloatingReason,
      get: get,
      getProperties: getProperties,
      switchDirection: switchDirection,
      move: move,
      requiredPropertiesMissing: requiredPropertiesMissing,
      place: place,
      hasMixedVirtualAndRealStops:hasMixedVirtualAndRealStops,
      pikavuoroIsAlone: pikavuoroIsAlone,
      copyDataFromOtherMasTransitStop: copyDataFromOtherMasTransitStop,
      getCurrentAsset: getCurrentAsset,
      deleteMassTransitStop: deleteMassTransitStop,
      getRoadLink: getRoadLink,
      isAdminClassState: isAdminClassState,
      isAdministratorELY: isAdministratorELY,
      isAdministratorHSL: isAdministratorHSL,
      validateDirectionsForSave : validateDirectionsForSave,
      validateDirectionsForCreation: validateDirectionsForCreation,
      getEndDate: getEndDate,
      isRoadNameDif: isRoadNameDif,
      setRoadNameFields: setRoadNameFields,
      isTerminalChild: isTerminalChild
    };
  };

})(window.SelectedMassTransitStop = window.SelectedMassTransitStop || {});
