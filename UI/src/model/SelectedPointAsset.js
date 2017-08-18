(function(root) {
  root.SelectedPointAsset = function(backend, assetName, roadCollection) {
    var current = null;
    var dirty = false;
    var originalAsset;
    var endPointName = assetName;
    return {
      open: open,
      getId: getId,
      get: get,
      place: place,
      set: set,
      save: save,
      isDirty: isDirty,
      isNew: isNew,
      cancel: cancel,
      close: close,
      exists: exists,
      isSelected: isSelected,
      getAdministrativeClass: getAdministrativeClass,
      checkSelectedSign: checkSelectedSign,
      setPropertyByPublicId: setPropertyByPublicId
    };

    function place(asset) {
      dirty = true;
      current = asset;
      eventbus.trigger(assetName + ':selected');
    }

    function set(asset) {
      dirty = true;
      _.merge(current, asset, function(a, b) {
        if (_.isArray(a)) { return b; }
      });
      eventbus.trigger(assetName + ':changed');
    }

    function open(asset) {
      originalAsset = _.cloneDeep(_.omit(asset, "geometry"));
      current = asset;
      eventbus.trigger(assetName + ':selected');
    }

    function cancel() {
      if (isNew()) {
        reset();
        eventbus.trigger(assetName + ':creationCancelled');
      } else {
        dirty = false;
        current = _.cloneDeep(originalAsset);
        eventbus.trigger(assetName + ':cancelled');
      }
    }

    function reset() {
      dirty = false;
      current = null;
    }

    function getId() {
      return current && current.id;
    }

    function get() {
      return current;
    }

    function exists() {
      return !_.isNull(current);
    }

    function isDirty() {
      return dirty;
    }

    function isNew() {
      return getId() === 0;
    }

    function save() {
      eventbus.trigger(assetName + ':saving');
      current = _.omit(current, 'geometry');
      if (current.toBeDeleted) {
        backend.removePointAsset(current.id, endPointName).done(done).fail(fail);
      } else if (isNew()) {
        backend.createPointAsset(current, endPointName).done(done).fail(fail);
      } else {
        backend.updatePointAsset(current, endPointName).done(done).fail(fail);
      }

      function done() {
        eventbus.trigger(assetName + ':saved');
        close();
      }

      function fail() {
        eventbus.trigger('asset:updateFailed');
      }
    }

    function close() {
      reset();
      eventbus.trigger(assetName + ':unselected');
    }

    function isSelected(asset) {
      return getId() === asset.id;
    }

    function getAdministrativeClass(linkId){
      if(current && current.administrativeClass && !linkId)
        return current.administrativeClass;
      else {
        var road = roadCollection.getRoadLinkByLinkId(linkId);
        var administrativeClass = road ? road.getData().administrativeClass : null;
        return _.isNull(administrativeClass) || _.isUndefined(administrativeClass) ? undefined : administrativeClass;
      }
    }

    function getSelectedTrafficSignValue() {
      return parseInt(_.first(_.find(current.propertyData, function(prop){return prop.publicId === "trafficSigns_type";}).values).propertyValue);
    }

    function checkSelectedSign(trafficSignsShowing){
      if (current && (!_.contains(trafficSignsShowing, getSelectedTrafficSignValue()) &&
        getSelectedTrafficSignValue() !== undefined)) {
        close();
      }
    }

    function setPropertyByPublicId(propertyPublicId, propertyValue) {
      dirty = true;
      _.map(current.propertyData, function (prop) {
        if (prop.publicId === propertyPublicId) {
          prop.values[0] = [{propertyValue: propertyValue, propertyDisplayValue: ''}];
        }
      });
      eventbus.trigger(assetName + ':changed');
    }
  };
})(this);
