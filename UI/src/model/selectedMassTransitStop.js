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
      'floating',
      'municipalityCode'];
    var massTransitStopTypePublicId = "pysakin_tyyppi";
    var administratorInfoPublicId = "tietojen_yllapitaja";
    var PRECONDITION_FAILED_412 = 412;
    var NON_AUTHORITATIVE_INFORMATION_203 = 203;
    var FAILED_DEPENDENCY_424 = 424;
    var assetHasBeenModified = false;
    var currentAsset = {};
    var changedProps = [];

    function getMethodToRequestAssetByType(properties) {
      return isServiceStop(properties) ? "getMassServiceStopByNationalId" : "getMassTransitStopByNationalId";
    }

    function getMethodToUpdateAssetByType(properties) {
      return isServiceStop(properties) ? "updateServiceStopAsset" : "updateAsset";
    }

    function getMethodToDeleteAssetByType(properties) {
      return isServiceStop(properties) ? "deleteAllMassServiceStopData" : "deleteAllMassTransitStopData";
    }

    function isAnAddToolOption(optionTool){
      return _.includes(['Add', 'AddTerminal', 'AddPointAsset'], optionTool);
    }

    var close = function() {
      assetHasBeenModified = false;
      currentAsset = {};
      changedProps = [];
      eventbus.trigger('asset:closed');
    };

    eventbus.on('tool:changed', function (tool) {
      if (!isAnAddToolOption(tool) && exists()) {
        backend[getMethodToRequestAssetByType(currentAsset.payload.properties)](currentAsset.payload.nationalId, function (asset) {
          eventbus.trigger('asset:fetched', asset);
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
          _.pick(property, 'publicId', 'propertyType', 'required', 'numCharacterMax'),
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
      return _.filter(properties, function(property) { return _.includes(publicIds, property.publicId); });
    };

    var payloadWithProperties = function(payload, publicIds) {

      if(_.some(publicIds, function(publicId){ return publicId === massTransitStopTypePublicId || publicId === administratorInfoPublicId; }))
        publicIds = _.union(publicIds, [massTransitStopTypePublicId, administratorInfoPublicId]) ;

      return _.merge(
        {},
        _.pickBy(payload, function(value, key) { return key != 'properties'; }),
        {
          properties: pickProperties(payload.properties, publicIds)
        });
    };

    var servicePointPropertyOrdering = [
      'lisatty_jarjestelmaan',
      'muokattu_viimeksi',
      'palvelu',
      'tarkenne',
      'palvelun_nimi',
      'palvelun_lisätieto',
      'viranomaisdataa',
      'suggest_box'];

    var getServicePointPropertyOrdering = function () {
      return servicePointPropertyOrdering;
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

        if(!_.isEmpty(currentAsset.stopTypes) && currentAsset.stopTypes[0] == '7')
          properties =  _.filter(properties, function(prop) { return _.includes(servicePointPropertyOrdering, prop.publicId);});

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
        backend[getMethodToRequestAssetByType(currentAsset.payload.properties)](currentAsset.payload.nationalId, function (asset) {
          eventbus.trigger('asset:updateCancelled', asset);
        });
      } else {
        currentAsset = {};
        eventbus.trigger('asset:creationCancelled');
      }
      eventbus.trigger('busStop:selected', 99);
    };

    eventbus.on('application:readOnly', function () {
      if (exists()) {
        backend[getMethodToRequestAssetByType(currentAsset.payload.properties)](currentAsset.payload.nationalId, function (asset) {
          eventbus.trigger('asset:fetched', asset);
        });
      }
    });

    eventbus.on('validityPeriod:changed', function(validityPeriods) {
      if (currentAsset && (!_.includes(validityPeriods, currentAsset.validityPeriod) &&
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

      var busStopTypeValue = (!_.isUndefined(asset.stopTypes) && !_.isEmpty(asset.stopTypes)) ? _.head(asset.stopTypes) : 99;
      eventbus.trigger('busStop:selected', busStopTypeValue);

      eventbus.trigger('asset:modified');
    };

    eventbus.on('asset:fetched', open, this);

    var getProperties = function() {
      return !_.isUndefined(currentAsset.payload) ? currentAsset.payload.properties : undefined;
    };

    var getPropertyMetadata = function(publicId) {
      return _.find(currentAsset.propertyMetadata, function(metadata) {
        return metadata.publicId === publicId;
      });
    };

    var getMunicipalityCode = function() {
     return !_.isUndefined(currentAsset.payload.municipalityCode) ? currentAsset.payload.municipalityCode : selectedMassTransitStopModel.getRoadLink().getData().municipalityCode;
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

    var requiredPropertiesMissing = function () {
      var isRequiredProperty = function (publicId) {
        var isTerminal = currentAsset.stopTypes && isTerminalType(_.head(currentAsset.stopTypes)) || currentAsset.payload && isTerminalBusStop(currentAsset.payload.properties);
        var isServiceBusStop = currentAsset.stopTypes && isServicePointType(_.head(currentAsset.stopTypes)) || currentAsset.payload && isServiceStop(currentAsset.payload.properties);

        //TODO we need to get a way to know the mandatory fields depending on the bus stop type (this was code after merging)
        if (isTerminal) {
          return 'liitetyt_pysakit' == publicId;
        } else if (isServiceBusStop)
          return 'palvelu' == publicId;

        return getPropertyMetadata(publicId).required;
      };

      var isChoicePropertyWithUnknownValue = function(property) {
        var propertyType = getPropertyMetadata(property.publicId).propertyType;
        return _.some((propertyType === "single_choice" || propertyType === "multiple_choice") && property.values, function(value) { return value.propertyValue == 99; });
      };

      return _.some(currentAsset.payload.properties, function(property) {
        return isRequiredProperty(property.publicId) && (
                isChoicePropertyWithUnknownValue(property) ||
                  _.every(property.values, function(value) { return $.trim(value.propertyValue) === ""; })
          );
      });
    };

    var save = function () {
      if (currentAsset.id === undefined) {
        if (isServicePointType(currentAsset.stopTypes[0])) {
          backend.createServiceStopAsset(currentAsset.payload, function () {
            eventbus.trigger('asset:creationFailed');
            close();
          });
        }else{
          backend.createAsset(currentAsset.payload, function (errorObject) {
            if (errorObject.status == PRECONDITION_FAILED_412) {
              eventbus.trigger('asset:creationNotFoundRoadAddressVKM');
            } else {
              eventbus.trigger('asset:creationFailed');
            }
            close();
          });
        }
      } else {
        currentAsset.payload.id = currentAsset.id;
        changedProps = _.union(changedProps, ["tietojen_yllapitaja"], ["inventointipaiva"], ["osoite_suomeksi"], ["osoite_ruotsiksi"], ["trSave"]);
        var payload = payloadWithProperties(currentAsset.payload, changedProps);
        var positionUpdated = !_.isEmpty(_.intersection(changedProps, ['lon', 'lat']));
        var payloadProperties = payload.properties;

        backend[getMethodToUpdateAssetByType(payloadProperties)](currentAsset.id, payload, function (asset) {
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
          backend[getMethodToRequestAssetByType(payloadProperties)](currentAsset.payload.nationalId, function (asset) {
            open(asset);
            if(isServiceStop(payloadProperties)){
              eventbus.trigger('asset:updateFailed', asset);
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
        var massTransitStopDirection = currentAsset.payload.validityDirection;
        return isTerminalBusStop(currentAsset.payload.properties) || isTram(currentAsset.payload.properties) || roadLinkDirection === 1 || roadLinkDirection === massTransitStopDirection;
      }
      return false;
    };

    var validateDirectionsForCreation = function () {
      if(roadCollection && !currentAsset.id){
        var roadLinkDirection = getRoadLinkDirection();
        var massTransitStopDirection = currentAsset.payload.validityDirection;
        if(roadLinkDirection != 1)
          return massTransitStopDirection != roadLinkDirection;
      }
      return true;
    };

    var getRoadLinkDirection = function(){
      var nearestLine = geometrycalculator.findNearestLine(roadCollection.getRoadsForPointAssets(), currentAsset.payload.lon, currentAsset.payload.lat);
      var linkId = nearestLine.linkId;
      if (!currentAsset.linkId)
        currentAsset.linkId = linkId;
      var directions_decode = {BothDirections: 1, TowardsDigitizing: 2, AgainstDigitizing: 3};
      return directions_decode[nearestLine.trafficDirection];
    };

    var switchDirection = function() {
      var validityDirection = validitydirections.switchDirection(getByProperty('validityDirection'));
      setProperty('vaikutussuunta', [{ propertyValue: validityDirection }]);
      currentAsset.payload.linkId = currentAsset.payload.linkId ? currentAsset.payload.linkId : currentAsset.linkId;
      currentAsset.payload.validityDirection = validityDirection;
    };

    var setProperty = function(publicId, values, propertyType, required, numCharacterMax) {
      var propertyData = {};
      if(propertyType){
        propertyData = {publicId: publicId, values: values, propertyType: propertyType, required: required, numCharacterMax: numCharacterMax};
      }
      else{
        propertyData = {publicId: publicId, values: values};
      }
      changedProps = _.union(changedProps, [publicId]);
      currentAsset.payload.properties = updatePropertyData(currentAsset.payload.properties, propertyData);
      assetHasBeenModified = true;
      eventbus.trigger('assetPropertyValue:changed', { propertyData: propertyData, id: currentAsset.id });
    };

    var setAdditionalProperty = function(publicId, values) {
      var propertyData = {publicId: publicId, values: values};
      currentAsset.payload.properties = updatePropertyData(currentAsset.payload.properties, propertyData);
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
        backend.getMassServiceStopByNationalId(assetNationalId, function (asset) {
          if (_.isUndefined(asset.success)) { eventbus.trigger('asset:fetched', asset); }
        });
        backend.getMassTransitStopByNationalId(assetNationalId, function (asset, statusMessage, errorObject) {
          if (errorObject !== undefined) {
            console.log(errorObject);

          }

          eventbus.trigger('asset:fetched', asset);
        });
      }
    };

    var changeById = function(id) {
      var anotherAssetIsSelectedAndHasNotBeenModified = exists() && currentAsset.payload.id !== id && !assetHasBeenModified;
      if (!exists() || anotherAssetIsSelectedAndHasNotBeenModified) {
        if (exists()) { close(); }
        backend.getMassServiceStopById(id, function (asset) {
          eventbus.trigger('asset:fetched', asset);
        });
        backend.getMassTransitStopById(id, function (asset, statusMessage, errorObject) {
          if (errorObject !== undefined) {
            console.log(errorObject);
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

    var getByProperty = function(key) {
      if (exists()) {
        return currentAsset.payload[key];
      }
    };

    var get = function() {
      if (exists()) {
          var nearestLine = geometrycalculator.findNearestLine(roadCollection.getRoadsForPointAssets(), currentAsset.payload.lon, currentAsset.payload.lat);
          var linkId = nearestLine.linkId;
          if (!currentAsset.linkId)
              currentAsset.linkId = linkId;
          return currentAsset;
      }
    };

    var getRoadLink = function(){
      if(_.isEmpty(currentAsset))
        return {};
      return roadCollection.getRoadLinkByLinkId(currentAsset.roadLinkId ? currentAsset.roadLinkId : currentAsset.linkId);
    };

    var getCurrentRoadLink = function(){
      if(_.isEmpty(currentAsset))
        return {};
      var linkOpt = _.find([currentAsset.payload.roadLinkId, currentAsset.payload.linkId, currentAsset.linkId], function(id){return !_.isUndefined(id);});
      return roadCollection.getRoadLinkByLinkId(linkOpt);
    };

    var deleteMassTransitStop = function (poistaSelected) {
      if (poistaSelected) {
        var currAsset = this.getCurrentAsset();
        backend[getMethodToDeleteAssetByType(currAsset.payload.properties)](currAsset.id, function () {
          assetHasBeenModified = false;
          eventbus.trigger('massTransitStopDeleted', currAsset);
        }, function (errorObject) {
          cancel();
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
      var stopRoadLink = getRoadLink();
      return stopRoadLink ? (stopRoadLink.getData().administrativeClass === 'State') : false;
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

    function isServiceStop(properties) {
      return _.some(properties, function (property) {
        return property.publicId == 'pysakin_tyyppi' && _.some(property.values, function (value) {
          return value.propertyValue == "7";
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

    function isTram(properties) {
      return _.some(properties, function (property) {
        return property.publicId == 'pysakin_tyyppi' && _.some(property.values, function (value) {
          return value.propertyValue == "1";
        });
      });
    }

    function hasRoadAddress() {
      var stopRoadLink = getCurrentRoadLink();
      var properties = getProperties();
      if(stopRoadLink){
        return !_.isUndefined(stopRoadLink.getData().roadNumber);
      }
      var roadNumber = _.find(properties, function(property){
        return property.publicId === 'tie';
      });
      return !_.isUndefined(roadNumber) && !_.isEmpty(roadNumber.values);
    }

    function isSuggested(data) {
      if (!_.isUndefined(data)) {
        return _.some((_.isUndefined(data.payload) ? data.propertyData || data.properties : data.payload.properties), function (property) {
          return property.publicId === 'suggest_box' && !_.isEmpty(property.values) && !!parseInt(_.head(property.values).propertyValue);
        });
      } else
        return false;
    }

    function isTerminalType(busStopType) {
      return busStopType == 6;
    }

    function isServicePointType(busStopType) {
      return busStopType == 7;
    }

    return {
      close: close,
      save: save,
      isDirty: function() { return assetHasBeenModified; },
      setProperty: setProperty,
      cancel: cancel,
      exists: exists,
      change: change,
      changeByNationalId: changeByNationalId,
      changeById:changeById,
      get: get,
      getId: getId,
      getName: getName,
      getDirection: getDirection,
      getFloatingReason: getFloatingReason,
      getByProperty: getByProperty,
      getProperties: getProperties,
      switchDirection: switchDirection,
      move: move,
      requiredPropertiesMissing: requiredPropertiesMissing,
      place: place,
      hasMixedVirtualAndRealStops:hasMixedVirtualAndRealStops,
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
      isTerminalChild: isTerminalChild,
      getMunicipalityCode: getMunicipalityCode,
      hasRoadAddress: hasRoadAddress,
      setAdditionalProperty: setAdditionalProperty,
      isSuggested: isSuggested,
      isAnAddToolOption: isAnAddToolOption,
      isTerminalType: isTerminalType,
      isServicePointType: isServicePointType,
      getServicePointPropertyOrdering: getServicePointPropertyOrdering
    };
  };

})(window.SelectedMassTransitStop = window.SelectedMassTransitStop || {});
