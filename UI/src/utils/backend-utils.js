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

    this.getRoadLinks = _.throttle(function(boundingBox, callback) {
      $.getJSON('api/roadlinks?bbox=' + boundingBox, function(data) {
        callback(data);
      });
    }, 1000);

    this.getRoadLinksFromVVH = _.throttle(function(boundingBox, callback) {
      $.getJSON('api/roadlinks2?bbox=' + boundingBox, function(data) {
        callback(data);
      });
    }, 1000);

    this.getManoeuvres = _.throttle(function(boundingBox, callback) {
      $.getJSON('api/manoeuvres?bbox=' + boundingBox, function(data) {
        callback(data);
      });
    }, 1000);

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

    this.getAdjacent = _.throttle(function(id, callback) {
      $.getJSON('api/roadlinks/adjacent/' + id, function(data) {
        callback(data);
      });
    }, 1000);

    this.getRoadLinkByMMLId = _.throttle(function(mmlId, callback) {
      return $.getJSON('api/roadlinks/' + mmlId, function(data) {
        return _.isFunction(callback) && callback(data);
      });
    }, 1000);

    this.getAssets = function (boundingBox) {
      self.getAssetsWithCallback(boundingBox, function (assets) {
        eventbus.trigger('assets:fetched', assets);
      });
    };

    this.getAssetsWithCallback = _.throttle(function(boundingBox, callback) {
      $.getJSON('api/massTransitStops?bbox=' + boundingBox, callback)
        .fail(function() { console.log("error"); });
    }, 1000);

    this.getSpeedLimits = _.throttle(function (boundingBox) {
      return $.getJSON('api/speedlimits?bbox=' + boundingBox);
    }, 1000);

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

    this.updateLinkProperties = _.throttle(function(mmlIds, data, success, failure) {
      $.ajax({
        contentType: "application/json",
        type: "PUT",
        url: "api/linkproperties",
        data: JSON.stringify({mmlIds: mmlIds, trafficDirection: data.trafficDirection, functionalClass: data.functionalClass, linkType: data.linkType}),
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

    this.getNumericalLimits = _.throttle(function(boundingBox, typeId, callback) {
      $.getJSON('api/numericallimits?typeId=' + typeId + '&bbox=' + boundingBox, function(numericalLimits) {
        callback(numericalLimits);
      });
    }, 1000);

    this.getNumericalLimit = _.throttle(function(id, callback) {
      $.getJSON('api/numericallimits/' + id, function(numericalLimit) {
        callback(numericalLimit);
      });
    }, 1000);

    this.updateNumericalLimit = _.throttle(function(id, value, success, failure) {
      putUpdateNumericalLimitCall(id, {value: value}, success, failure);
    }, 1000);

    this.expireNumericalLimit = _.throttle(function(id, success, failure) {
      putUpdateNumericalLimitCall(id, {expired: true}, success, failure);
    }, 1000);

    var putUpdateNumericalLimitCall = function(id, data, success, failure) {
      $.ajax({
        contentType: "application/json",
        type: "PUT",
        url: "api/numericallimits/" + id,
        data: JSON.stringify(data),
        dataType: "json",
        success: success,
        error: failure
      });
    };

    this.createNumericalLimit = _.throttle(function(typeId, roadLinkId, value, success, error) {
      $.ajax({
        contentType: "application/json",
        type: "POST",
        url: "api/numericallimits?typeId=" + typeId,
        data: JSON.stringify({roadLinkId: roadLinkId, value: value}),
        dataType: "json",
        success: success,
        error: error
      });
    }, 1000);

    this.splitNumericalLimit = function(id, roadLinkId, splitMeasure, value, expired, success, failure) {
      $.ajax({
        contentType: "application/json",
        type: "POST",
        url: "api/numericallimits/" + id,
        data: JSON.stringify({roadLinkId: roadLinkId, splitMeasure: splitMeasure, value: value, expired: expired}),
        dataType: "json",
        success: success,
        error: failure
      });
    };

    this.getMassTransitStopByNationalId = function(nationalId, callback) {
      $.get('api/massTransitStops/' + nationalId, callback);
    };

    this.getAssetTypeProperties = function(callback) {
      $.get('api/assetTypeProperties/10', callback);
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

    this.getIncompleteLinks = function() {
      return $.getJSON('api/roadLinks/incomplete');
    };

    this.getUnknownLimits = function() {
      return $.getJSON('api/speedlimits/unknown');
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

    this.getGeocode = function(address) {
      return $.post("vkm/geocode", { address: address }).then(function(x) { return JSON.parse(x); });
    };

    this.getCoordinatesFromRoadAddress = function(roadNumber, section, distance, lane) {
      return $.get("vkm/tieosoite", {tie: roadNumber, osa: section, etaisyys: distance, ajorata: lane})
        .then(function(x) { return JSON.parse(x); });
    };

    this.withRoadLinkData = function (roadLinkData) {
      self.getRoadLinks = function (boundingBox, callback) {
        callback(roadLinkData);
        eventbus.trigger('roadLinks:fetched');
      };
      self.getRoadLinksFromVVH = function(boundingBox, callback) {
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
      self.getSpeedLimits = function(boundingBox) {
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
      self.getAssetTypeProperties = function(callback) {
        callback(assetTypePropertiesData);
      };
      return self;
    };
  };
}(this));
