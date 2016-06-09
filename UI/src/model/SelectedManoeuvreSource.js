(function(root) {
  root.SelectedManoeuvreSource = function(collection) {
    var current = null;

    //----------------------------------
    // Public methods
    //----------------------------------

    var close = function() {
      if (current) {
        current.unselect();
        current = null;
        eventbus.trigger('manoeuvres:unselected');
      }
    };

    var open = function(linkId) {
      if (!current || current.linkId !== linkId) {
        close();
        collection.get(linkId, function(roadLink){
          current = roadLink;
          current.select();
          eventbus.trigger('manoeuvres:selected', roadLink);
        });
      }
    };

    var get = function() {
      return current;
    };

    var getLinkId = function() {
      return current.linkId;
    };

    var exists = function() {
      return current !== null;
    };

    var addManoeuvre = function(manoeuvre) {
      var sourceLinkId = current.linkId;
      // Add sourceLinkId as first element in linkIds list if it's not there already
      var linkIds = (manoeuvre.linkIds[0] != sourceLinkId) ? manoeuvre.linkIds.unshift(sourceLinkId) : manoeuvre.linkIds;
      var newManoeuvre = _.merge({}, { sourceLinkId: sourceLinkId, linkIds: linkIds }, manoeuvre);
      collection.addManoeuvre(newManoeuvre);
    };

    var removeManoeuvre = function(manoeuvre) {
      var sourceLinkId = current.linkId;
      var linkIds = (manoeuvre.linkIds[0] != sourceLinkId) ? manoeuvre.linkIds.unshift(sourceLinkId) : manoeuvre.linkIds;
      var manoeuvreToBeRemoved = _.merge({}, { sourceLinkId: current.linkId, linkIds: linkIds }, manoeuvre);
      collection.removeManoeuvre(manoeuvreToBeRemoved);
    };

    var setExceptions = function(manoeuvreId, exceptions) {
      collection.setExceptions(manoeuvreId, exceptions);
    };

    var setValidityPeriods = function(manoeuvreId, exceptions) {
      collection.setValidityPeriods(manoeuvreId, exceptions);
    };

    var setAdditionalInfo = function(manoeuvreId, additionalInfo) {
      collection.setAdditionalInfo(manoeuvreId, additionalInfo);
    };

    var save = function() {
      eventbus.trigger('manoeuvres:saving');
      collection.save(function() {
        eventbus.trigger('manoeuvres:saved', current);
      });
    };

    var cancel = function() {
      collection.cancelModifications();
      eventbus.trigger('manoeuvres:cancelled', current);
    };

    var isDirty = function() {
      return collection.isDirty();
    };

    var refresh = function() {
      if (current) {
        var linkId = current.linkId;
        current = null;
        open(linkId);
      }
    };

    return {
      close: close,
      open: open,
      get: get,
      getLinkId: getLinkId,
      exists: exists,
      addManoeuvre: addManoeuvre,
      removeManoeuvre: removeManoeuvre,
      setExceptions: setExceptions,
      setValidityPeriods: setValidityPeriods,
      setAdditionalInfo: setAdditionalInfo,
      save: save,
      cancel: cancel,
      isDirty: isDirty,
      refresh: refresh
    };
  };
})(this);
