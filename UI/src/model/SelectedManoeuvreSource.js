(function(root) {
  root.SelectedManoeuvreSource = function(collection) {
    var current = null;

    var close = function() {
      if (current) {
        current.unselect();
        current = null;
        eventbus.trigger('manoeuvres:unselected');
      }
    };

    var open = function(mmlId) {
      if (!current || current.mmlId !== mmlId) {
        close();
        collection.get(mmlId, function(roadLink){
          current = roadLink;
          current.select();
          eventbus.trigger('manoeuvres:selected', roadLink);
        });
      }
    };

    var get = function() {
      return current;
    };

    var refresh = function() {
      if (current) {
        var mmlId = current.mmlId;
        current = null;
        open(mmlId);
      }
    };

    var getMmlId = function() {
      return current.mmlId;
    };

    var exists = function() {
      return current !== null;
    };

    var addManoeuvre = function(manoeuvre) {
      var newManoeuvre = _.merge({}, { sourceMmlId: current.mmlId }, manoeuvre);
      collection.addManoeuvre(newManoeuvre);
    };

    var removeManoeuvre = function(manoeuvre) {
      var manoeuvreToBeRemoved = _.merge({}, { sourceMmlId: current.mmlId }, manoeuvre);
      collection.removeManoeuvre(manoeuvreToBeRemoved);
    };

    var setExceptions = function(manoeuvreId, exceptions) {
      collection.setExceptions(manoeuvreId, exceptions);
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

    return {
      close: close,
      open: open,
      get: get,
      getMmlId: getMmlId,
      exists: exists,
      addManoeuvre: addManoeuvre,
      removeManoeuvre: removeManoeuvre,
      setExceptions: setExceptions,
      setAdditionalInfo: setAdditionalInfo,
      save: save,
      cancel: cancel,
      isDirty: isDirty,
      refresh: refresh
    };
  };
})(this);
