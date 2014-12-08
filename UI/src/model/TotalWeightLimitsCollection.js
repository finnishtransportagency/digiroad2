(function(root) {
  root.TotalWeightLimitsCollection = function(backend) {
    var totalWeightLimits = {};
    var dirty = false;

    var splitTotalWeightLimits = {};

    this.getAll = function() {
      return _.values(totalWeightLimits);
    };

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
          }), sideCode: values[0].sideCode, limit: values[0].limit, expired: values[0].expired }];
        })
        .object()
        .value();
    };

    this.fetch = function(boundingBox, selectedTotalWeightLimit) {
      backend.getTotalWeightLimits(boundingBox, function(fetchedTotalWeightLimits) {
        var selected = _.find(_.values(totalWeightLimits), function(totalWeightLimit) { return totalWeightLimit.isSelected; });

        totalWeightLimits = transformTotalWeightLimits(fetchedTotalWeightLimits);

        if (selected && !totalWeightLimits[selected.id]) {
          totalWeightLimits[selected.id] = selected;
        } else if (selected) {
          var selectedInCollection = totalWeightLimits[selected.id];
          selectedInCollection.isSelected = selected.isSelected;
          selectedInCollection.limit = selected.limit;
          selectedInCollection.expired = selected.expired;
        }

        var newTotalWeightLimit = [];
        if (selectedTotalWeightLimit.isNew() && selectedTotalWeightLimit.isDirty()) {
          newTotalWeightLimit = [selectedTotalWeightLimit.get()];
        }

        if (splitTotalWeightLimits.existing) {
          eventbus.trigger('totalWeightLimits:fetched', buildPayload(totalWeightLimits, splitTotalWeightLimits));
        } else {
          eventbus.trigger('totalWeightLimits:fetched', _.values(totalWeightLimits).concat(newTotalWeightLimit));
        }
      });
    };

    this.fetchTotalWeightLimit = function(id, callback) {
      if (id) {
        backend.getTotalWeightLimit(id, function(totalWeightLimit) {
          callback(_.merge({}, totalWeightLimits[id], totalWeightLimit));
        });
      } else {
        callback(_.merge({}, splitTotalWeightLimits.created));
      }
    };

    this.markAsSelected = function(id) {
      console.log('mark as selected: ', id);
      totalWeightLimits[id].isSelected = true;
    };

    this.markAsDeselected = function(id) {
      totalWeightLimits[id].isSelected = false;
    };

    this.changeLimit = function(id, limit) {
      if (splitTotalWeightLimits.created) {
        splitTotalWeightLimits.created.limit = limit;
      } else {
        totalWeightLimits[id].limit = limit;
      }
    };

    this.changeExpired = function(id, expired) {
      if (splitTotalWeightLimits.created) {
        splitTotalWeightLimits.created.expired = expired;
      } else {
        totalWeightLimits[id].expired = expired;
      }
    };

    this.remove = function(id) {
      delete totalWeightLimits[id];
    };

    this.add = function(weightLimit) {
      totalWeightLimits[weightLimit.id] = weightLimit;
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
      backend.getTotalWeightLimit(id, function(totalWeightLimit) {
        var totalWeightLimitLinks = totalWeightLimit.totalWeightLimitLinks;
        var splitLink = _.find(totalWeightLimitLinks, function(link) {
          return link.roadLinkId === roadLinkId;
        });
        var position = splitLink.position;
        var towardsLinkChain = splitLink.towardsLinkChain;

        var left = _.cloneDeep(totalWeightLimits[id]);
        var right = _.cloneDeep(totalWeightLimits[id]);

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
          splitTotalWeightLimits.created = left;
          splitTotalWeightLimits.existing = right;
        } else {
          splitTotalWeightLimits.created = right;
          splitTotalWeightLimits.existing = left;
        }

        splitTotalWeightLimits.created.id = null;
        splitTotalWeightLimits.splitMeasure = split.splitMeasure;
        splitTotalWeightLimits.splitRoadLinkId = roadLinkId;
        dirty = true;
        eventbus.trigger('totalWeightLimits:fetched', buildPayload(totalWeightLimits, splitTotalWeightLimits));
        eventbus.trigger('totalWeightLimit:split');
      });
    };

    this.saveSplit = function() {
      backend.splitTotalWeightLimit(splitTotalWeightLimits.existing.id, splitTotalWeightLimits.splitRoadLinkId, splitTotalWeightLimits.splitMeasure, splitTotalWeightLimits.created.limit, function(updatedTotalWeightLimits) {
        var existingId = splitTotalWeightLimits.existing.id;
        splitTotalWeightLimits = {};
        dirty = false;
        delete totalWeightLimits[existingId];

        _.each(updatedTotalWeightLimits, function(totalWeightLimit) {
          totalWeightLimit.links = totalWeightLimit.totalWeightLimitLinks;
          delete totalWeightLimit.totalWeightLimitLinks;
          totalWeightLimit.sideCode = totalWeightLimit.links[0].sideCode;
          totalWeightLimits[totalWeightLimit.id] = totalWeightLimit;
        });

        eventbus.trigger('totalWeightLimits:fetched', _.values(totalWeightLimits));
        eventbus.trigger('totalWeightLimit:saved', (_.find(updatedTotalWeightLimits, function(totalWeightLimit) {
          return existingId !== totalWeightLimit.id;
        })));
        applicationModel.setSelectedTool('Select');
      });
    };

    this.cancelSplit = function() {
      dirty = false;
      splitTotalWeightLimits = {};
      eventbus.trigger('totalWeightLimits:fetched', _.values(totalWeightLimits));
    };

    this.isDirty = function() {
      return dirty;
    };

  };
})(this);
