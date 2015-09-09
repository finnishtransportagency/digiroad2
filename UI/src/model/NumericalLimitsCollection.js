(function(root) {
  root.NumericalLimitsCollection = function(backend, typeId, singleElementEventCategory, multiElementEventCategory) {
    var numericalLimits = {};
    var dirty = false;
    var splitNumericalLimits = {};

    var buildPayload = function(numericalLimits, splitNumericalLimits) {
      var payload = _.chain(numericalLimits)
                     .reject(function(totalNumericalLimit, id) {
                       return id === splitNumericalLimits.existing.id.toString();
                     })
                     .values()
                     .value();
      payload.push(splitNumericalLimits.existing);
      payload.push(splitNumericalLimits.created);
      return payload;
    };

    var transformNumericalLimits = function(numericalLimits) {
      return _.chain(numericalLimits)
        .groupBy('id')
        .map(function(values, key) {
          return [key, { id: values[0].id, links: _.map(values, function(value) {
            return {
              roadLinkId: value.roadLinkId,
              mmlId: value.mmlId,
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
      return _.values(numericalLimits);
    };

    this.fetch = function(boundingBox, selectedNumericalLimit) {
      backend.getNumericalLimits(boundingBox, typeId, function(fetchedNumericalLimits) {
        var selected = selectedNumericalLimit.exists() ? numericalLimits[selectedNumericalLimit.getId()] : undefined;

        numericalLimits = transformNumericalLimits(fetchedNumericalLimits);

        if (selected && !numericalLimits[selected.id]) {
          numericalLimits[selected.id] = selected;
        } else if (selected) {
          var selectedInCollection = numericalLimits[selected.id];
          selectedInCollection.value = selected.value;
          selectedInCollection.expired = selected.expired;
        }

        var newNumericalLimit = [];
        if (selectedNumericalLimit.isNew() && selectedNumericalLimit.isDirty()) {
          newNumericalLimit = [selectedNumericalLimit.get()];
        }

        if (splitNumericalLimits.existing) {
          eventbus.trigger(multiElementEvent('fetched'), buildPayload(numericalLimits, splitNumericalLimits));
        } else {
          eventbus.trigger(multiElementEvent('fetched'), _.values(numericalLimits).concat(newNumericalLimit));
        }
      });
    };

    this.fetchNumericalLimit = function(id, callback) {
      if (id) {
        backend.getNumericalLimit(id, function(numericalLimit) {
          callback(_.merge({}, numericalLimits[id], numericalLimit));
        });
      } else {
        callback(_.merge({}, splitNumericalLimits.created));
      }
    };

    this.changeLimitValue = function(id, value) {
      if (splitNumericalLimits.created) {
        splitNumericalLimits.created.value = value;
      } else {
        numericalLimits[id].value = value;
      }
    };

    this.changeExpired = function(id, expired) {
      if (splitNumericalLimits.created) {
        splitNumericalLimits.created.expired = expired;
      } else {
        numericalLimits[id].expired = expired;
      }
    };

    this.remove = function(id) {
      delete numericalLimits[id];
    };

    this.add = function(numericalLimit) {
      numericalLimits[numericalLimit.id] = numericalLimit;
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

    this.splitNumericalLimit = function(id, roadLinkId, split) {
      backend.getNumericalLimit(id, function(numericalLimit) {
        var numericalLimitLinks = numericalLimit.numericalLimitLinks;
        var splitLink = _.find(numericalLimitLinks, function(link) {
          return link.roadLinkId === roadLinkId;
        });
        var position = splitLink.position;
        var towardsLinkChain = splitLink.towardsLinkChain;

        var left = _.cloneDeep(numericalLimits[id]);
        var right = _.cloneDeep(numericalLimits[id]);

        var leftLinks = _.filter(_.cloneDeep(numericalLimitLinks), function(it) {
          return it.position < position;
        });

        var rightLinks = _.filter(_.cloneDeep(numericalLimitLinks), function(it) {
          return it.position > position;
        });

        left.links = leftLinks.concat([{points: towardsLinkChain ? split.firstSplitVertices : split.secondSplitVertices,
                                        position: position,
                                        roadLinkId: roadLinkId}]);

        right.links = [{points: towardsLinkChain ? split.secondSplitVertices : split.firstSplitVertices,
                        position: position,
                        roadLinkId: roadLinkId}].concat(rightLinks);

        if (calculateMeasure(left.links) < calculateMeasure(right.links)) {
          splitNumericalLimits.created = left;
          splitNumericalLimits.existing = right;
        } else {
          splitNumericalLimits.created = right;
          splitNumericalLimits.existing = left;
        }

        splitNumericalLimits.created.id = null;
        splitNumericalLimits.splitMeasure = split.splitMeasure;
        splitNumericalLimits.splitRoadLinkId = roadLinkId;
        dirty = true;
        eventbus.trigger(multiElementEvent('fetched'), buildPayload(numericalLimits, splitNumericalLimits));
        eventbus.trigger(singleElementEvent('split'));
      });
    };

    this.saveSplit = function(splitLimit) {
      backend.splitNumericalLimit(splitNumericalLimits.existing.id, splitNumericalLimits.splitRoadLinkId, splitNumericalLimits.splitMeasure, splitLimit.value, splitLimit.expired, function(updatedNumericalLimits) {
        var existingId = splitNumericalLimits.existing.id;
        splitNumericalLimits = {};
        dirty = false;
        delete numericalLimits[existingId];

        _.each(updatedNumericalLimits, function(numericalLimit) {
          numericalLimit.links = numericalLimit.numericalLimitLinks;
          delete numericalLimit.numericalLimitLinks;
          numericalLimit.sideCode = numericalLimit.links[0].sideCode;
          numericalLimits[numericalLimit.id] = numericalLimit;
        });

        eventbus.trigger(multiElementEvent('fetched'), _.values(numericalLimits));
        eventbus.trigger(singleElementEvent('saved'), (_.find(updatedNumericalLimits, function(numericalLimit) {
          return existingId !== numericalLimit.id;
        })));
        applicationModel.setSelectedTool('Select');
      });
    };

    this.cancelSplit = function() {
      dirty = false;
      splitNumericalLimits = {};
      eventbus.trigger(multiElementEvent('fetched'), _.values(numericalLimits));
    };

    this.isDirty = function() {
      return dirty;
    };
  };
})(this);
