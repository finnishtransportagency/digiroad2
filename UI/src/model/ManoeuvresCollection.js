(function(root) {
  root.ManoeuvresCollection = function(backend, roadCollection) {
    var manoeuvres = [];
    var addedManoeuvres = [];
    var removedManoeuvres = [];
    var updatedExceptions = {};

    var combineRoadLinksWithManoeuvres = function(roadLinks, manoeuvres) {
      return _.map(roadLinks, function(roadLink) {
        var filteredManoeuvres = _.filter(manoeuvres, function(manoeuvre) {
          return manoeuvre.sourceRoadLinkId === roadLink.roadLinkId;
        });
        var destinationOfManoeuvres = _.chain(manoeuvres)
          .filter(function(manoeuvre) {
            return manoeuvre.destRoadLinkId === roadLink.roadLinkId;
          })
          .pluck('id')
          .value();

        return _.merge({}, roadLink, {
          manoeuvreSource: _.isEmpty(filteredManoeuvres) ? 0 : 1,
          destinationOfManoeuvres: destinationOfManoeuvres,
          manoeuvres: filteredManoeuvres,
          type: 'normal'
        });
      });
    };

    var fetchManoeuvres = function(extent, callback) {
      backend.getManoeuvres(extent, callback);
    };

    var fetch = function(extent, zoom, callback) {
      eventbus.once('roadLinks:fetched', function() {
        fetchManoeuvres(extent, function(ms) {
          manoeuvres = ms;
          callback();
        });
      });
      roadCollection.fetch(extent, zoom);
    };

    var manoeuvresWithModifications = function() {
      return _.reject(manoeuvres.concat(addedManoeuvres), function(manoeuvre) {
        return _.some(removedManoeuvres, function(x) {
          return (manoeuvresEqual(x, manoeuvre));
        });
      });
    };

    var getAll = function() {
      return combineRoadLinksWithManoeuvres(roadCollection.getAll(), manoeuvresWithModifications());
    };

    var getDestinationRoadLinksBySourceRoadLink = function(roadLinkId) {
      return _.chain(manoeuvresWithModifications())
        .filter(function(manoeuvre) {
          return manoeuvre.sourceRoadLinkId === roadLinkId;
        })
        .pluck('destRoadLinkId')
        .value();
    };

    var get = function(roadLinkId, callback) {
      var roadLink = _.find(getAll(), function(manoeuvre) {
        return manoeuvre.roadLinkId === roadLinkId;
      });
      backend.getAdjacent(roadLink.roadLinkId, function(adjacent) {
        callback(_.merge({}, roadLink, { adjacent: adjacent }));
      });
    };

    var addManoeuvre = function(newManoeuvre) {
      if (_.isNull(newManoeuvre.manoeuvreId)) {
        _.remove(addedManoeuvres, function(m) { return manoeuvresEqual(m, newManoeuvre); });
        addedManoeuvres.push(newManoeuvre);
      } else {
        _.remove(removedManoeuvres, function(x) {
          return manoeuvresEqual(x, newManoeuvre);
        });
      }
      eventbus.trigger('manoeuvre:changed');
    };

    var removeManoeuvre = function(manoeuvre) {
      if (_.isNull(manoeuvre.manoeuvreId)) {
        _.remove(addedManoeuvres, function(x) {
          return manoeuvresEqual(x, manoeuvre);
        });
      } else {
        removedManoeuvres.push(manoeuvre);
      }
      eventbus.trigger('manoeuvre:changed');
    };

    var setExceptions = function(manoeuvreId, exceptions) {
      updatedExceptions[manoeuvreId] = exceptions;
      eventbus.trigger('manoeuvre:changed');
    };

    var manoeuvresEqual = function(x, y) {
      return (x.sourceRoadLinkId === y.sourceRoadLinkId && x.destRoadLinkId === y.destRoadLinkId);
    };

    var cancelModifications = function() {
      addedManoeuvres = [];
      removedManoeuvres = [];
      updatedExceptions = {};
    };

    var save = function(callback) {
      var removedManoeuvreIds = _.map(removedManoeuvres, function(manoeuvreToRemove) {
        var id = _.find(manoeuvres, function(manoeuvre) {
          return manoeuvresEqual(manoeuvre, manoeuvreToRemove);
        }).id;
        return id;
      });

      var failureCallback = function() { eventbus.trigger('asset:updateFailed'); };
      backend.removeManoeuvres(removedManoeuvreIds, function() {
        removedManoeuvres = [];
        backend.createManoeuvres(addedManoeuvres, function() {
          addedManoeuvres = [];
          callback();
        }, failureCallback);
      }, failureCallback);
    };

    var isDirty = function() {
      return !_.isEmpty(addedManoeuvres) || !_.isEmpty(removedManoeuvres) || !_.isEmpty(updatedExceptions);
    };

    return {
      fetch: fetch,
      getAll: getAll,
      getDestinationRoadLinksBySourceRoadLink: getDestinationRoadLinksBySourceRoadLink,
      get: get,
      addManoeuvre: addManoeuvre,
      removeManoeuvre: removeManoeuvre,
      setExceptions: setExceptions,
      cancelModifications: cancelModifications,
      isDirty: isDirty,
      save: save
    };
  };
})(this);
