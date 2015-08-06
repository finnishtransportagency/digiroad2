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
      $.getJSON('api/roadlinks/' + mmlId, function(data) {
        callback(data);
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

    this.getSpeedLimits = _.throttle(function (boundingBox, callback) {
      $.getJSON('api/speedlimits?bbox=' + boundingBox, function (speedLimits) {
        callback(speedLimits);
      });
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

    this.updateLinkProperties = _.throttle(function(id, data, success, failure) {
      $.ajax({
        contentType: "application/json",
        type: "PUT",
        url: "api/linkproperties/" + id,
        data: JSON.stringify({trafficDirection: data.trafficDirection, functionalClass: data.functionalClass, linkType: data.linkType}),
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

    this.getAsset = function(nationalId) {
      self.getMassTransitStopByNationalId(nationalId, function(asset) {
        eventbus.trigger('asset:fetched', asset);
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

    this.getFloatingAssetsWithCallback = function(callback) {
      $.getJSON('api/floatingMassTransitStops', callback);
    };

    this.getIncompleteLinksWithCallBack = function(callback) {
      $.getJSON('api/incompleteLinks', callback);
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
      self.getSpeedLimits = function(boundingBox, callback) {
        callback(speedLimitsData);
      };
      return self;
    };

    this.withMultiSegmentSpeedLimitUpdate = function(speedLimitData, modificationData) {
      self.updateSpeedLimits = function (payload, success, failure) {
        var response = _.map(payload.ids, function(id) {
          var speedLimitLink = _.find(speedLimitData, { id: id });
          return {
            id: id,
            value: payload.value,
            points: speedLimitLink.points,
            modifiedBy: modificationData ? modificationData[id].modifiedBy : undefined,
            modifiedDateTime: modificationData ? modificationData[id].modifiedDateTime : undefined,
            createdBy: modificationData ? modificationData[id].createdBy : undefined,
            createdDateTime: modificationData ? modificationData[id].createdDateTime : undefined,
            speedLimitLinks: [_.merge({}, speedLimitLink, _.pick(payload, 'value'))]
          };
        });
        success(response);
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
