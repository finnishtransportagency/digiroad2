(function(root) {
  var RoadLinkModel = function(data) {
    var selected = false;
    var original = _.clone(data);

    var getId = function() {
      return data.roadLinkId || data.linkId;
    };

    var getData = function() {
      return data;
    };

    var getPoints = function() {
      return _.cloneDeep(data.points);
    };

    var setLinkProperty = function(name, value) {
      if (value != data[name]) {
        data[name] = value;
      }
    };

    var select = function() {
      selected = true;
    };

    var unselect = function() {
      selected = false;
    };

    var isSelected = function() {
      return selected;
    };

    var isCarTrafficRoad = function() {
      return !_.isUndefined(data.linkType) && !_.contains([8, 9, 21, 99], data.linkType);
    };

    var cancel = function() {
      data.trafficDirection = original.trafficDirection;
      data.functionalClass = original.functionalClass;
      data.linkType = original.linkType;
    };

    return {
      getId: getId,
      getData: getData,
      getPoints: getPoints,
      setLinkProperty: setLinkProperty,
      isSelected: isSelected,
      isCarTrafficRoad: isCarTrafficRoad,
      select: select,
      unselect: unselect,
      cancel: cancel
    };
  };

  root.RoadCollection = function(backend) {
    var roadLinkGroups = [];
    var tmpRoadAddresses = [];
    var tmpNewRoadAddresses = [];
    var preMovedRoadAddresses = [];
    var changedIds = [];

    var roadLinks = function() {
      return _.flatten(roadLinkGroups);
    };


    var getSelectedRoadLinks = function() {
      return _.filter(roadLinks(), function(roadLink) {
        return roadLink.isSelected();
      });
    };


    this.fetch = function(boundingBox, zoom) {
      backend.getRoadLinks({boundingBox: boundingBox, zoom: zoom}, function(fetchedRoadLinks) {
        var selectedIds = _.map(getSelectedRoadLinks(), function(roadLink) {
          return roadLink.getId();
        });
        var fetchedRoadLinkModels = _.map(fetchedRoadLinks, function(roadLinkGroup) {
          return _.map(roadLinkGroup, function(roadLink) {
            return new RoadLinkModel(roadLink);
          });
        });
        roadLinkGroups = _.reject(fetchedRoadLinkModels, function(roadLinkGroup) {
          return _.some(roadLinkGroup, function(roadLink) {
            _.contains(selectedIds, roadLink.getId());
          });
        }).concat(getSelectedRoadLinks());
        eventbus.trigger('roadLinks:fetched', roadLinkGroups);
        if(applicationModel.isProjectButton()){
          eventbus.trigger('linkProperties:highlightSelectedProject', applicationModel.getProjectFeature());
          applicationModel.setProjectButton(false);
        }
      });
    };

    this.getRoadsForMassTransitStops = function() {
      return _.chain(roadLinks())
        .filter(function(roadLink) {
          return roadLink.isCarTrafficRoad() && (roadLink.getData().administrativeClass != "Unknown");
        })
        .map(function(roadLink) {
          return roadLink.getData();
        })
        .value();
    };

    this.getRoadLinkByLinkId = function (linkId) {
      return _.find(_.flatten(roadLinkGroups), function(road) { return road.getId() === linkId; });
    };

    this.getAll = function() {
      return _.map(roadLinks(), function(roadLink) {
        return roadLink.getData();
      });
    };

    this.getAllTmp = function(){
      return tmpRoadAddresses;
    };

    this.get = function(ids) {
      return _.map(ids, function(id) {
        return _.find(roadLinks(), function(road) { return road.getId() === id; });
      });
    };

    this.getByLinkId = function(ids) {
      var segments = _.filter(roadLinks(), function (road){
        return road.getData().linkId == ids;
      });
      return segments;
    };

    this.getById = function(ids) {
      return _.map(ids, function(id) {
        return _.find(roadLinks(), function(road) { return road.getData().id === id; });
      });
    };

    this.getGroup = function(id) {
      var group =_.find(roadLinkGroups, function(roadLinkGroup) {
        return _.some(roadLinkGroup, function(roadLink) {
          return roadLink.getId() === id;
        });
      });
      var road = _.first(group).getData();
      return processGroupOutput(road, group);
    };

    this.getGroupByLinkId = function (linkId) {
      var group = _.find(roadLinkGroups, function(roadLinkGroup) {
        return _.some(roadLinkGroup, function(roadLink) {
          return roadLink.getData().linkId === linkId;
        });
      });
      var road = _.first(group).getData();
      return processGroupOutput(road, group);
    };

    var processGroupOutput = function(road,group){
      if(roadIsOther(road) || roadIsUnknown(road)){
        return group;
      }
      else {
        var allGroups = _.filter(roadLinkGroups, function(rlg){
          return _.some(rlg, function(groupedRoad){
            return groupedRoad.getData().roadNumber === road.roadNumber && groupedRoad.getData().anomaly === road.anomaly && groupedRoad.getData().roadLinkType === road.roadLinkType && groupedRoad.getData().roadPartNumber === road.roadPartNumber && groupedRoad.getData().trackCode === road.trackCode;
          });
        });
        return _.flatten(allGroups);
      }
    };

    this.getGroupById = function (id) {
      return _.find(roadLinkGroups, function(roadLinkGroup) {
        return _.some(roadLinkGroup, function(roadLink) {
          return roadLink.getData().id === id;
        });
      });
    };

    this.setTmpRoadAddresses = function (tmp){
      tmpRoadAddresses = tmp;
    };

    this.setChangedIds = function (ids){
      changedIds = ids;
    };

    this.getChangedIds = function (){
      return changedIds;
    };

    this.reset = function(){
      roadLinkGroups = [];
    };
    this.resetTmp = function(){
      tmpRoadAddresses = [];
    };
    this.resetChangedIds = function(){
      changedIds = [];
    };

    this.setNewTmpRoadAddresses = function (tmp){
      tmpNewRoadAddresses = tmp;
    };

    this.getNewTmpRoadAddresses = function(){
      return tmpNewRoadAddresses;
    };

    this.resetNewTmpRoadAddresses = function(){
      tmpNewRoadAddresses = [];
    };

    this.addPreMovedRoadAddresses = function(ra){
      preMovedRoadAddresses.push(ra);
    };

    this.getPreMovedRoadAddresses = function(){
      return preMovedRoadAddresses;
    };

    this.resetPreMovedRoadAddresses = function(){
      preMovedRoadAddresses = [];
    };


    var roadIsOther = function(road){
      return  0 === road.roadNumber && 0 === road.anomaly && 0 === road.roadLinkType && 0 === road.roadPartNumber && 99 === road.trackCode;
    };

    var roadIsUnknown = function(road){
      return  0 === road.roadNumber && 1 === road.anomaly && 0 === road.roadLinkType && 0 === road.roadPartNumber && 99 === road.trackCode;
    };
  };
})(this);
