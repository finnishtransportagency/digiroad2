(function(root) {
  root.ManoeuvresCollection = function(backend, roadCollection) {
    var manoeuvres = [];
    var addedManoeuvres = [];
    var removedManoeuvres = [];
    var updatedInfo = {};
    var dirty = false;


    //Attach manoeuvre data to road link
    var combineRoadLinksWithManoeuvres = function (roadLinks, manoeuvres) {
      return _.map(roadLinks, function (roadLink) {

        // Filter manoeuvres whose sourceLinkId matches road link's id
        var filteredManoeuvres = _.filter(manoeuvres, function (manoeuvre) {
          return _.some(manoeuvre.elements, function (element) {
            return element.sourceLinkId === roadLink.linkId && element.elementType === 1;
          });
        });
        var intermediateManoeuvres  = _.chain(manoeuvres)
            .filter(function(manoeuvre) {
              return _.some(manoeuvre.elements, function (element) {
                return element.sourceLinkId === roadLink.linkId && element.elementType === 2;
              });
            })
            .pluck('id')
            .value();
        // Check if road link is destination link of some manoeuvre
        // Used to show road link dashed on map
        var destinationOfManoeuvres = _.chain(manoeuvres)
          .filter(function(manoeuvre) {
            return _.some(manoeuvre.elements, function (element) {
              return element.sourceLinkId === roadLink.linkId && element.elementType === 3;
            });
          })
          .pluck('id')
          .value();

        // Check if road link is source link of some manoeuvre
        // Used to show road link as blue or grey on map
        var manoeuvreSource = _.isEmpty(filteredManoeuvres) ? 0 : 1;

        return _.merge({}, roadLink, {
          manoeuvreSource: manoeuvreSource,
          destinationOfManoeuvres: destinationOfManoeuvres,
          intermediateManoeuvres : intermediateManoeuvres,
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
          eventbus.trigger('manoeuvres:fetched');
        });
      });
      roadCollection.fetch(extent);
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

    var getDestinationRoadLinksBySourceLinkId = function(linkId) {
      return _.chain(manoeuvresWithModifications())
        .filter(function(manoeuvre) {
          return manoeuvre.sourceLinkId === linkId;
        })
        .pluck('destLinkId')
        .value();
    };

    var sortLinkManoeuvres = function(manoeuvres) {
      return manoeuvres.sort(function(m1, m2) {
        var date1 = moment(m1.modifiedDateTime, "DD.MM.YYYY HH:mm:ss");
        var date2 = moment(m2.modifiedDateTime, "DD.MM.YYYY HH:mm:ss");
        return date1.isBefore(date2) ? 1 : -1;
      });
    };

    var getLatestModificationDataBySourceRoadLink = function(linkId) {
      var sourceLinkManoeuvres = _.filter(manoeuvresWithModifications(), function(manoeuvre) {
        return manoeuvre.sourceLinkId === linkId;
      });
      var sortedManoeuvres = sortLinkManoeuvres(sourceLinkManoeuvres);
      var latestModification = _.first(sortedManoeuvres);
      return {
        modifiedAt: latestModification ? latestModification.modifiedDateTime : null,
        modifiedBy: latestModification ? latestModification.modifiedBy : null
      };
    };

    var get = function(linkId, callback) {
      var roadLink = _.find(getAll(), {linkId: linkId});
      backend.getAdjacent(roadLink.linkId, function(adjacent) {
        var modificationData = getLatestModificationDataBySourceRoadLink(linkId);
        var markers = ["A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"];
        var sortedAdjacentWithMarker = _.chain(adjacent)
          .sortBy('id')
          .map(function(a, i){
            return _.merge({}, a, { marker: markers[i] });
          }).value();
        var sourceRoadLinkModel = roadCollection.get([linkId])[0];
        callback(_.merge({}, roadLink, modificationData, { adjacent: sortedAdjacentWithMarker }, { select: sourceRoadLinkModel.select, unselect: sourceRoadLinkModel.unselect } ));
      });
    };

    var addManoeuvre = function(newManoeuvre) {
      dirty = true;
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
      dirty = true;
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
      dirty = true;
      var info = updatedInfo[manoeuvreId] || {};
      info.exceptions = exceptions;
      updatedInfo[manoeuvreId] = info;
      eventbus.trigger('manoeuvre:changed');
    };

    var setValidityPeriods = function(manoeuvreId, validityPeriods) {
      dirty = true;
      var info = updatedInfo[manoeuvreId] || {};
      info.validityPeriods = validityPeriods;
      updatedInfo[manoeuvreId] = info;
      eventbus.trigger('manoeuvre:changed');
    };

    var setAdditionalInfo = function(manoeuvreId, additionalInfo) {
      dirty = true;
      var info = updatedInfo[manoeuvreId] || {};
      info.additionalInfo = additionalInfo;
      updatedInfo[manoeuvreId] = info;
      eventbus.trigger('manoeuvre:changed');
    };

    var manoeuvresEqual = function(x, y) {
      return (x.sourceLinkId === y.sourceLinkId && x.destLinkId === y.destLinkId);
    };

    var cancelModifications = function() {
      addedManoeuvres = [];
      removedManoeuvres = [];
      updatedInfo = {};
      dirty = false;
    };

    var unwindBackendCallStack = function(stack, callback, failureCallback) {
      if(_.isEmpty(stack)) {
        callback();
      } else {
        var call = stack.pop();
        if(_.isEmpty(call.data)) {
          unwindBackendCallStack(stack, callback, failureCallback);
        } else {
          call.operation(call.data, function() {
            call.resetData();
            unwindBackendCallStack(stack, callback, failureCallback);
          }, failureCallback);
        }
      }
    };

    var save = function(callback) {
      var removedManoeuvreIds = _.pluck(removedManoeuvres, 'manoeuvreId');

      var failureCallback = function() {
        dirty = true;
        eventbus.trigger('asset:updateFailed');
      };
      var successCallback = function() {
        dirty = false;
        callback();
      };
      var details = _.omit(updatedInfo, function(value, key) {
        return _.some(removedManoeuvreIds, function(id) {
          return id === parseInt(key, 10);
        });
      });

      var backendCallStack = [];
      backendCallStack.push({
        data: removedManoeuvreIds,
        operation: backend.removeManoeuvres,
        resetData: function() { removedManoeuvres = []; }
      });
      backendCallStack.push({
        data: addedManoeuvres,
        operation: backend.createManoeuvres,
        resetData: function() { addedManoeuvres = []; }
      });
      backendCallStack.push({
        data: details,
        operation: backend.updateManoeuvreDetails,
        resetData: function() { updatedInfo = {}; }
      });

      unwindBackendCallStack(backendCallStack, successCallback, failureCallback);
    };

    var isDirty = function() {
      return dirty;
    };

    return {
      fetch: fetch,
      getAll: getAll,
      getDestinationRoadLinksBySourceLinkId: getDestinationRoadLinksBySourceLinkId,
      get: get,
      addManoeuvre: addManoeuvre,
      removeManoeuvre: removeManoeuvre,
      setExceptions: setExceptions,
      setValidityPeriods: setValidityPeriods,
      setAdditionalInfo: setAdditionalInfo,
      cancelModifications: cancelModifications,
      isDirty: isDirty,
      save: save
    };
  };
})(this);
