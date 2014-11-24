(function(root) {
  root.SelectedTotalWeightLimit = function(backend, collection) {
    var current = null;
    var self = this;
    var dirty = false;
    var originalTotalWeightLimit = null;

    eventbus.on('totalWeightLimit:split', function() {
      collection.fetchTotalWeightLimit(null, function(totalWeightLimit) {
        current = totalWeightLimit;
        originalTotalWeightLimit = totalWeightLimit.limit;
        dirty = true;
        eventbus.trigger('totalWeightLimit:selected', self);
      });
    });

    this.open = function(id) {
      self.close();
      collection.fetchTotalWeightLimit(id, function(totalWeightLimit) {
        current = totalWeightLimit;
        originalTotalWeightLimit = totalWeightLimit.limit;
        collection.markAsSelected(totalWeightLimit.id);
        eventbus.trigger('totalWeightLimit:selected', self);
      });
    };

    this.close = function() {
      if (current && !dirty) {
        collection.markAsDeselected(current.id);
        var id = current.id;
        current = null;
        eventbus.trigger('totalWeightLimit:unselected', id);
      }
    };

    this.saveSplit = function() {
      collection.saveSplit();
    };

    this.cancelSplit = function() {
      var id = current.id;
      current = null;
      dirty = false;
      collection.cancelSplit();
      eventbus.trigger('totalWeightLimit:unselected', id);
    };

    this.save = function() {
      backend.updateTotalWeightLimit(current.id, current.limit, function(totalWeightLimit) {
        dirty = false;
        current = _.merge({}, current, totalWeightLimit);
        originalTotalWeightLimit = current.limit;
        eventbus.trigger('totalWeightLimit:saved', current);
      }, function() {
        eventbus.trigger('asset:updateFailed');
      });
    };

    this.cancel = function() {
      current.limit = originalTotalWeightLimit;
      collection.changeLimit(current.id, originalTotalWeightLimit);
      dirty = false;
      eventbus.trigger('totalWeightLimit:cancelled', self);
    };

    this.exists = function() {
      return current !== null;
    };

    this.getId = function() {
      return current.id;
    };

    this.getEndpoints = function() {
      return current.endpoints;
    };

    this.getLimit = function() {
      return current.limit;
    };

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

    this.setLimit = function(limit) {
      if (limit != current.limit) {
        collection.changeLimit(current.id, limit);
        current.limit = limit;
        dirty = true;
        eventbus.trigger('totalWeightLimit:limitChanged', self);
      }
    };

    this.isDirty = function() {
      return dirty;
    };

    this.isNew = function() {
      return current.id === null;
    };

    eventbus.on('totalWeightLimit:saved', function(totalWeightLimit) {
      current = totalWeightLimit;
      originalTotalWeightLimit = totalWeightLimit.limit;
      collection.markAsSelected(totalWeightLimit.id);
      dirty = false;
    });
  };
})(this);
