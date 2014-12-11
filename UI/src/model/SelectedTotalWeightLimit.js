(function(root) {
  root.SelectedTotalWeightLimit = function(backend, collection) {
    var current = null;
    var self = this;
    var dirty = false;
    var originalTotalWeightLimit = null;
    var originalExpired = null;

    eventbus.on('totalWeightLimit:split', function() {
      collection.fetchWeightLimit(null, function(totalWeightLimit) {
        current = totalWeightLimit;
        current.isSplit = true;
        originalTotalWeightLimit = totalWeightLimit.value;
        originalExpired = totalWeightLimit.expired;
        dirty = true;
        eventbus.trigger('totalWeightLimit:selected', self);
      });
    });

    this.open = function(id) {
      self.close();
      collection.fetchWeightLimit(id, function(totalWeightLimit) {
        current = totalWeightLimit;
        originalTotalWeightLimit = totalWeightLimit.value;
        originalExpired = totalWeightLimit.expired;
        collection.markAsSelected(totalWeightLimit.id);
        eventbus.trigger('totalWeightLimit:selected', self);
      });
    };

    this.create = function(roadLinkId, points) {
      self.close();
      var endpoints = [_.first(points), _.last(points)];
      originalTotalWeightLimit = null;
      originalExpired = true;
      current = {
        id: null,
        roadLinkId: roadLinkId,
        endpoints: endpoints,
        value: null,
        expired: true,
        sideCode: 1,
        links: [{
          roadLinkId: roadLinkId,
          points: points
        }]
      };
      eventbus.trigger('totalWeightLimit:selected', self);
    };

    this.close = function() {
      if (current && !dirty) {
        var id = current.id;
        var roadLinkId = current.roadLinkId;
        if (id) {
          collection.markAsDeselected(id);
        }
        if (current.expired) {
          collection.remove(id);
        }
        current = null;
        eventbus.trigger('totalWeightLimit:unselected', id, roadLinkId);
      }
    };

    this.cancelSplit = function() {
      var id = current.id;
      current = null;
      dirty = false;
      collection.cancelSplit();
      eventbus.trigger('totalWeightLimit:unselected', id);
    };

    this.save = function() {
      var success = function(totalWeightLimit) {
        var wasNew = isNew();
        dirty = false;
        current = _.merge({}, current, totalWeightLimit);
        originalTotalWeightLimit = current.value;
        originalExpired = current.expired;
        if (wasNew) {
          collection.add(current);
        }
        eventbus.trigger('totalWeightLimit:saved', current);
      };
      var failure = function() {
        eventbus.trigger('asset:updateFailed');
      };

      if (current.isSplit) {
        collection.saveSplit(current);
      } else {
        if (expired() && isNew()) {
          return;
        }

        if (expired()) {
          expire(success, failure);
        } else if (isNew()) {
          createNew(success, failure);
        } else {
          update(success, failure);
        }
      }
    };

    var expire = function(success, failure) {
      backend.expireTotalWeightLimit(current.id, success, failure);
    };

    var update = function(success, failure) {
      backend.updateTotalWeightLimit(current.id, current.value, success, failure);
    };

    var createNew = function(success, failure) {
      backend.createTotalWeightLimit(current.roadLinkId, current.value, success, failure);
    };

    this.cancel = function() {
      if (current.isSplit) {
        self.cancelSplit();
        return;
      }
      current.value = originalTotalWeightLimit;
      current.expired = originalExpired;
      if (!isNew()) {
        collection.changeLimitValue(current.id, originalTotalWeightLimit);
        collection.changeExpired(current.id, originalExpired);
      }
      dirty = false;
      eventbus.trigger('totalWeightLimit:cancelled', self);
    };

    var exists = function() {
      return current !== null;
    };

    this.exists = exists;

    this.getId = function() {
      return current.id;
    };

    this.getRoadLinkId = function() {
      return current.roadLinkId;
    };

    this.getEndpoints = function() {
      return current.endpoints;
    };

    this.getValue = function() {
      return current.value;
    };

    var expired = function() {
      return current.expired;
    };

    this.expired = expired;

    this.getModifiedBy = function() {
      return current.modifiedBy;
    };

    this.getModifiedDateTime = function() {
      return current.modifiedDateTime;
    };

    this.getCreatedBy = function() {
      return current.createdBy;
    };

    this.getCreatedDateTime = function() {
      return current.createdDateTime;
    };

    this.get = function() {
      return current;
    };

    this.setValue = function(value) {
      if (value != current.value) {
        if (!isNew()) { collection.changeLimitValue(current.id, value); }
        current.value = value;
        dirty = true;
        eventbus.trigger('totalWeightLimit:limitChanged', self);
      }
    };

    this.setExpired = function(expired) {
      if (expired != current.expired) {
        if (!isNew()) { collection.changeExpired(current.id, expired); }
        current.expired = expired;
        dirty = true;
        eventbus.trigger('totalWeightLimit:expirationChanged', self);
      }
    };

    this.isDirty = function() {
      return dirty;
    };

    var isNew = function() {
      return exists() && current.id === null;
    };

    this.isNew = isNew;

    eventbus.on('totalWeightLimit:saved', function(totalWeightLimit) {
      current = totalWeightLimit;
      originalTotalWeightLimit = totalWeightLimit.value;
      originalExpired = totalWeightLimit.expired;
      collection.markAsSelected(totalWeightLimit.id);
      dirty = false;
    });
  };
})(this);
