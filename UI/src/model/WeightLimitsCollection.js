(function(root) {
  root.WeightLimitsCollection = function(getWeightLimit, getWeightLimits, splitWeightLimit, singleElementEventCategory, multiElementEventCategory) {
    var weightLimits = {};
    var dirty = false;
    var splitWeightLimits = {};

    var buildPayload = function(weightLimits, splitWeightLimits) {
      var payload = _.chain(weightLimits)
                     .reject(function(totalWeightLimit, id) {
                       return id === splitWeightLimits.existing.id.toString();
                     })
                     .values()
                     .value();
      payload.push(splitWeightLimits.existing);
      payload.push(splitWeightLimits.created);
      return payload;
    };

    var transformWeightLimits = function(weightLimits) {
      return _.chain(weightLimits)
        .groupBy('id')
        .map(function(values, key) {
          return [key, { id: values[0].id, links: _.map(values, function(value) {
            return {
              roadLinkId: value.roadLinkId,
              position: value.position,
              points: value.points
            };
          }), sideCode: values[0].sideCode, value: values[0].value, expired: values[0].expired }];
        })
        .object()
        .value();
    };

    var singleElementEvent = function(eventName) {
      return singleElementEventCategory + ':' + eventName;
    };

    var multiElementEvent = function(eventName) {
      return multiElementEventCategory + ':' + eventName;
    };

    this.getAll = function() {
      return _.values(weightLimits);
    };

    this.fetch = function(boundingBox, selectedWeightLimit) {
      getWeightLimits(boundingBox, function(fetchedWeightLimits) {
        var selected = _.find(_.values(weightLimits), function(weightLimit) { return weightLimit.isSelected; });

        weightLimits = transformWeightLimits(fetchedWeightLimits);

        if (selected && !weightLimits[selected.id]) {
          weightLimits[selected.id] = selected;
        } else if (selected) {
          var selectedInCollection = weightLimits[selected.id];
          selectedInCollection.isSelected = selected.isSelected;
          selectedInCollection.value = selected.value;
          selectedInCollection.expired = selected.expired;
        }

        var newWeightLimit = [];
        if (selectedWeightLimit.isNew() && selectedWeightLimit.isDirty()) {
          newWeightLimit = [selectedWeightLimit.get()];
        }

        if (splitWeightLimits.existing) {
          eventbus.trigger(multiElementEvent('fetched'), buildPayload(weightLimits, splitWeightLimits));
        } else {
          eventbus.trigger(multiElementEvent('fetched'), _.values(weightLimits).concat(newWeightLimit));
        }
      });
    };

    this.fetchWeightLimit = function(id, callback) {
      if (id) {
        getWeightLimit(id, function(weightLimit) {
          callback(_.merge({}, weightLimits[id], weightLimit));
        });
      } else {
        callback(_.merge({}, splitWeightLimits.created));
      }
    };

    this.markAsSelected = function(id) {
      weightLimits[id].isSelected = true;
    };

    this.markAsDeselected = function(id) {
      weightLimits[id].isSelected = false;
    };

    this.changeLimitValue = function(id, value) {
      if (splitWeightLimits.created) {
        splitWeightLimits.created.value = value;
      } else {
        weightLimits[id].value = value;
      }
    };

    this.changeExpired = function(id, expired) {
      if (splitWeightLimits.created) {
        splitWeightLimits.created.expired = expired;
      } else {
        weightLimits[id].expired = expired;
      }
    };

    this.remove = function(id) {
      delete weightLimits[id];
    };

    this.add = function(weightLimit) {
      weightLimits[weightLimit.id] = weightLimit;
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

    this.splitWeightLimit = function(id, roadLinkId, split) {
      getWeightLimit(id, function(weightLimit) {
        var weightLimitLinks = weightLimit.weightLimitLinks;
        var splitLink = _.find(weightLimitLinks, function(link) {
          return link.roadLinkId === roadLinkId;
        });
        var position = splitLink.position;
        var towardsLinkChain = splitLink.towardsLinkChain;

        var left = _.cloneDeep(weightLimits[id]);
        var right = _.cloneDeep(weightLimits[id]);

        var leftLinks = _.filter(_.cloneDeep(weightLimitLinks), function(it) {
          return it.position < position;
        });

        var rightLinks = _.filter(_.cloneDeep(weightLimitLinks), function(it) {
          return it.position > position;
        });

        left.links = leftLinks.concat([{points: towardsLinkChain ? split.firstSplitVertices : split.secondSplitVertices,
                                        position: position,
                                        roadLinkId: roadLinkId}]);

        right.links = [{points: towardsLinkChain ? split.secondSplitVertices : split.firstSplitVertices,
                        position: position,
                        roadLinkId: roadLinkId}].concat(rightLinks);

        if (calculateMeasure(left.links) < calculateMeasure(right.links)) {
          splitWeightLimits.created = left;
          splitWeightLimits.existing = right;
        } else {
          splitWeightLimits.created = right;
          splitWeightLimits.existing = left;
        }

        splitWeightLimits.created.id = null;
        splitWeightLimits.splitMeasure = split.splitMeasure;
        splitWeightLimits.splitRoadLinkId = roadLinkId;
        dirty = true;
        eventbus.trigger(multiElementEvent('fetched'), buildPayload(weightLimits, splitWeightLimits));
        eventbus.trigger(singleElementEvent('split'));
      });
    };

    this.saveSplit = function(splitLimit) {
      splitWeightLimit(splitWeightLimits.existing.id, splitWeightLimits.splitRoadLinkId, splitWeightLimits.splitMeasure, splitLimit.value, splitLimit.expired, function(updatedWeightLimits) {
        var existingId = splitWeightLimits.existing.id;
        splitWeightLimits = {};
        dirty = false;
        delete weightLimits[existingId];

        _.each(updatedWeightLimits, function(weightLimit) {
          weightLimit.links = weightLimit.weightLimitLinks;
          delete weightLimit.weightLimitLinks;
          weightLimit.sideCode = weightLimit.links[0].sideCode;
          weightLimits[weightLimit.id] = weightLimit;
        });

        eventbus.trigger(multiElementEvent('fetched'), _.values(weightLimits));
        eventbus.trigger(singleElementEvent('saved'), (_.find(updatedWeightLimits, function(weightLimit) {
          return existingId !== weightLimit.id;
        })));
        applicationModel.setSelectedTool('Select');
      });
    };

    this.cancelSplit = function() {
      dirty = false;
      splitWeightLimits = {};
      eventbus.trigger(multiElementEvent('fetched'), _.values(weightLimits));
    };

    this.isDirty = function() {
      return dirty;
    };
  };
})(this);
