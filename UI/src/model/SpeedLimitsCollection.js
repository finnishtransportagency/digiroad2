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

    this.fetch = function(boundingBox) {
      backend.getSpeedLimits(boundingBox, function(fetchedSpeedLimits) {
        var selected = _.find(_.values(speedLimits), function(speedLimit) { return speedLimit.isSelected; });

        speedLimits = _.chain(fetchedSpeedLimits)
          .groupBy('id')
          .map(function(values, key) {
            return [key, { id: values[0].id, links: _.map(values, function(value) {
              return {
                position: value.position,
                points: value.points
              };
            }), sideCode: values[0].sideCode, limit: values[0].limit }];
          })
          .object()
          .value();

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

    this.splitSpeedLimit = function(id, position, splitGeometry) {
      splitSpeedLimits.existing = _.cloneDeep(speedLimits[id]);
      var existing = _.filter(splitSpeedLimits.existing.links, function(it) {
        return it.position < position;
      });
      splitSpeedLimits.existing.links = existing.concat([{points: splitGeometry[0], position: position}]);

      splitSpeedLimits.created = _.cloneDeep(speedLimits[id]);
      splitSpeedLimits.created.id = null;
      var created = _.filter(splitSpeedLimits.created.links, function(it) {
        return it.position > position;
      });
      splitSpeedLimits.created.links = [{points: splitGeometry[1], position: position}].concat(created);

      dirty = true;
      eventbus.trigger('speedLimits:fetched', buildPayload(speedLimits, splitSpeedLimits));
      eventbus.trigger('speedLimit:split');
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
