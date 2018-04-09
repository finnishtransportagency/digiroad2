(function(root) {
  root.ManoeuvresCollection = function(backend, roadCollection) {
    var manoeuvres = [];
    var addedManoeuvre = {};
    var removedManoeuvres = {};
    var updatedInfo = {};
    var roadlinkAdjacents = {};
    var dirty = false;

    //----------------------------------
    // Public methods
    //----------------------------------

    /**
     * Fetches manoeuvres in map area and attached them to manoeuvres variable in ManoeuvresCollection. Used by ManoeuvreLayer.
     *
     * @param extent
     * @param zoom
     * @param callback
     */
    var fetch = function(extent, zoom, callback) {
      eventbus.once('roadLinks:fetched', function() {
        fetchManoeuvres(extent, function(ms) {
          manoeuvres = ms;
          manoeuvres = formatManoeuvres(manoeuvres);
          callback();
          eventbus.trigger('manoeuvres:fetched');
        });
      });
      roadCollection.fetch(extent);
    };

    /**
     * Returns all road links with manoeuvres attached to their source road link. Used by ManoeuvresCollection.get and ManoeuvreLayer.
     *
     * @returns {Array}
     */
    var getAll = function() {
      return combineRoadLinksWithManoeuvres(roadCollection.getAll(), manoeuvresWithModifications());
    };

    /**
     * Returns a list of second link ids in manoeuvre link chain by source link id. Used by ManoeuvreLayer.
     *
     * @param linkId
     * @returns {*}
     */
    var getFirstTargetRoadLinksBySourceLinkId = function(linkId) {
      return _.chain(manoeuvresWithModifications())
          .filter(function(manoeuvre) {
            return manoeuvre.sourceLinkId === linkId;
          })
          .pluck('firstTargetLinkId')
          .value();
    };

    var getNextTargetRoadLinksBySourceLinkId = function(linkId) {
      return _.flatten(_.chain(manoeuvresWithModifications())
          .filter(function(manoeuvre){
            return manoeuvre.sourceLinkId === linkId;
          })
          .map(function(manoeuvre){
            return manoeuvre.linkIds.slice(1);
          }).value());
    };

    /**
     * Returns road link with adjacent links and their markers added. Used by SelectedManoeuvreSource.open.
     *
     * @param linkId    Source link id
     * @param callback  What to do after asynchronous backend call
     */
    var get = function(linkId, callback) {
      var roadLink = _.find(getAll(), {linkId: linkId});
      var linkIds = getNonAdjacentTargetRoadLinksBySourceLinkId(linkId);
      var targets = _.map(linkIds, function (t) {
        var link = roadCollection.get([t])[0];
        return link ? link.getData() : {
          linkId: t
        };
      });
      backend.getAdjacent(roadLink.linkId, function(adjacents) {
        linkIds = linkIds.concat(_.map(adjacents, function (rl) {
          return rl.linkId;
        }));
        roadlinkAdjacents[roadLink.linkId] = adjacents;
        fillTargetAdjacents(roadLink, targets, linkIds, adjacents, callback);
      });
    };

    /**
     * Updates model after form changes.
     *
     * @param newManoeuvre
     */
    var addManoeuvre = function(newManoeuvre) {
      dirty = true;
      if (_.isNull(newManoeuvre.manoeuvreId)) {
        addedManoeuvre = newManoeuvre;
      } else {
        removedManoeuvres = {};
      }
      eventbus.trigger('manoeuvre:changed', newManoeuvre);
    };

      /**
       * Updates model after form changes.
       *
       * @param manoeuvre
       */
    var removeManoeuvre = function(manoeuvre) {
      dirty = true;
      if (_.isNull(manoeuvre.manoeuvreId)) {
        addedManoeuvre = {};
      } else {
        removedManoeuvres = manoeuvre;
      }
      eventbus.trigger('manoeuvre:changed', manoeuvre);
    };

    /**
     * Add link id to manoeuvre link chain.
     *
     * @param manoeuvre
     * @param linkId
     */
    var addLink = function(manoeuvre, linkId) {
      dirty = true;

      if (_.isNull(manoeuvre.manoeuvreId)) {

        // Add link id to list after removal comparison and update destLinkId
        if("linkIds" in addedManoeuvre){
          manoeuvre.linkIds = addedManoeuvre.linkIds;
        }
        manoeuvre.linkIds.push(linkId);
        manoeuvre.destLinkId = linkId;

        // Remove source link id and destination link id to get only intermediate link ids
        var intermediateLinkIds = manoeuvre.linkIds.slice(1, manoeuvre.linkIds.length-1);
        var manoeuvreIntermediateLinks = _.merge({}, { intermediateLinkIds: intermediateLinkIds }, manoeuvre, { additionalInfo: addedManoeuvre.additionalInfo, exceptions: addedManoeuvre.exceptions, validityPeriods: addedManoeuvre.validityPeriods });

        // Add enriched manoeuvre to addedManoeuvre
        addedManoeuvre = manoeuvreIntermediateLinks;
      }

      reload(manoeuvre, linkId, function(reloaded) {
        addedManoeuvre = reloaded;
        eventbus.trigger('manoeuvre:linkAdded', reloaded);
        eventbus.trigger('adjacents:updated', reloaded);
      });
    };

    var reload = function(manoeuvre, linkId, callback) {
      // Reload the source link and the manoeuvre data
      if(_.isUndefined(manoeuvre))return;
      get(manoeuvre.sourceLinkId, function (roadLink) {
        var modified = roadLink.manoeuvres.find(function (m) {
          return manoeuvresEqual(m, manoeuvre);
        });
        var searchBase;
        if (!_.isUndefined(modified) && modified.intermediateLinkIds && modified.intermediateLinkIds.length > 0) {
          searchBase = roadLink.nonAdjacentTargets;
        } else {
          searchBase = roadLink.adjacent;
        }
        var targetLink = searchBase.find(function (t) {
          return t.linkId === linkId;
        });

        if (targetLink) {

          var previousAdjacentLinks = [];

          _.each(manoeuvre.linkIds, function(item){
            if(item != linkId)
              previousAdjacentLinks = previousAdjacentLinks.concat(_.pluck(roadlinkAdjacents[item], 'linkId'));
          });

          var nextAdjacentLinks = _.filter(targetLink.adjacentLinks, function(item){
            //Remove from adjacents the previous adjacents links, all links from the chain and
            return !_.contains(previousAdjacentLinks, item.linkId);// && !_.contains(manoeuvre.linkIds, item.linkId);
          });
          callback(_.merge({}, modified, {"adjacentLinks": nextAdjacentLinks}));
        }

      });
    };

    /**
     * Remove link id from manoeuvre link chain.
     *
     * @param manoeuvre
     * @param linkId
     */
    var removeLink = function(manoeuvre, linkId) {
      dirty = true;
      var modified = manoeuvresWithModifications().find(function (m) {
        return manoeuvresEqual(m, manoeuvre) && m.destLinkId === linkId;
      });
      if (modified) {
        console.log("manoeuvre found!");
        addedManoeuvre = {};
        manoeuvre.linkIds.pop();
        var newDestLinkId = manoeuvre.linkIds[manoeuvre.linkIds.length-1];
        manoeuvre.destLinkId = newDestLinkId;
        var intermediateLinkIds = manoeuvre.linkIds.slice(1, manoeuvre.linkIds.length-2);
        var manoeuvreIntermediateLinks = _.merge({}, manoeuvre);
        manoeuvreIntermediateLinks.intermediateLinkIds = intermediateLinkIds;
        manoeuvreIntermediateLinks.destLinkId = newDestLinkId;
        manoeuvreIntermediateLinks.nextTargetLinkId = newDestLinkId;
        addedManoeuvre = manoeuvreIntermediateLinks;
        console.log(manoeuvreIntermediateLinks);
        eventbus.trigger('manoeuvre:changed', manoeuvreIntermediateLinks);
        reload(manoeuvreIntermediateLinks,
          manoeuvreIntermediateLinks.linkIds[manoeuvreIntermediateLinks.linkIds.length-1], function(reloaded) {
          eventbus.trigger('manoeuvre:linkDropped', reloaded);
        });
      } else {
        console.log("Warning! Attempted to remove link but manoeuvre was not found!");
      }
    };

    /**
     * Update exception modifications to model.
     *
     * @param manoeuvreId
     * @param exceptions
     */
    var setExceptions = function(manoeuvreId, exceptions) {
      dirty = true;
      if(!manoeuvreId){
        if(!("exceptions" in addedManoeuvre)) {
          addedManoeuvre.exceptions = [];
        }
        addedManoeuvre.exceptions = exceptions;
      }
      else {
        var info = updatedInfo[manoeuvreId] || {};
        info.exceptions = exceptions;
        updatedInfo[manoeuvreId] = info;
      }
      eventbus.trigger('manoeuvre:changed', exceptions);
    };

    /**
     * Update validity period modifications to model.
     *
     * @param manoeuvreId
     * @param validityPeriods
       */
    var setValidityPeriods = function(manoeuvreId, validityPeriods) {
      dirty = true;
      if(!manoeuvreId){
        addedManoeuvre.validityPeriods = validityPeriods;
      } else {
        var info = updatedInfo[manoeuvreId] || {};
        info.validityPeriods = validityPeriods;
        updatedInfo[manoeuvreId] = info;
      }
      eventbus.trigger('manoeuvre:changed', validityPeriods);
    };

    /**
     * Update additional info modifications to model.
     *
     * @param manoeuvreId
     * @param additionalInfo
     */
    var setAdditionalInfo = function(manoeuvreId, additionalInfo) {
      dirty = true;
      if(!manoeuvreId) {
        addedManoeuvre.additionalInfo = additionalInfo;
      }
      else {
        var info = updatedInfo[manoeuvreId] || {};
        info.additionalInfo = additionalInfo;
        updatedInfo[manoeuvreId] = info;
      }
      eventbus.trigger('manoeuvre:changed', additionalInfo);
    };

    /**
     * Revert model state after cancellation.
     */
    var cancelModifications = function() {
      addedManoeuvre = {};
      removedManoeuvres = {};
      updatedInfo = {};
      dirty = false;
    };

      /**
       * Revert model state after cancelling the removal of a manoeuvre.
       */
    var cancelManoeuvreRemoval = function(){
      removedManoeuvres = {};
    };

    /**
     * Mark model state to 'dirty' after form changes.
     *
     * @returns {boolean}
       */
    var isDirty = function() {
      return dirty;
    };

    /**
     * Set model state 'dirty'
     *
     * @returns {boolean}
     */
    var setDirty = function(state) {
      dirty = state;
    };

    /**
     * Save model changes to database.
     *
     * @param callback
       */
    var save = function(callback) {
      var removedManoeuvreIds = _.isEmpty(removedManoeuvres) ? [] : [removedManoeuvres.manoeuvreId];

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
        resetData: function() { removedManoeuvres = {}; }
      });
      backendCallStack.push({
        data: (_.isEmpty(addedManoeuvre) ? [] : [addedManoeuvre]),
        operation: backend.createManoeuvres,
        resetData: function() { addedManoeuvre = {}; roadlinkAdjacents = {}; }
      });
      backendCallStack.push({
        data: details,
        operation: backend.updateManoeuvreDetails,
        resetData: function() { updatedInfo = {}; }
      });

      unwindBackendCallStack(backendCallStack, successCallback, failureCallback);
    };

    //---------------------------------------
    // Utility functions
    //---------------------------------------

    /**
     * Attaches manoeuvre data to road link.
     * - Manoeuvres with road link as source link
     * - Road link visualization information
     *
     * @param roadLinks
     * @param manoeuvres
     * @returns {Array} Roadlinks enriched with manoeuvre related data.
     */
    var combineRoadLinksWithManoeuvres = function (roadLinks, manoeuvres) {
      return _.map(roadLinks, function (roadLink) {

        var multipleSourceManoeuvresHMap = {};
        var multipleIntermediateManoeuvresHMap = {};
        var multipleDestinationManoeuvresHMap = {};
        var sourceDestinationManoeuvresHMap = {};

        // Filter manoeuvres whose sourceLinkId matches road link's id
        var filteredManoeuvres = _.filter(manoeuvres, function (manoeuvre) {
          return manoeuvre.sourceLinkId === roadLink.linkId;
        });

        // Check if road link is intermediate link of some manoeuvre
        var intermediateManoeuvres  = _.chain(manoeuvres)
            .filter(function(manoeuvre) {
              return _.some(manoeuvre.intermediateLinkIds, function (intermediateLinkId) {
                return intermediateLinkId === roadLink.linkId;
              });
            })
            .pluck('id')
            .value();

        // Check if road link is source link for multiple manoeuvres
        var multipleSourceManoeuvres = _.chain(filteredManoeuvres)
            .filter(function (manoeuvre) {
              multipleSourceManoeuvresHMap[manoeuvre.sourceLinkId] = manoeuvre.sourceLinkId in multipleSourceManoeuvresHMap ? multipleSourceManoeuvresHMap[manoeuvre.sourceLinkId] += 1 : 1;
              return multipleSourceManoeuvresHMap[manoeuvre.sourceLinkId] >= 2;
            })
            .pluck('id')
            .value();

        // Check if road link is intermediate link for multiple manoeuvres
        var multipleIntermediateManoeuvres = _.chain(manoeuvres)
            .filter(function (manoeuvre) {
              return _.some(manoeuvre.intermediateLinkIds, function (intermediateLinkId) {
                if (intermediateLinkId === roadLink.linkId){
                  multipleIntermediateManoeuvresHMap[intermediateLinkId] = intermediateLinkId in multipleIntermediateManoeuvresHMap ? multipleIntermediateManoeuvresHMap[intermediateLinkId] += 1 : 1;
                  return multipleIntermediateManoeuvresHMap[intermediateLinkId] >= 2;
                }
              });
            })
            .pluck('id')
            .value();

        // Check if road link is destination link of some manoeuvre
        var destinationOfManoeuvres = _.chain(manoeuvres)
            .filter(function(manoeuvre) {
              return manoeuvre.destLinkId === roadLink.linkId;
            })
            .pluck('id')
            .value();

        // Check if road link is destination link for multiple manoeuvres
        var multipleDestinationManoeuvres = _.chain(manoeuvres)
            .filter(function (manoeuvre) {
              if (manoeuvre.destLinkId === roadLink.linkId) {
                multipleDestinationManoeuvresHMap[manoeuvre.destLinkId] = manoeuvre.destLinkId in multipleDestinationManoeuvresHMap ? multipleDestinationManoeuvresHMap[manoeuvre.destLinkId] += 1 : 1;
                return multipleDestinationManoeuvresHMap[manoeuvre.destLinkId] >= 2;
              }
            })
            .pluck('id')
            .value();

        // Check if road link is source and destination link for manoeuvres
        var sourceDestinationManoeuvres = _.chain(manoeuvres)
            .filter(function (manoeuvre) {
              if (!_.isEmpty(filteredManoeuvres) && manoeuvre.destLinkId === roadLink.linkId) {
                var sourceOrDestLinkId = roadLink.linkId;
                sourceDestinationManoeuvresHMap[sourceOrDestLinkId] = sourceOrDestLinkId in sourceDestinationManoeuvresHMap ? sourceDestinationManoeuvresHMap[sourceOrDestLinkId] += 1 : 1;
                return sourceDestinationManoeuvresHMap[sourceOrDestLinkId] >= 1;
              }
            })
            .pluck('id')
            .value();

        // Check if road link is source link of some manoeuvre
        var manoeuvreSource = _.isEmpty(filteredManoeuvres) ? 0 : 1;

        return _.merge({}, roadLink, {
          manoeuvreSource: manoeuvreSource,
          destinationOfManoeuvres: destinationOfManoeuvres,
          intermediateManoeuvres : intermediateManoeuvres,
          multipleSourceManoeuvres: multipleSourceManoeuvres,
          multipleIntermediateManoeuvres: multipleIntermediateManoeuvres,
          multipleDestinationManoeuvres: multipleDestinationManoeuvres,
          sourceDestinationManoeuvres: sourceDestinationManoeuvres,
          manoeuvres: filteredManoeuvres,
          type: 'normal'
        });
      });
    };

    var fetchManoeuvres = function(extent, callback) {
      backend.getManoeuvres(extent, callback);
    };

    /**
     * Extract manoeuvre element data to manoeuvre level.
     *
     * @param manoeuvres
     * @returns {Array} Manoeuvres with data to be used in UI functionality.
    */
    var formatManoeuvres = function(manoeuvres) {
      return _.map(manoeuvres, function (manoeuvre) {
        var sourceLinkId = manoeuvre.elements[0].sourceLinkId;
        var firstTargetLinkId = manoeuvre.elements[0].destLinkId;
        var lastElementIndex = manoeuvre.elements.length - 1;
        var destLinkId = manoeuvre.elements[lastElementIndex].sourceLinkId;
        var linkIds = _.chain(manoeuvre.elements).pluck('sourceLinkId').value();
        var intermediateLinkIds = _.chain(manoeuvre.elements)
          .filter(function(element) {
            return element.elementType === 2;
          })
          .pluck('sourceLinkId')
          .value();
        return _.merge({}, manoeuvre, {
          sourceLinkId: sourceLinkId,
          firstTargetLinkId: firstTargetLinkId,
          linkIds: linkIds,
          intermediateLinkIds: intermediateLinkIds,
          destLinkId: destLinkId
        });
      });
    };

    var manoeuvresWithModifications = function() {
      return _.reject(manoeuvres.concat((_.isEmpty(addedManoeuvre) ? [] : [addedManoeuvre])), function(manoeuvre) {
        return _.some([removedManoeuvres], function(x) {
          return (manoeuvresEqual(x, manoeuvre));
        });
      });
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

    /**
     * Check if manoeuvres are equal. Manoeuvres are equal if they have the same link chain.
     *
     * @param manoeuvreA
     * @param manoeuvreB
     * @returns {boolean}
       */
    var manoeuvresEqual = function(manoeuvreA, manoeuvreB) {
      var linkIdsA = manoeuvreA.linkIds;
      var linkIdsB = manoeuvreB.linkIds;
      return _.isEqual(linkIdsA, linkIdsB);
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

    var getNonAdjacentTargetRoadLinksBySourceLinkId = function(linkId) {
      return _.chain(manoeuvresWithModifications())
          .filter(function(manoeuvre) {
            return manoeuvre.sourceLinkId === linkId && manoeuvre.intermediateLinkIds &&
                manoeuvre.intermediateLinkIds.length > 0;
          })
          .pluck('destLinkId')
          .value();
    };

    var setMarkersToRoadLinks= function(roadLink, adjacentLinks, targetLinks, nextTargetLinks, callback) {
      var linkId = roadLink.linkId;
      var modificationData = getLatestModificationDataBySourceRoadLink(linkId);
      var markers = ["A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z",
        "AA", "AB", "AC", "AD", "AE", "AF", "AG", "AH", "AI", "AJ", "AK", "AL", "AM", "AN", "AO", "AP", "AQ", "AR", "AS", "AT", "AU", "AV", "AW", "AX", "AY", "AZ",
        "BA", "BB", "BC", "BD", "BE", "BF", "BG", "BH", "BI", "BJ", "BK", "BL", "BM", "BN", "BO", "BP", "BQ", "BR", "BS", "BT", "BU", "BV", "BW", "BX", "BY", "BZ",
        "CA", "CB", "CC", "CD", "CE", "CF", "CG", "CH", "CI", "CJ", "CK", "CL", "CM", "CN", "CO", "CP", "CQ", "CR", "CS", "CT", "CU", "CV", "CW", "CX", "CY", "CZ"];

      var adjacentIds = adjacentLinks.map(function(l) { return l.linkId; });
      var targetIds = targetLinks.map(function(l) { return l.linkId; });

      var sortedNextTargetLinksWithMarker = _.chain(nextTargetLinks)
          .mapValues(function(a){
            return _.merge({}, _.map(a, function (b, i) {
              return _.merge({}, b, { marker: markers[adjacentLinks.length + targetLinks.length + i] });
            }));
          }).value();

      var alteredTargets = _.map(targetLinks, function (t) {
        var targetAdjacent = _.filter(sortedNextTargetLinksWithMarker[t.linkId], function (rl) {
          return !(rl.linkId === roadLink.linkId || _.contains(targetIds, rl.linkId));
        });

        return _.merge({}, t, { adjacentLinks: targetAdjacent });
      });

      var alteredAdjacents = _.map(adjacentLinks, function (t) {
        var targetAdjacent = _.filter(sortedNextTargetLinksWithMarker[t.linkId], function (rl) {
          return !(rl.linkId === roadLink.linkId || _.contains(adjacentIds, rl.linkId));
        });
        return _.merge({}, t, { adjacentLinks: targetAdjacent });
      });

      var sortedAdjacentWithMarker = _.chain(alteredAdjacents)
          .sortBy('id')
          .map(function(a, i){
            return _.merge({}, a, { marker: markers[i]} );
          }).value();

      var sortedTargetsWithMarker = _.chain(alteredTargets)
          .sortBy('id')
          .map(function(a, i){
            return _.merge({}, a, { marker: markers[i+adjacentLinks.length] } );
          }).value();

      var sourceRoadLinkModel = roadCollection.get([linkId])[0];
      callback(_.merge({}, roadLink, modificationData, { adjacent: sortedAdjacentWithMarker }, { nonAdjacentTargets: sortedTargetsWithMarker }, { select: sourceRoadLinkModel.select, unselect: sourceRoadLinkModel.unselect } ));
    };

    var fillTargetAdjacents = function(roadLink, targets, linkIds, adjacentLinks, callback) {
      backend.getAdjacents(linkIds.join(), function (nextTargets) {
        roadlinkAdjacents = _.merge({}, roadlinkAdjacents, nextTargets);
        setMarkersToRoadLinks(roadLink, adjacentLinks, targets, nextTargets, callback);
      });
    };

    var getManoeuvresBySourceLinkId = function(sourceLinkId){
      var foundManoeuvres = [];

      if("sourceLinkId" in addedManoeuvre && addedManoeuvre.sourceLinkId === sourceLinkId)
        foundManoeuvres.push(addedManoeuvre);

      return foundManoeuvres;
    };

    var getManoeuvreData = function(manoeuvreSource){

      var manoeuvre = _.find(manoeuvreSource.manoeuvres, function(item){
        return item.id === addedManoeuvre.id || manoeuvresEqual(addedManoeuvre, item);
      });

      if (!manoeuvre) {
        var allManoeuvres = _.flatten(_.pluck(this.getAll(), 'manoeuvres'));

        manoeuvre = _.find(allManoeuvres, function(m) {
          return _.some(manoeuvreSource.manoeuvres, function(item){
            return m.id === item.id || manoeuvresEqual(m,item);
          });
        });
      }
    return manoeuvre;
    };

    var getDestinationRoadLinksBySource = function (manoeuvreSource) {
      var destinationRoadLinkList = [];
      manoeuvreSource.manoeuvres.forEach(function (m) {
        if(!_.contains(destinationRoadLinkList,_.last(m.linkIds))){
          destinationRoadLinkList.push(_.last(m.linkIds));
        }
      });
      return destinationRoadLinkList;
    };

    var getIntermediateRoadLinksBySource = function (manoeuvreSource) {
      var intermediateRoadLinkList = [];
      manoeuvreSource.manoeuvres.forEach(function (m){
        intermediateRoadLinkList.push(_.difference(m.intermediateLinkIds, intermediateRoadLinkList));
      });
    return intermediateRoadLinkList;
    };

    var getAddedManoeuvre = function(){
      return addedManoeuvre;
    };

    return {
      fetch: fetch,
      getAll: getAll,
      getFirstTargetRoadLinksBySourceLinkId: getFirstTargetRoadLinksBySourceLinkId,
      getNextTargetRoadLinksBySourceLinkId: getNextTargetRoadLinksBySourceLinkId,
      get: get,
      addManoeuvre: addManoeuvre,
      removeManoeuvre: removeManoeuvre,
      addLink: addLink,
      removeLink: removeLink,
      setExceptions: setExceptions,
      setValidityPeriods: setValidityPeriods,
      setAdditionalInfo: setAdditionalInfo,
      cancelModifications: cancelModifications,
      cancelManoeuvreRemoval: cancelManoeuvreRemoval,
      isDirty: isDirty,
      setDirty: setDirty,
      save: save,
      manoeuvresEqual: manoeuvresEqual,
      reload: reload,
      getManoeuvresBySourceLinkId : getManoeuvresBySourceLinkId,
      getManoeuvreData : getManoeuvreData,
      getDestinationRoadLinksBySource : getDestinationRoadLinksBySource,
      getIntermediateRoadLinksBySource : getIntermediateRoadLinksBySource,
      getAddedManoeuvre: getAddedManoeuvre
    };
  };
})(this);
