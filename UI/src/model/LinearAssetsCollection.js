(function(root) {
  root.LinearAssetsCollection = function(backend, typeId, singleElementEventCategory, multiElementEventCategory) {
    var linearAssets = {};
    var dirty = false;
    var splitLinearAssets = {};

    var buildPayload = function(linearAssets, splitLinearAssets) {
      var payload = _.chain(linearAssets)
                     .reject(function(totalLinearAsset, id) {
                       return id === createTempId(splitLinearAssets.existing);
                     })
                     .values()
                     .value();
      payload.push(splitLinearAssets.existing);
      payload.push(splitLinearAssets.created);
      return payload;
    };

    var singleElementEvent = function(eventName) {
      return singleElementEventCategory + ':' + eventName;
    };

    var multiElementEvent = function(eventName) {
      return multiElementEventCategory + ':' + eventName;
    };

    this.getAll = function() {
      return _.flatten(_.values(linearAssets));
    };

    function createTempId(asset) {
      return asset.mmlId.toString() + asset.points[0].x + asset.points[0].y;
    }
    this.fetch = function(boundingBox, selectedLinearAsset) {
      var transformLinearAssets = function(linearAssets) {
        return _.chain(linearAssets)
          .groupBy(createTempId)
          .value();
      };

      backend.getLinearAssets(boundingBox, typeId, function(fetchedLinearAssets) {
        linearAssets = transformLinearAssets(fetchedLinearAssets);

        if (selectedLinearAsset.exists()) {
          var selected = selectedLinearAsset.get();
          linearAssets[createTempId(selected)] = selected;
        }

        if (splitLinearAssets.existing) {
          eventbus.trigger(multiElementEvent('fetched'), _.flatten(buildPayload(linearAssets, splitLinearAssets)));
        } else {
          eventbus.trigger(multiElementEvent('fetched'), _.flatten(_.values(linearAssets)));
        }
      });
    };

    this.fetchLinearAsset = function(id, callback) {
      if (id) {
        backend.getLinearAsset(id, function(linearAsset) {
          callback(_.merge({}, linearAssets[createTempId(linearAsset)], linearAsset));
        });
      } else {
        callback(_.merge({}, splitLinearAssets.created));
      }
    };

    this.changeLimitValue = function(asset, value) {
      if (splitLinearAssets.created) {
        splitLinearAssets.created.value = value;
      } else {
        linearAssets[createTempId(asset)].value = value;
      }
    };

    this.changeExpired = function(asset, expired) {
      if (splitLinearAssets.created) {
        splitLinearAssets.created.expired = expired;
      } else {
        linearAssets[createTempId(asset)].expired = expired;
      }
    };

    this.remove = function(asset) {
      delete linearAssets[createTempId(asset)];
    };

    this.add = function(linearAsset) {
      linearAssets[createTempId(linearAsset)] = linearAsset;
    };

    var calculateMeasure = function(asset) {
      var points = _.map(asset.points, function(point) {
        return new OpenLayers.Geometry.Point(point.x, point.y);
      });
      var geometries = new OpenLayers.Geometry.LineString(points);
      return geometries.getLength();
    };

    this.splitLinearAsset = function(id, mmlId, split) {
      backend.getLinearAsset(id, function(linearAsset) {
        var left = _.cloneDeep(linearAsset);
        var right = _.cloneDeep(linearAsset);

        left.points = split.firstSplitVertices;
        right.points = split.secondSplitVertices;

        if (calculateMeasure(left) < calculateMeasure(right)) {
          splitLinearAssets.created = left;
          splitLinearAssets.existing = right;
        } else {
          splitLinearAssets.created = right;
          splitLinearAssets.existing = left;
        }

        splitLinearAssets.created.id = null;
        splitLinearAssets.splitMeasure = split.splitMeasure;
        splitLinearAssets.splitMmlId = mmlId;
        dirty = true;
        eventbus.trigger(multiElementEvent('fetched'), _.flatten(buildPayload(linearAssets, splitLinearAssets)));
        eventbus.trigger(singleElementEvent('split'));
      });
    };

    this.saveSplit = function(splitLimit) {
      backend.splitLinearAssets(splitLinearAssets.existing.id, splitLinearAssets.splitMmlId, splitLinearAssets.splitMeasure, splitLimit.value, splitLimit.expired, function(updatedLinearAssets) {
        var existingId = splitLinearAssets.existing.id;
        splitLinearAssets = {};
        dirty = false;
        delete linearAssets[existingId];

        _.each(updatedLinearAssets, function(linearAsset) {
          linearAsset.links = [linearAsset];
          linearAsset.sideCode = linearAsset.links[0].sideCode;
          linearAssets[linearAsset.id] = linearAsset;
        });

        eventbus.trigger(multiElementEvent('fetched'), _.flatten(_.values(linearAssets)));
        eventbus.trigger(singleElementEvent('saved'), (_.find(updatedLinearAssets, function(linearAsset) {
          return existingId !== linearAsset.id;
        })));
        applicationModel.setSelectedTool('Select');
      });
    };

    this.cancelSplit = function() {
      dirty = false;
      splitLinearAssets = {};
      eventbus.trigger(multiElementEvent('fetched'), _.values(linearAssets));
    };

    this.isDirty = function() {
      return dirty;
    };
  };
})(this);
