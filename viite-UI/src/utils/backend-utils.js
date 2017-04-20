(function (root) {
  root.Backend = function() {
    var self = this;
    this.getRoadLinks = createCallbackRequestor(function(params) {
      var zoom = params.zoom;
      var boundingBox = params.boundingBox;
      return {
        url: 'api/viite/roadlinks?zoom=' + zoom + '&bbox=' + boundingBox
      };
    });

    this.getRoadLinkByLinkId = _.throttle(function(linkId, callback) {
      return $.getJSON('api/viite/roadlinks/' + linkId, function(data) {
        return _.isFunction(callback) && callback(data);
      });
    }, 1000);

    this.getRoadLinkByMmlId = _.throttle(function(mmlId, callback) {
      return $.getJSON('api/viite/roadlinks/mml/' + mmlId, function(data) {
        return _.isFunction(callback) && callback(data);
      });
    }, 1000);

    this.getFloatingAdjacent = _.throttle(function(roadData, callback) {
      return $.getJSON('api/viite/roadlinks/adjacent?roadData=' +JSON.stringify(roadData), function(data) {
        return _.isFunction(callback) && callback(data);
      });
    }, 1000);

    this.getAdjacentsFromMultipleSources = _.throttle(function(roadData, callback) {
      return $.getJSON('api/viite/roadlinks/multiSourceAdjacents?roadData=' +JSON.stringify(roadData), function(data) {
        return _.isFunction(callback) && callback(data);
      });
    }, 1000);

    this.getTransferResult = _.throttle(function(dataTransfer, callback) {
      return $.getJSON('api/viite/roadlinks/transferRoadLink?data=' +JSON.stringify(dataTransfer), function(data) {
        return _.isFunction(callback) && callback(data);
      });
    }, 1000);

    this.createRoadAddress = _.throttle(function(data, success, failure) {
      $.ajax({
        contentType: "application/json",
        type: "PUT",
        url: "api/viite/roadlinks/roadaddress",
        data: JSON.stringify(data),
        dataType: "json",
        success: success,
        error: failure
      });
    }, 1000);

    this.createRoadAddressProject = _.throttle(function(data, success, failure) {
      $.ajax({
        contentType: "application/json",
        type: "PUT",
        url: "api/viite/roadlinks/roadaddress/project/save",
        data: JSON.stringify(data),
        dataType: "json",
        success: success,
        error: failure
      });
    }, 1000);

    this.checkIfRoadpartReserved = (function(roadnuber,startPart,endPart) {
        return $.get('api/viite/roadlinks/roadaddress/project/validatereservedlink/', {
            roadnumber: roadnuber,
            startpart: startPart,
            endpart: endPart
        })
            .then(function (x) {
             return x
            });
    });


    this.getRoadAddressProjects = _.throttle(function(callback) {
      return $.getJSON('api/viite/roadlinks/roadaddress/project/all', function(data) {
        return _.isFunction(callback) && callback(data);
      });
    }, 1000);

    this.getProjectsWithLinksById = _.throttle(function(id, callback) {
      return $.getJSON('api/viite/roadlinks/roadaddress/project/all/projectId/' + id, function(data) {
        return _.isFunction(callback) && callback(data);
      });
    }, 1000);

    this.getUserRoles = function () {
      $.get('api/viite/user/roles', function (roles) {
        eventbus.trigger('roles:fetched', roles);
      });
    };

    this.getStartupParametersWithCallback = function(callback) {
      var url = 'api/viite/startupParameters';
      $.getJSON(url, callback);
    };

    this.getGeocode = function(address) {
      return $.post("vkm/geocode", { address: address }).then(function(x) { return JSON.parse(x); });
    };

    this.getCoordinatesFromRoadAddress = function(roadNumber, section, distance, lane) {
      return $.get("vkm/tieosoite", {tie: roadNumber, osa: section, etaisyys: distance, ajorata: lane})
        .then(function(x) { return JSON.parse(x); });
    };

    function createCallbackRequestor(getParameters) {
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

    this.withStartupParameters = function(startupParameters) {
      self.getStartupParametersWithCallback = function(callback) { callback(startupParameters); };
      return self;
    };

  };
}(this));
