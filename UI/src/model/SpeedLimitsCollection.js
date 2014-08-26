(function(root) {
  var getKey = function(speedLimit) {
    return speedLimit.id + '-' + speedLimit.roadLinkId;
  };

  root.SpeedLimitsCollection = function(backend) {
    var speedLimits = {};

    this.fetch = function(boundingBox) {
      backend.getSpeedLimits(boundingBox, function(fetchedSpeedLimits) {
        var selectedSpeedLimitLinks = _.pick(speedLimits, function(speedLimit) {
          return speedLimit.isSelected;
        });
        speedLimits = _.merge(_.reduce(fetchedSpeedLimits, function(acc, speedLimit) {
          acc[getKey(speedLimit)] = speedLimit;
          return acc;
        }, {}), selectedSpeedLimitLinks);
        eventbus.trigger('speedLimits:fetched', speedLimits);
      });
    };

    this.fetchSpeedLimitByLink = function(link, callback) {
      var key = getKey(link);
      backend.getSpeedLimit(speedLimits[key].id, function(speedLimit) {
        callback(_.merge({}, speedLimits[key], speedLimit));
      });
    };

    this.getSpeedLimitLinks = function(id) {
      return _.filter(_.values(speedLimits), function(link) { return link.id === id; });
    };

    this.markAsSelectedById = function(id) {
      _.each(_.filter(_.values(speedLimits), function(link) { return link.id === id; }), function(speedLimitLink) {
        speedLimitLink.isSelected = true;
      });
    };

    this.markAsDeselectedById = function(id) {
      _.each(_.filter(_.values(speedLimits), function(link) { return link.id === id; }), function(speedLimitLink) {
        speedLimitLink.isSelected = false;
      });
    };

    this.changeLimit = function(id, limit) {
      _.each(_.filter(_.values(speedLimits), function(link) { return link.id === id; }), function(speedLimitLink) {
        speedLimitLink.limit = limit;
      });
    };
  };
})(this);
