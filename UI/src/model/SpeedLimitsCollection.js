(function(root) {
  var getKey = function(speedLimit) {
    return speedLimit.id + '-' + speedLimit.roadLinkId;
  };

  root.SpeedLimitsCollection = function(backend) {
    var speedLimits = {};

    this.fetch = function(boundingBox) {
      backend.getSpeedLimits(boundingBox, function(fetchedSpeedLimits) {
        var selectedSpeedLimit = _.pick(speedLimits, function(speedLimit) {
          return speedLimit.isSelected;
        });
        speedLimits = _.merge(selectedSpeedLimit, _.reduce(fetchedSpeedLimits, function(acc, speedLimit) {
          acc[getKey(speedLimit)] = speedLimit;
          return acc;
        }, {}));
        eventbus.trigger('speedLimits:fetched', speedLimits);
      });
    };

    this.get = function(id, callback) {
      backend.getSpeedLimit(speedLimits[id].id, function(speedLimit) {
        callback(_.merge({}, speedLimits[id], speedLimit));
      });
    };
  };
})(this);
