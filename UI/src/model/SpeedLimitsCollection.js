(function(root) {
  root.SpeedLimitsCollection = function(backend) {
    var speedLimits = {};
    var dirty = false;

    var splitSpeedLimits = {};

    this.getAll = function() {
      return _.values(speedLimits);
    };

    var buildPayload = function(speedLimits, splitSpeedLimits) {
      var payload = _.chain(speedLimits)
                     .reject(function(speedLimit, id) {
                       return id === splitSpeedLimits.existing.id.toString();
                     })
                     .values()
                     .value();
      payload.push(splitSpeedLimits.existing);
      payload.push(splitSpeedLimits.created);
      return payload;
    };

    var transformSpeedLimits = function(speedLimits) {
      return _.chain(speedLimits)
        .groupBy('id')
        .map(function(values, key) {
          return [key, { id: values[0].id, links: _.map(values, function(value) {
            return {
              roadLinkId: value.roadLinkId,
                 position: value.position,
                 points: value.points
            };
          }), sideCode: values[0].sideCode, limit: values[0].limit }];
        })
        .object()
        .value();
    };

    this.fetch = function(boundingBox) {
      backend.getSpeedLimits(boundingBox, function(fetchedSpeedLimits) {
        var selected = _.find(_.values(speedLimits), function(speedLimit) { return speedLimit.isSelected; });

        speedLimits = transformSpeedLimits(fetchedSpeedLimits);

        if (selected && !speedLimits[selected.id]) {
          speedLimits[selected.id] = selected;
        } else if (selected) {
          var selectedInCollection = speedLimits[selected.id];
          selectedInCollection.isSelected = selected.limit;
          selectedInCollection.limit = selected.limit;
        }

        if (splitSpeedLimits.existing) {
          eventbus.trigger('speedLimits:fetched', buildPayload(speedLimits, splitSpeedLimits));
        } else {
          eventbus.trigger('speedLimits:fetched', _.values(speedLimits));
        }
      });
    };

    this.fetchSpeedLimit = function(id, callback) {
      if (id) {
        backend.getSpeedLimit(id, function(speedLimit) {
          callback(_.merge({}, speedLimits[id], speedLimit));
        });
      } else {
        callback(_.merge({}, splitSpeedLimits.created));
      }
    };

    this.markAsSelected = function(id) {
      speedLimits[id].isSelected = true;
    };

    this.markAsDeselected = function(id) {
      speedLimits[id].isSelected = false;
    };

    this.changeLimit = function(id, limit) {
      if (splitSpeedLimits.created) {
        splitSpeedLimits.created.limit = limit;
      } else {
        speedLimits[id].limit = limit;
      }
    };

    var calculateMeasure = function(links) {
      var geometries = _.map(links, function(link) {
        var points = _.map(link.points, function(point) {
          return new OpenLayers.Geometry.Point(point.x, point.y);
        });
        return new OpenLayers.Geometry.LineString(points);
      });
      return _.reduce(geometries, function(acc, x) {
        return acc + x.getLength();
      }, 0);
    };

    this.splitSpeedLimit = function(id, roadLinkId, split) {
      backend.getSpeedLimit(id, function(speedLimit) {
        var speedLimitLinks = speedLimit.speedLimitLinks;
        var position = _.find(speedLimitLinks, function(link) {
          return link.roadLinkId === roadLinkId;
        }).position;

        var left = _.cloneDeep(speedLimits[id]);
        var right = _.cloneDeep(speedLimits[id]);

        var leftLinks = _.filter(_.cloneDeep(speedLimitLinks), function(it) {
          return it.position < position;
        });

        var rightLinks = _.filter(_.cloneDeep(speedLimitLinks), function(it) {
          return it.position > position;
        });

        left.links = leftLinks.concat([{points: split.firstSplitVertices,
                                        position: position,
                                        roadLinkId: roadLinkId}]);

        right.links = [{points: split.secondSplitVertices,
                        position: position,
                        roadLinkId: roadLinkId}].concat(rightLinks);

        if (calculateMeasure(left.links) < calculateMeasure(right.links)) {
          splitSpeedLimits.created = left;
          splitSpeedLimits.existing = right;
        } else {
          splitSpeedLimits.created = right;
          splitSpeedLimits.existing = left;
        }

        splitSpeedLimits.created.id = null;
        splitSpeedLimits.splitMeasure = split.splitMeasure;
        splitSpeedLimits.splitRoadLinkId = roadLinkId;
        dirty = true;
        eventbus.trigger('speedLimits:fetched', buildPayload(speedLimits, splitSpeedLimits));
        eventbus.trigger('speedLimit:split');
      });
    };

    this.saveSplit = function() {
      backend.splitSpeedLimit(splitSpeedLimits.existing.id, splitSpeedLimits.splitRoadLinkId, splitSpeedLimits.splitMeasure, function(updatedSpeedLimits) {
        var existingId = splitSpeedLimits.existing.id;
        splitSpeedLimits = {};
        dirty = false;
        delete speedLimits[existingId];

        _.each(updatedSpeedLimits, function(speedLimit) {
          speedLimit.links = speedLimit.speedLimitLinks;
          delete speedLimit.speedLimitLinks;
          speedLimit.sideCode = speedLimit.links[0].sideCode;
          speedLimits[speedLimit.id] = speedLimit;
        });


        eventbus.trigger('speedLimits:fetched', _.values(speedLimits));
        eventbus.trigger('speedLimit:unselected');
      });
    };

    this.cancelSplit = function() {
      dirty = false;
      splitSpeedLimits = {};
      eventbus.trigger('speedLimits:fetched', _.values(speedLimits));
    };

    this.isDirty = function() {
      return dirty;
    };

  };
})(this);
