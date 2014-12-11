(function(root) {
  root.TotalWeightLimitsCollection = function(getWeightLimit, getWeightLimits, splitWeightLimit, singleElementEventCategory, multiElementEventCategory) {
    var weightLimits = {};
    var dirty = false;
    var splitWeightLimits = {};

    var buildPayload = function(totalWeightLimits, splitTotalWeightLimits) {
      var payload = _.chain(totalWeightLimits)
                     .reject(function(totalWeightLimit, id) {
                       return id === splitTotalWeightLimits.existing.id.toString();
                     })
                     .values()
                     .value();
      payload.push(splitTotalWeightLimits.existing);
      payload.push(splitTotalWeightLimits.created);
      return payload;
    };

    var transformTotalWeightLimits = function(totalWeightLimits) {
      return _.chain(totalWeightLimits)
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

    this.fetch = function(boundingBox, selectedTotalWeightLimit) {
      getWeightLimits(boundingBox, function(fetchedTotalWeightLimits) {
        var selected = _.find(_.values(weightLimits), function(totalWeightLimit) { return totalWeightLimit.isSelected; });

        weightLimits = transformTotalWeightLimits(fetchedTotalWeightLimits);

        if (selected && !weightLimits[selected.id]) {
          weightLimits[selected.id] = selected;
        } else if (selected) {
          var selectedInCollection = weightLimits[selected.id];
          selectedInCollection.isSelected = selected.isSelected;
          selectedInCollection.value = selected.value;
          selectedInCollection.expired = selected.expired;
        }

        var newTotalWeightLimit = [];
        if (selectedTotalWeightLimit.isNew() && selectedTotalWeightLimit.isDirty()) {
          newTotalWeightLimit = [selectedTotalWeightLimit.get()];
        }

        if (splitWeightLimits.existing) {
          eventbus.trigger(multiElementEvent('fetched'), buildPayload(weightLimits, splitWeightLimits));
        } else {
          eventbus.trigger(multiElementEvent('fetched'), _.values(weightLimits).concat(newTotalWeightLimit));
        }
      });
    };

    this.fetchTotalWeightLimit = function(id, callback) {
      if (id) {
        getWeightLimit(id, function(totalWeightLimit) {
          callback(_.merge({}, weightLimits[id], totalWeightLimit));
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

    this.splitTotalWeightLimit = function(id, roadLinkId, split) {
      getWeightLimit(id, function(totalWeightLimit) {
        var totalWeightLimitLinks = totalWeightLimit.weightLimitLinks;
        var splitLink = _.find(totalWeightLimitLinks, function(link) {
          return link.roadLinkId === roadLinkId;
        });
        var position = splitLink.position;
        var towardsLinkChain = splitLink.towardsLinkChain;

        var left = _.cloneDeep(weightLimits[id]);
        var right = _.cloneDeep(weightLimits[id]);

        var leftLinks = _.filter(_.cloneDeep(totalWeightLimitLinks), function(it) {
          return it.position < position;
        });

        var rightLinks = _.filter(_.cloneDeep(totalWeightLimitLinks), function(it) {
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
      splitWeightLimit(splitWeightLimits.existing.id, splitWeightLimits.splitRoadLinkId, splitWeightLimits.splitMeasure, splitLimit.value, splitLimit.expired, function(updatedTotalWeightLimits) {
        var existingId = splitWeightLimits.existing.id;
        splitWeightLimits = {};
        dirty = false;
        delete weightLimits[existingId];

        _.each(updatedTotalWeightLimits, function(totalWeightLimit) {
          totalWeightLimit.links = totalWeightLimit.weightLimitLinks;
          delete totalWeightLimit.weightLimitLinks;
          totalWeightLimit.sideCode = totalWeightLimit.links[0].sideCode;
          weightLimits[totalWeightLimit.id] = totalWeightLimit;
        });

        eventbus.trigger(multiElementEvent('fetched'), _.values(weightLimits));
        eventbus.trigger(singleElementEvent('saved'), (_.find(updatedTotalWeightLimits, function(totalWeightLimit) {
          return existingId !== totalWeightLimit.id;
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
