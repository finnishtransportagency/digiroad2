(function (root) {
  root.Backend = function() {
    var self = this;
    this.getEnumeratedPropertyValues = function() {
      $.getJSON('api/enumeratedPropertyValues/10', function (enumeratedPropertyValues) {
        eventbus.trigger('enumeratedPropertyValues:fetched', enumeratedPropertyValues);
      })
        .fail(function () {
          console.log("error");
        });
    };

      this.getAssetEnumeratedPropertyValues = function(assetType) {
          $.getJSON('api/enumeratedPropertyValues/'+assetType, function (enumeratedPropertyValues) {
              eventbus.trigger('assetEnumeratedPropertyValues:fetched', { assetType: assetType, enumeratedPropertyValues: enumeratedPropertyValues});
          })
              .fail(function () {
                  console.log("error");
              });
      };

    this.getAssetTypeMetadata = function(assetTypeId) {
      $.getJSON('api/getAssetTypeMetadata/'+ assetTypeId, function (getAssetTypeMetadata) {
        eventbus.trigger('getAssetTypeMetadata:fetched', getAssetTypeMetadata);
      })
          .fail(function () {
            console.log("error");
          });
    };

    this.getRoadLinks = createCallbackRequestorWithParameters(function(boundingBox) {
      return {
        url: 'api/roadlinks?bbox=' + boundingBox
      };
    });

    this.getHistoryRoadLinks = createCallbackRequestor(function(boundingBox) {
        return {
        url: 'api/roadlinks/history?bbox=' + boundingBox
      };
    });

    this.getRoadLinksWithComplementary = createCallbackRequestor(function (boundingBox) {
      return {
        url: 'api/roadlinks/complementaries?bbox=' + boundingBox
      };
    });

    this.getManoeuvres = createCallbackRequestor(function(boundingBox) {
      return {
        url: 'api/manoeuvres?bbox=' + boundingBox
      };
    });

    this.updateManoeuvreDetails = function(details, success, failure) {
      $.ajax({
        contentType: "application/json",
        type: "PUT",
        url: "api/manoeuvres",
        data: JSON.stringify(details),
        dataType: "json",
        success: success,
        error: failure
      });
    };

    this.createManoeuvres = function(manoeuvres, success, failure) {
      $.ajax({
        contentType: "application/json",
        type: "POST",
        url: "api/manoeuvres",
        data: JSON.stringify({ manoeuvres: manoeuvres }),
        dataType: "json",
        success: success,
        error: failure
      });
    };

    this.removeManoeuvres = function(manoeuvreIds, success, failure) {
      $.ajax({
        contentType: "application/json",
        type: "DELETE",
        url: "api/manoeuvres",
        data: JSON.stringify({ manoeuvreIds: manoeuvreIds }),
        dataType: "json",
        success: success,
        error: failure
      });
    };

    this.getAdjacents = _.throttle(function(ids, callback) {
      $.getJSON('api/roadlinks/adjacents/' + ids, function(data) {
        callback(data);
      });
    }, 1000);

    this.getAdjacent = _.throttle(function(id, callback) {
      $.getJSON('api/roadlinks/adjacent/' + id, function(data) {
        callback(data);
      });
    }, 1000);

    this.getRoadLinkByLinkId = _.throttle(function(linkId, callback) {
      return $.getJSON('api/roadlinks/' + linkId, function(data) {
        return _.isFunction(callback) && callback(data);
      });
    }, 1000);

    this.getRoadLinkByMmlId = _.throttle(function(mmlId, callback) {
      return $.getJSON('api/roadlinks/mml/' + mmlId, function(data) {
        return _.isFunction(callback) && callback(data);
      });
    }, 1000);

    this.getAssets = function (boundingBox, filter) {
      if(!filter)
        filter = function(assets){return assets;};

      self.getAssetsWithCallback(boundingBox, function (assets) {
        eventbus.trigger('assets:fetched',filter(assets));
      });
    };

    this.getAssetsWithCallback = createCallbackRequestor(function(boundingBox) {
      return {
        url: 'api/massTransitStops?bbox=' + boundingBox
      };
    });

    this.getSpeedLimits = latestResponseRequestor(function(boundingBox, withRoadAddress) {
      return {
        url: 'api/speedlimits?bbox=' + boundingBox + '&withRoadAddress=' + withRoadAddress
      };
    });

    this.getSpeedLimitsHistory = latestResponseRequestor(function(boundingBox) {
      return {
        url: 'api/speedlimits/history?bbox=' + boundingBox
      };
    });

    this.updateSpeedLimits = _.throttle(function(payload, success, failure) {
      $.ajax({
        contentType: "application/json",
        type: "PUT",
        url: "api/speedlimits",
        data: JSON.stringify(payload),
        dataType: "json",
        success: success,
        error: failure
      });
    }, 1000);

    this.updateLinkProperties = _.throttle(function(linkIds, data, success, failure) {
      $.ajax({
        contentType: "application/json",
        type: "PUT",
        url: "api/linkproperties",
        data: JSON.stringify(data),
        dataType: "json",
        success: success,
        error: failure
      });
    }, 1000);

    this.splitSpeedLimit = function(id, splitMeasure, createdValue, existingValue, success, failure) {
      $.ajax({
        contentType: "application/json",
        type: "POST",
        url: "api/speedlimits/" + id + "/split",
        data: JSON.stringify({splitMeasure: splitMeasure, createdValue: createdValue, existingValue: existingValue}),
        dataType: "json",
        success: success,
        error: failure
      });
    };

    this.separateSpeedLimit = function(id, valueTowardsDigitization, valueAgainstDigitization, success, failure) {
      $.ajax({
        contentType: "application/json",
        type: "POST",
        url: "api/speedlimits/" + id + "/separate",
        data: JSON.stringify({valueTowardsDigitization: valueTowardsDigitization, valueAgainstDigitization: valueAgainstDigitization}),
        dataType: "json",
        success: success,
        error: failure
      });
    };

    this.getPointAssetsWithComplementary = latestResponseRequestor(function(boundingBox, endPointName) {
      return {
        url: 'api/' + endPointName + '?bbox=' + boundingBox
      };
    });

    this.getPointAssetById = latestResponseRequestor(function(id, endPointName) {
      return {
        url: 'api/'+ endPointName + '/' + id
      };
    });

    this.getLinearAssetById = latestResponseRequestor(function(id, endPointName) {
      return {
        url: 'api/linearAsset/unchecked/' + id
      };
    });

    this.createPointAsset = function(asset, endPointName) {
      return $.ajax({
        contentType: "application/json",
        type: "POST",
        url: "api/" + endPointName,
        data: JSON.stringify({asset: asset}),
        dataType: "json"
      });
    };

    this.updatePointAsset = function(asset, endPointName) {
      return $.ajax({
        contentType: "application/json",
        type: "PUT",
        url: "api/" + endPointName + "/" + asset.id,
        data: JSON.stringify({asset: asset}),
        dataType: "json"
      });
    };

    this.removePointAsset = _.throttle(function(id, endPointName) {
      return $.ajax({
        contentType: "application/json",
        type: "DELETE",
        url: "api/"+ endPointName + "/" + id,
        dataType: "json"
      });
    }, 1000);

    this.getLinearAssets = latestResponseRequestor(function(boundingBox, typeId, withRoadAddress) {
      return {
        url: 'api/linearassets?bbox=' + boundingBox + '&typeId=' + typeId + '&withRoadAddress=' + withRoadAddress
      };
    });

    this.getLinearAssetsWithComplementary = latestResponseRequestor(function(boundingBox, typeId) {
      return {
        url: 'api/linearassets/complementary?bbox=' + boundingBox + '&typeId=' + typeId
      };
    });

    this.createLinearAssets = _.throttle(function(data, success, failure) {
      $.ajax({
        contentType: "application/json",
        type: "POST",
        url: "api/linearassets",
        data: JSON.stringify(data),
        dataType: "json",
        success: success,
        error: failure
      });
    }, 1000);

    this.deleteLinearAssets = _.throttle(function(data, success, failure) {
      if (data.typeId == 110) { // Pavement must be set to null value, not deleted
        $.ajax({
          contentType: "application/json",
          type: "POST",
          url: "api/linearassets",
          data: JSON.stringify(data),
          dataType: "json",
          success: success,
          error: failure
        });
      } else {
        $.ajax({
          contentType: "application/json",
          type: "DELETE",
          url: "api/linearassets",
          data: JSON.stringify(data),
          dataType: "json",
          success: success,
          error: failure
        });
      }
    }, 1000);

    this.splitLinearAssets = function(typeId, id, splitMeasure, createdValue, existingValue, success, failure) {
      $.ajax({
        contentType: "application/json",
        type: "POST",
        url: "api/linearassets/" + id,
        data: JSON.stringify({typeId: typeId, splitMeasure: splitMeasure, createdValue: createdValue, existingValue: existingValue}),
        dataType: "json",
        success: success,
        error: failure
      });
    };

    this.separateLinearAssets = function(typeId, id, valueTowardsDigitization, valueAgainstDigitization, success, failure) {
      $.ajax({
        contentType: "application/json",
        type: "POST",
        url: "api/linearassets/" + id + "/separate",
        data: JSON.stringify({typeId: typeId, valueTowardsDigitization: valueTowardsDigitization, valueAgainstDigitization: valueAgainstDigitization}),
        dataType: "json",
        success: success,
        error: failure
      });
    };

    this.deleteAllMassTransitStopData = function(assetId,success, failure){
      $.ajax({
        contentType: "application/json",
        type: "DELETE",
        url: "api/massTransitStops/removal",
        data: JSON.stringify({assetId: assetId}),
        dataType: "json",
        success: success,
        error: failure
      });
    };

    this.getMassTransitStopByNationalId = function(nationalId, callback) {
      $.get('api/massTransitStops/' + nationalId, callback);
    };

    this.getUserRoles = function () {
      $.get('api/user/roles', function (roles) {
        eventbus.trigger('roles:fetched', roles);
      });
    };

    this.getStartupParametersWithCallback = function(callback) {
      var url = 'api/startupParameters';
      $.getJSON(url, callback);
    };

    this.getAssetPropertyNamesWithCallback = function(callback) {
      $.getJSON('api/assetPropertyNames/fi', callback);
    };

    this.getFloatingMassTransitStops = function() {
      return $.getJSON('api/massTransitStops/floating');
    };

    this.getAssetTypeProperties = function (position, callback) {
      if (position) {
        $.get('api/massTransitStops/metadata?position=' + position.lon + ',' + position.lat, callback);
      } else {
        $.get('api/massTransitStops/metadata', callback);
      }
    };

    this.getIncompleteLinks = function() {
      return $.getJSON('api/roadLinks/incomplete');
    };

    this.getUnknownLimits = function() {
      return $.getJSON('api/speedlimits/unknown');
    };

    this.getFloatinPedestrianCrossings = function() {
      return $.getJSON('api/pedestrianCrossings/floating');
    };

    this.getFloatingTrafficLights = function() {
      return $.getJSON('api/trafficLights/floating');
    };

    this.getFloatingObstacles = function() {
      return $.getJSON('api/obstacles/floating');
    };

    this.getFloatingRailwayCrossings = function() {
      return $.getJSON('api/railwayCrossings/floating');
    };

    this.getFloatingDirectionalTrafficSigns = function() {
      return $.getJSON('api/directionalTrafficSigns/floating');
    };

    this.getFloatingTrafficSigns = function() {
      return $.getJSON('api/trafficSigns/floating');
    };

    this.getLinearAssetUnchecked = function(typeId) {
      return $.getJSON('api/linearAsset/unchecked?typeId=' + typeId);
    };

    this.createAsset = function (data, errorCallback) {
      eventbus.trigger('asset:creating');
      $.ajax({
        contentType: "application/json",
        type: "POST",
        url: "api/massTransitStops",
        data: JSON.stringify(data),
        dataType: "json",
        success: function (asset) {
          eventbus.trigger('asset:created', asset);
        },
        error: errorCallback
      });
    };

    this.updateAsset = function (id, data, successCallback, errorCallback) {
      eventbus.trigger('asset:saving');
      $.ajax({
        contentType: "application/json",
        type: "PUT",
        url: "api/massTransitStops/" + id,
        data: JSON.stringify(data),
        dataType: "json",
        success: successCallback,
        error: errorCallback
      });
    };


    this.getMassTransitStopStreetViewUrl = function test(lati,longi,heading) {
      function getJson(){
        $.getJSON("api/masstransitstopgapiurl?latitude=" + lati + "&longitude=" + longi+"&heading="+heading)
          .done(function (response) {
            $('#streetViewTemplatesgooglestreetview').replaceWith('<img id="streetViewTemplatesgooglestreetview" alt="Google StreetView-n&auml;kym&auml" src=' +response.gmapiurl +'>');
          });
      }
      if (lati && longi && heading)
        getJson();
    };

    this.copyMassTransitStopAsset = function(id, data, successCallback, errorCallback) {
      $.ajax({
        contentType: "application/json",
        type: "PUT",
        url: "api/massTransitStops/copy/" + id,
        data: JSON.stringify(data),
        dataType: "json",
        success: successCallback,
        error: errorCallback
      });
    };

    this.getGeocode = function(address) {
      return $.post("vkm/geocode", { address: address }).then(function(x) { return JSON.parse(x); });
    };

    this.getRoadLinkToPromise= function(linkid)
    {
     return $.get("api/roadlinks/" + linkid);
    };

    this.getCoordinatesFromRoadAddress = function(roadNumber, section, distance, lane) {
      return $.get("vkm/tieosoite", {tie: roadNumber, osa: section, etaisyys: distance, ajorata: lane})
        .then(function(x) { return JSON.parse(x); });
    };

    this.getMassTransitStopByNationalIdForSearch = function(nationalId) {
      return $.get('api/massTransitStopsSafe/' + nationalId);
    };
    this.getSpeedLimitsLinkIDFromSegmentID = function(sid) {
      return $.get('api/speedlimit/sid/?segmentid=' + sid);
    };

    this.getMassTransitStopByLiviIdForSearch = function(liviId) {
      return $.get('api/massTransitStops/livi/' + liviId);
    };

    function createCallbackRequestor(getParameters) {
      var requestor = latestResponseRequestor(getParameters);
      return function(parameter, callback) {
        requestor(parameter).then(callback);
      };
    }

    function createCallbackRequestorWithParameters(getParameters) {
      var requestor = latestResponseRequestor(getParameters);
      return function(parameter, callback) {
        requestor(parameter).then(callback);
      };
    }

    function latestResponseRequestor(getParameters) {
      var deferred;
      var requests = new Bacon.Bus();
      var responses = requests.debounce(200).flatMapLatest(function(params) {
        return Bacon.$.ajax(params, true);
      });

      return function() {
        if (deferred) { deferred.reject(); }
        deferred = responses.toDeferred();
        requests.push(getParameters.apply(undefined, arguments));
        return deferred.promise();
      };
    }

    this.withRoadLinkData = function (roadLinkData) {
      self.getRoadLinks = function(boundingBox, callback) {
        callback(roadLinkData);
        eventbus.trigger('roadLinks:fetched');
      };
      return self;
    };

    this.withUserRolesData = function(userRolesData) {
      self.getUserRoles = function () {
        eventbus.trigger('roles:fetched', userRolesData);
      };
      return self;
    };

    this.withEnumeratedPropertyValues = function(enumeratedPropertyValuesData) {
      self.getEnumeratedPropertyValues = function () {
          eventbus.trigger('enumeratedPropertyValues:fetched', enumeratedPropertyValuesData);
      };
      return self;
    };

    this.withAssetEnumeratedPropertyValues = function(enumeratedPropertyValuesData, typeId) {
      self.getAssetEnumeratedPropertyValues = function (typeId) {
          eventbus.trigger('assetEnumeratedPropertyValues:fetched', { assetType: assetType, enumeratedPropertyValues: enumeratedPropertyValuesData});
      };
      return self;
    };

    this.withStartupParameters = function(startupParameters) {
      self.getStartupParametersWithCallback = function(callback) { callback(startupParameters); };
      return self;
    };

    this.withAssetPropertyNamesData = function(assetPropertyNamesData) {
      self.getAssetPropertyNamesWithCallback = function(callback) { callback(assetPropertyNamesData); };
      return self;
    };

    this.withAssetsData = function(assetsData) {
      self.getAssetsWithCallback = function (boundingBox, callback) {
        callback(assetsData);
      };
      return self;
    };

    this.withAssetData = function(assetData) {
      self.getMassTransitStopByNationalId = function (externalId, callback) {
        callback(assetData);
      };
      self.updateAsset = function (id, data, successCallback) {
        eventbus.trigger('asset:saving');
        successCallback(_.defaults(data, assetData));
      };
      return self;
    };

    this.withSpeedLimitsData = function(speedLimitsData) {
      self.getSpeedLimits = function(boundingBox, withRoadAddress) {
        return $.Deferred().resolve(speedLimitsData);
      };
      return self;
    };

    this.withSpeedLimitUpdate = function() {
      self.updateSpeedLimits = function (payload, success, failure) {
        success();
      };
      return self;
    };

    this.withSpeedLimitSplitting = function(speedLimitSplitting) {
      self.splitSpeedLimit = speedLimitSplitting;
      return self;
    };

    this.withPassThroughAssetCreation = function() {
      self.createAsset = function(data) {
        eventbus.trigger('asset:created', data);
      };
      return self;
    };

    this.withAssetCreationTransformation = function(transformation) {
      self.createAsset = function(data) {
        eventbus.trigger('asset:created', transformation(data));
      };
      return self;
    };

    this.withAssetTypePropertiesData = function(assetTypePropertiesData) {
      self.getAssetTypeProperties = function(position, callback) {
        callback(assetTypePropertiesData);
      };
      return self;
    };
  };
}(this));
