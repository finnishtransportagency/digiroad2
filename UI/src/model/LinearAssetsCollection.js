(function(root) {
  root.LinearAssetsCollection = function(backend, typeId, singleElementEventCategory, multiElementEventCategory) {
    var linearAssets = {};
    var dirty = false;
    var splitLinearAssets = {};

    var buildPayload = function(linearAssets, splitLinearAssets) {
      var payload = _.chain(linearAssets)
                     .reject(function(totalLinearAsset, id) {
                       return id === splitLinearAssets.existing.id.toString();
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
      return asset.mmlId.toString() + asset.startMeasure + asset.endMeasure;
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
          eventbus.trigger(multiElementEvent('fetched'), buildPayload(linearAssets, splitLinearAssets));
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

    this.splitLinearAsset = function(id, mmlId, split) {
      backend.getLinearAsset(id, function(linearAsset) {
        var splitLink = linearAsset.linearAssetLink;

        var left = _.cloneDeep(linearAssets[id]);
        var right = _.cloneDeep(linearAssets[id]);

        left.links = [{points: split.firstSplitVertices, mmlId: mmlId}];
        right.links = [{points: split.secondSplitVertices, mmlId: mmlId}];

        if (calculateMeasure(left.links) < calculateMeasure(right.links)) {
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
        eventbus.trigger(multiElementEvent('fetched'), buildPayload(linearAssets, splitLinearAssets));
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

        eventbus.trigger(multiElementEvent('fetched'), _.values(linearAssets));
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
