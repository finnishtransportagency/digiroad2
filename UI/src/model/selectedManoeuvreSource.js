(function(root) {
  root.SelectedManoeuvreSource = function(manoeuvresCollection) {
    var current = null;
    var targetRoadLinkSelected = null;


    //----------------------------------
    // Public methods
    //----------------------------------

    /**
     * Closes selected manoeuvre source and triggers event that empties the manoeuvre form.
     * Used by SelectedManoeuvreSource.open and ManoeuvreLayer.unselectManoeuvre
     */
    var close = function() {
      if (current) {
        current.unselect();
        current = null;
        eventbus.trigger('manoeuvres:unselected');
      }
    };

    /**
     * Fetches road link by link id from collection and sets it as current manoeuvre source.
     *
     * @param linkId
       */
    var open = function(linkId) {
      if (!current || current.linkId !== linkId) {
        close();
        manoeuvresCollection.get(linkId, function(roadLink){
          current = roadLink;
          current.select();
          eventbus.trigger('manoeuvres:selected', roadLink);
        });
      }
    };

    /**
     * Refreshes current source link data when new target is selected in the manoeuvre form.
     * ManoeuvresCollection.get fetches adjacent links of new target and attaches them to source road link.
     */
    var updateAdjacents = function() {
      console.log("updateAdjacents");
      console.log(current);
      if (current) {
        var linkId = current.linkId;
        var mano = fetchManoeuvre(null, targetRoadLinkSelected);
        if(!mano){
            mano = manoeuvresCollection.getManoeuvreData(current);
        }
        manoeuvresCollection.reload(mano, targetRoadLinkSelected, function(manoeuvre) {
          eventbus.trigger('adjacents:updated', manoeuvre);
        });

      }
    };

    /**
     * Returns current source link. Used by ManoeuvreLayer to visualize road links on map.
     */
    var get = function() {
      return current;
    };

    /**
     * Returns link id of current source. Used by ManoeuvreLayer to visualize road links on map.
     */
    var getLinkId = function() {
      return current.linkId;
    };

    /**
     * Returns true if source link exists. Used by ManoeuvreLayer to visualize road links on map.
     */
    var exists = function() {
      return current !== null;
    };

    /**
     * Add source link id to manoeuvre and pass it to ManoeuvresCollection to update the model.
     *
     * @param manoeuvre
       */
    var addManoeuvre = function(manoeuvre) {
      var sourceLinkId = current.linkId;
      // Add sourceLinkId as first element in linkIds list if it's not there already
      var linkIds = (manoeuvre.linkIds[0] != sourceLinkId) ? manoeuvre.linkIds.unshift(sourceLinkId) : manoeuvre.linkIds;
      var newManoeuvre = _.merge({}, { sourceLinkId: sourceLinkId, linkIds: linkIds }, manoeuvre);
      manoeuvresCollection.addManoeuvre(newManoeuvre);
    };

    /**
     * Add source link id to manoeuvre and pass it to ManoeuvresCollection to update the model.
     * If no manoeuvre is found it will cancel the removal.
     * 
     * @param manoeuvre
       */
    var removeManoeuvre = function(manoeuvre) {
      if(manoeuvre){
        var sourceLinkId = current.linkId;
        var linkIds = (manoeuvre.linkIds[0] != sourceLinkId) ? manoeuvre.linkIds.unshift(sourceLinkId) : manoeuvre.linkIds;
        var manoeuvreToBeRemoved = _.merge({}, { sourceLinkId: current.linkId, linkIds: linkIds }, manoeuvre);
        manoeuvresCollection.removeManoeuvre(manoeuvreToBeRemoved);
      } else {
        manoeuvresCollection.cancelManoeuvreRemoval();
      }
      
    };

    /**
     * Add link id to manoeuvre link chain.
     *
     * @param manoeuvre
     * @param linkId
       */
    var addLink = function(manoeuvre, linkId) {
      console.log("AddLink " + manoeuvre.linkIds + " ++ " + linkId);
      var sourceLinkId = current.linkId;
      var linkIds = (manoeuvre.linkIds[0] != sourceLinkId) ? manoeuvre.linkIds.unshift(sourceLinkId) : manoeuvre.linkIds;
      var manoeuvreWithSourceLink = _.merge({}, { sourceLinkId: current.linkId, linkIds: linkIds }, manoeuvre);
      targetRoadLinkSelected = linkId;
      manoeuvresCollection.addLink(manoeuvreWithSourceLink, linkId);
    };

    /**
     * Remove link id from manoeuvre link chain.
     *
     * @param manoeuvre
     * @param linkId
       */
    var removeLink = function(manoeuvre, linkId) {
      manoeuvresCollection.removeLink(manoeuvre, linkId);
    };

    /**
     * Update exception modifications.
     *
     * @param manoeuvreId
     * @param exceptions
       */
    var setExceptions = function(manoeuvreId, exceptions) {
      manoeuvresCollection.setExceptions(manoeuvreId, exceptions);
    };

    /**
     * Update validity period modifications.
     *
     * @param manoeuvreId
     * @param exceptions
       */
    var setValidityPeriods = function(manoeuvreId, exceptions) {
      manoeuvresCollection.setValidityPeriods(manoeuvreId, exceptions);
    };

    /**
     * Update additional info modifications.
     *
     * @param manoeuvreId
     * @param additionalInfo
       */
    var setAdditionalInfo = function(manoeuvreId, additionalInfo) {
      manoeuvresCollection.setAdditionalInfo(manoeuvreId, additionalInfo);
    };

    /**
     * Save model and trigger event.
     */
    var save = function() {
      eventbus.trigger('manoeuvres:saving');
      manoeuvresCollection.save(function() {
        targetRoadLinkSelected = null;
        eventbus.trigger('manoeuvres:saved', current);
      });
    };

    /**
     * Revert model after cancellation and trigger event.
     */
    var cancel = function() {
      manoeuvresCollection.cancelModifications();
      manoeuvresCollection.get(current.linkId, function(roadLink){
        current = roadLink;
        eventbus.trigger('manoeuvres:cancelled', current);
      });
    };

    /**
     * Check if model has been modified.
     * @returns {*|boolean}
     */
    var isDirty = function() {
      return manoeuvresCollection.isDirty();
    };

    /**
     * Set model like modified.
     * @returns {*|boolean}
     */
    var setDirty = function(state) {
      manoeuvresCollection.setDirty(state);
    };

    /**
     * Refresh source link after form save.
     */
    var refresh = function() {
      if (current) {
        var linkId = current.linkId;
        current = null;
        open(linkId);
      }
    };

    /**
     * Save the target selected in the map to show radio buttons options
     *
     * @param targetRoadLink
     */
    var setTargetRoadLink = function(targetRoadLink) {
      targetRoadLinkSelected = targetRoadLink;
    };

    /**
     * Return the target selected in the map to show radio buttons options
     */
    var getTargetRoadLink = function() {
      return targetRoadLinkSelected;
    };

    /**
     * Returns true if Radio Buttons was showed. Used by ManoeuvreLayer to visualize if radio buttons options was showed before the redraw of the map.
     */
    var existTargetRoadLink = function() {
      return targetRoadLinkSelected !== null;
    };

    /**
     * Fetch a given manoeuvre from collection: if id is given it is required, otherwise using destination
     * @param manoeuvreId
     * @param destinationLinkId
     */
    var fetchManoeuvre = function(manoeuvreId, destinationLinkId) {
      var foundManoeuvres;
      if(get().manoeuvres.length !== 0){
        var manoeuvreList = get().manoeuvres.find(function (m) {
          return m.destLinkId === destinationLinkId && (!manoeuvreId ||
              m.manoeuvreId === manoeuvreId); });
        if (!manoeuvreList){
          foundManoeuvres = manoeuvresCollection.getManoeuvresBySourceLinkId(get().linkId);
          for(var i = 0; i < foundManoeuvres.length; i++) {
            if (foundManoeuvres[i].destLinkId === destinationLinkId) {
              return foundManoeuvres[i];
            }
          }
        }
      }
      else  {
        foundManoeuvres = manoeuvresCollection.getManoeuvresBySourceLinkId(get().linkId);
        for(var j = 0; j < foundManoeuvres.length; j++) {
          if (foundManoeuvres[j].destLinkId === destinationLinkId) {
            return foundManoeuvres[j];
          }
        }
      }
    };

    var getAdjacents = function(destLinkId){
      var target = current.adjacent.find(function (rl) {
        return rl.linkId == destLinkId;
      });
      if (!target) {
        target = current.nonAdjacentTargets.find(function (rl) {
          return rl.linkId == destLinkId;
        });
      }
      if (target) {
        return target.adjacentLinks;
      }
      return {};
    };

    return {
      close: close,
      open: open,
      getAdjacents: getAdjacents,
      updateAdjacents: updateAdjacents,
      get: get,
      getLinkId: getLinkId,
      exists: exists,
      addManoeuvre: addManoeuvre,
      removeManoeuvre: removeManoeuvre,
      addLink: addLink,
      removeLink: removeLink,
      setExceptions: setExceptions,
      setValidityPeriods: setValidityPeriods,
      setAdditionalInfo: setAdditionalInfo,
      save: save,
      cancel: cancel,
      isDirty: isDirty,
      setDirty: setDirty,
      refresh: refresh,
      setTargetRoadLink: setTargetRoadLink,
      getTargetRoadLink: getTargetRoadLink,
      existTargetRoadLink: existTargetRoadLink,
      fetchManoeuvre: fetchManoeuvre
    };
  };
})(this);
