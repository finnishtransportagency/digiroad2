(function(root) {
  root.SelectedPointAsset = function(backend, assetName, roadCollection) {
    var current = null;
    var dirty = false;
    var originalAsset;
    var endPointName = assetName;
    var isConvertedAsset;
    var selectedGroupedId;
    return {
      open: open,
      getId: getId,
      get: get,
      getByProperty: getByProperty,
      place: place,
      set: set,
      setProperties: setProperties,
      save: save,
      isDirty: isDirty,
      isNew: isNew,
      cancel: cancel,
      close: close,
      exists: exists,
      isSelected: isSelected,
      getAdministrativeClass: getAdministrativeClass,
      getConstructionType: getConstructionType,
      checkSelectedSign: checkSelectedSign,
      setPropertyByPublicId: setPropertyByPublicId,
      setPropertyByGroupedIdAndPublicId: setPropertyByGroupedIdAndPublicId,
      getPropertyByGroupedIdAndPublicId: getPropertyByGroupedIdAndPublicId,
      removePropertyByGroupedId: removePropertyByGroupedId,
      getMunicipalityCode: getMunicipalityCode,
      getMunicipalityCodeByLinkId: getMunicipalityCodeByLinkId,
      getCoordinates: getCoordinates,
      setAdditionalPanels: setAdditionalPanels,
      setAdditionalPanel: setAdditionalPanel,
      getConvertedAssetValue: getConvertedAssetValue,
      setConvertedAssetValue: setConvertedAssetValue,
      addAdditionalTrafficLight: addAdditionalTrafficLight,
      setSelectedGroupedId: setSelectedGroupedId,
      getSelectedGroupedId: getSelectedGroupedId
    };

    function place(asset) {
      selectedGroupedId = undefined;
      dirty = true;
      current = asset;
      isConvertedAsset = false;
      eventbus.trigger(assetName + ':selected');
    }

    function set(asset) {
      dirty = true;
      _.mergeWith(current, asset, function(a, b) {
        if (_.isArray(a)) { return b; }
      });
      eventbus.trigger(assetName + ':changed');
    }

    function setProperties(property) {
      dirty = true;
      _.mergeWith(current.properties, property, function(a, b){
        if(_.isArray(a)) { return b; }
      });
      eventbus.trigger(assetName + ":changed");
    }

    function addAdditionalTrafficLight(newProperties) {
      dirty = true;
      current.propertyData = current.propertyData.concat(newProperties);
    }

    function open(asset) {
      selectedGroupedId = undefined;
      originalAsset = _.cloneDeep(_.omit(asset, "geometry"));
      current = asset;
      eventbus.trigger(assetName + ':selected');
    }

    function cancel() {
      if (isNew() && !isConvertedAsset) {
        reset();
        eventbus.trigger(assetName + ':creationCancelled');
      } else {
        dirty = false;
        isConvertedAsset = false;
        current = _.cloneDeep(originalAsset);
        eventbus.trigger(assetName + ':cancelled');
      }
    }

    function reset() {
      dirty = false;
      isConvertedAsset = false;
      current = null;
      selectedGroupedId = undefined;
    }

    function getId() {
      return current && current.id;
    }

    function get() {
      return current;
    }

    function getByProperty(key) {
      if (exists()) {
        return _.find(current.propertyData, function(asset) {
          return asset.publicId === key;
        }).values[0].propertyValue;
      }
    }

    function exists() {
      return !_.isNull(current);
    }

    function isDirty() {
      return dirty;
    }

    function isNew() {
      return getId() === 0 || isConvertedAsset;
    }

    function save() {
      eventbus.trigger(assetName + ':saving');
      current = _.omit(current, 'geometry');
      // Filter property 'counter' as it is not a real property to save.
      // 'counter' is used only to group nearby TrafficSigns together in UI
      current.propertyData = _.filter(current.propertyData, function(prop) {
        return prop.publicId !== 'counter';
      });

      if (current.toBeDeleted) {
        eventbus.trigger(endPointName + ':deleted', current, 'deleted');
        backend.removePointAsset(current.id, endPointName).done(done).fail(fail);
      } else if (isNew() && !isConvertedAsset) {
        eventbus.trigger(endPointName + ':created', current, 'created');
        backend.createPointAsset(current, endPointName).done(done).fail(fail);
      } else {
        eventbus.trigger(endPointName + ':updated', current, 'updated');
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
      var road = roadCollection.getRoadLinkByLinkId(linkId);
      var administrativeClass = road ? road.getData().administrativeClass : null;
      return _.isNull(administrativeClass) || _.isUndefined(administrativeClass) ? undefined : administrativeClass;

    }

    function getConstructionType(linkId){
      var road = roadCollection.getRoadLinkByLinkId(linkId);
      var constructionType = road ? road.getData().constructionType : null;
      return _.isNull(constructionType) || _.isUndefined(constructionType) ? undefined : constructionType;
    }

    function getMunicipalityCodeByLinkId(linkId){
      if(current && current.municipalityCode && !linkId)
        return current.municipalityCode;
      var road = roadCollection.getRoadLinkByLinkId(linkId);
      var municipalityCode = road ? road.getData().municipalityCode : null;
      return _.isNull(municipalityCode) || _.isUndefined(municipalityCode) ? undefined : municipalityCode;

    }

    function getMunicipalityCode(){
      return !_.isUndefined(current.municipalityCode) ?  current.municipalityCode: roadCollection.getRoadLinkByLinkId(current.linkId).getData().municipalityCode;
    }

    function getCoordinates(){
      return {lon: current.lon, lat: current.lat};
    }

    function getSelectedTrafficSignValue() {
      return parseInt(_.head(_.find(current.propertyData, function(prop){return prop.publicId === "trafficSigns_type";}).values).propertyValue);
    }

    function checkSelectedSign(trafficSignsShowing){
      if (current && (!_.includes(trafficSignsShowing, getSelectedTrafficSignValue()) &&
        getSelectedTrafficSignValue() !== undefined)) {
        close();
      }
    }

    function setPropertyByPublicId(propertyPublicId, propertyValue) {
      dirty = true;
      _.map(current.propertyData, function (prop) {
        if (prop.publicId === propertyPublicId) {
          prop.values[0] = {propertyValue: propertyValue, propertyDisplayValue: ''};
        }
      });
      eventbus.trigger(assetName + ':changed');
    }

    function getPropertyByGroupedIdAndPublicId(propertyGroupedId, propertyPublicId) {
      return _.find(current.propertyData, function (prop) {
        return prop.groupedId == propertyGroupedId && prop.publicId == propertyPublicId;
      });
    }

    function setPropertyByGroupedIdAndPublicId(propertyGroupedId, propertyPublicId, propertyValue) {
      dirty = true;
      var property = _.find(current.propertyData, {'groupedId': propertyGroupedId, 'publicId': propertyPublicId});
      property.values[0] = {propertyValue: propertyValue, propertyDisplayValue: ''};
      eventbus.trigger(assetName + ':changed');
    }

    function removePropertyByGroupedId(propertyGroupedId) {
      dirty = true;
      _.remove(current.propertyData, {'groupedId': parseInt(propertyGroupedId)});
    }

    function setAdditionalPanels(panels) {
      dirty = true;
      _.map(current.propertyData, function (prop) {
        if (prop.publicId === 'additional_panel') {
          prop.values = panels;
        }
      });
      eventbus.trigger(assetName + ':changed');
    }

    function setAdditionalPanel(myobj) {
      dirty = true;
      _.map(current.propertyData, function (prop) {
        if (prop.publicId === 'additional_panel') {
          var index = _.findIndex(prop.values, {formPosition: myobj.formPosition});
          prop.values.splice(index, 1, myobj);
        }
      });
      eventbus.trigger(assetName + ':changed');
    }

    function getConvertedAssetValue() {
      return isConvertedAsset;
    }

    function setConvertedAssetValue(value) {
      isConvertedAsset = value;
    }

    function setSelectedGroupedId(id) {
      selectedGroupedId = id;
      eventbus.trigger(assetName + ':changed');
    }

    function getSelectedGroupedId() {
      return selectedGroupedId;
    }
  };
})(this);
