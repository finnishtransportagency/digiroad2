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
      if (value !== data[name]) {
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
      return !_.isUndefined(data.linkType) && !_.includes([8, 9, 21, 99], data.linkType);
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
    this.roadLinkGroups = [];
    this.roadLinkGroupsHistory = [];
    var me = this;

    this.roadLinks = function() {
      return _.flatten(me.roadLinkGroups);
    };

    var roadLinksHistory = function() {
      return _.flatten(me.roadLinkGroupsHistory);
    };

    var getSelectedRoadLinks = function() {
      return _.filter(me.roadLinks(), function(roadLink) {
        return roadLink.isSelected();
      });
    };

    var getSelectedRoadLinksHistory = function() {
      return _.filter(roadLinksHistory(), function(roadLink) {
        return roadLink.isSelected();
      });
    };

    this.fetch = function(boundingBox) {
      backend.getRoadLinks(boundingBox, function(fetchedRoadLinks) {
          var selectedIds = _.map(getSelectedRoadLinks(), function(roadLink) {
            return roadLink.getId();
          });
          var fetchedRoadLinkModels = _.map(fetchedRoadLinks, function(roadLinkGroup) {
            return _.map(roadLinkGroup, function(roadLink) {
              return new RoadLinkModel(roadLink);
            });
          });
          me.roadLinkGroups = _.reject(fetchedRoadLinkModels, function(roadLinkGroup) {
            return _.some(roadLinkGroup, function(roadLink) {
              _.includes(selectedIds, roadLink.getId());
            });
          }).concat(getSelectedRoadLinks());
        eventbus.trigger('roadLinks:fetched');
      });
    };

    this.fetchHistory = function (boundingBox) {
      backend.getHistoryRoadLinks(boundingBox, function (fetchedHistoryRoadLinks) {
        var selectedIds = _.map(getSelectedRoadLinksHistory(), function(roadLink) {
          return roadLink.getId();
        });
        var fetchedRoadLinkModels = _.map(fetchedHistoryRoadLinks, function(roadLinkGroup) {
          return _.map(roadLinkGroup, function(roadLink) {
            return new RoadLinkModel(roadLink);
          });
        });
        me.roadLinkGroupsHistory = _.reject(fetchedRoadLinkModels, function(roadLinkGroupHistory) {
          return _.some(roadLinkGroupHistory, function(roadLink) {
            _.includes(selectedIds, roadLink.getId());
          });
        }).concat(getSelectedRoadLinksHistory());
        eventbus.trigger('roadLinks:historyFetched');
      });
    };

    this.fetchWithComplementary = function(boundingBox) {
      backend.getRoadLinksWithComplementary(boundingBox, function(fetchedRoadLinks) {
        var selectedIds = _.map(getSelectedRoadLinks(), function(roadLink) {
          return roadLink.getId();
        });
        var fetchedRoadLinkModels = _.map(fetchedRoadLinks, function(roadLinkGroup) {
          return _.map(roadLinkGroup, function(roadLink) {
            return new RoadLinkModel(roadLink);
          });
        });
        me.roadLinkGroups = _.reject(fetchedRoadLinkModels, function(roadLinkGroup) {
          return _.some(roadLinkGroup, function(roadLink) {
            _.includes(selectedIds, roadLink.getId());
          });
        }).concat(getSelectedRoadLinks());
        eventbus.trigger('roadLinks:fetched');
      });
    };

    this.getRoadsForPointAssets = function() {
      return _.chain(me.roadLinks())
        .filter(function(roadLink) {
          return roadLink.isCarTrafficRoad() && (roadLink.getData().administrativeClass !== "Unknown");
        })
        .map(function(roadLink) {
          return roadLink.getData();
        })
        .value();
    };

    this.getRoadLinkByLinkId = function (linkId) {
      return _.find(_.flatten(me.roadLinkGroups), function(road) { return road.getId() === linkId; });
    };

    this.getAll = function() {
      return _.map(me.roadLinks(), function(roadLink) {
        return roadLink.getData();
      });
    };

    this.getAllHistory = function() {
      return _.map(roadLinksHistory(), function(roadLinkHistory){
        return roadLinkHistory.getData();
      });
    };

    this.get = function(ids) {
      return _.map(ids, function(id) {
        return _.find(me.roadLinks(), function(road) { return road.getId() === id; });
      });
    };

    this.getGroup = function(id) {
      return _.find(me.roadLinkGroups, function(roadLinkGroup) {
        return _.some(roadLinkGroup, function(roadLink) {
          return roadLink.getId() === id;
        });
      });
    };

    this.reset = function(){
      me.roadLinkGroups = [];
      me.roadLinkGroupsHistory = [];
    };

    this.resetHistory = function(){
      me.roadLinkGroupsHistory = [];
    };
  };
})(this);
