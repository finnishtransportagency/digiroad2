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

    var transformLinearAssets = function(linearAssets) {
      return _.chain(linearAssets)
        .groupBy('id')
        .map(function(values, key) {
          return [key, { id: values[0].id, links: _.map(values, function(value) {
            return {
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
      return _.values(linearAssets);
    };

    this.fetch = function(boundingBox, selectedLinearAsset) {
      backend.getLinearAssets(boundingBox, typeId, function(fetchedLinearAssets) {
        var selected = selectedLinearAsset.exists() ? linearAssets[selectedLinearAsset.getId()] : undefined;

        linearAssets = transformLinearAssets(fetchedLinearAssets);

        if (selected && !linearAssets[selected.id]) {
          linearAssets[selected.id] = selected;
        } else if (selected) {
          var selectedInCollection = linearAssets[selected.id];
          selectedInCollection.value = selected.value;
          selectedInCollection.expired = selected.expired;
        }

        var newLinearAsset = [];
        if (selectedLinearAsset.isNew() && selectedLinearAsset.isDirty()) {
          newLinearAsset = [selectedLinearAsset.get()];
        }

        if (splitLinearAssets.existing) {
          eventbus.trigger(multiElementEvent('fetched'), buildPayload(linearAssets, splitLinearAssets));
        } else {
          eventbus.trigger(multiElementEvent('fetched'), _.values(linearAssets).concat(newLinearAsset));
        }
      });
    };

    this.fetchLinearAsset = function(id, callback) {
      if (id) {
        backend.getLinearAsset(id, function(linearAsset) {
          callback(_.merge({}, linearAssets[id], linearAsset));
        });
      } else {
        callback(_.merge({}, splitLinearAssets.created));
      }
    };

    this.changeLimitValue = function(id, value) {
      if (splitLinearAssets.created) {
        splitLinearAssets.created.value = value;
      } else {
        linearAssets[id].value = value;
      }
    };

    this.changeExpired = function(id, expired) {
      if (splitLinearAssets.created) {
        splitLinearAssets.created.expired = expired;
      } else {
        linearAssets[id].expired = expired;
      }
    };

    this.remove = function(id) {
      delete linearAssets[id];
    };

    this.add = function(linearAsset) {
      linearAssets[linearAsset.id] = linearAsset;
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

    this.splitLinearAssets = function(id, mmlId, split) {
      backend.getLinearAsset(id, function(linearAsset) {
        var linearAssetLinks = linearAsset.linearAssetLinks;
        var splitLink = _.find(linearAssetLinks, function(link) {
          return link.mmlId === mmlId;
        });
        var position = splitLink.position;
        var towardsLinkChain = splitLink.towardsLinkChain;

        var left = _.cloneDeep(linearAssets[id]);
        var right = _.cloneDeep(linearAssets[id]);

        var leftLinks = _.filter(_.cloneDeep(linearAssetLinks), function(it) {
          return it.position < position;
        });

        var rightLinks = _.filter(_.cloneDeep(linearAssetLinks), function(it) {
          return it.position > position;
        });

        left.links = leftLinks.concat([{points: towardsLinkChain ? split.firstSplitVertices : split.secondSplitVertices,
                                        position: position,
                                        mmlId: mmlId}]);

        right.links = [{points: towardsLinkChain ? split.secondSplitVertices : split.firstSplitVertices,
                        position: position,
                        mmlId: mmlId}].concat(rightLinks);

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
          linearAsset.links = linearAsset.linearAssetLinks;
          delete linearAsset.linearAssetLinks;
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
