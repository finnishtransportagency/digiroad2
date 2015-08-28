(function(root) {
  var RoadLinkModel = function(data) {
    var selected = false;
    var original = _.clone(data);

    var getId = function() {
      return data.roadLinkId || data.mmlId;
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
        eventbus.trigger('linkProperties:changed');
      }
    };

    var setTrafficDirection = _.partial(setLinkProperty, 'trafficDirection');
    var setFunctionalClass = _.partial(setLinkProperty, 'functionalClass');
    var setLinkType = _.partial(setLinkProperty, 'linkType');

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
      setTrafficDirection: setTrafficDirection,
      setFunctionalClass: setFunctionalClass,
      setLinkType: setLinkType,
      isSelected: isSelected,
      isCarTrafficRoad: isCarTrafficRoad,
      select: select,
      unselect: unselect,
      cancel: cancel
    };
  };

  root.RoadCollection = function(backend) {
    var roadLinks = [];

    var self = this;

    var getSelectedRoadLinks = function() {
      return _.filter(_.flatten(roadLinks), function(roadLink) {
        return roadLink.isSelected();
      });
    };

    this.fetch = function(boundingBox, zoom) {
      backend.getRoadLinks(boundingBox, function(fetchedRoadLinks) {
        var selectedIds = _.map(getSelectedRoadLinks(), function(roadLink) {
          return roadLink.getId();
        });
        var fetchedRoadLinkModels = _.map(fetchedRoadLinks, function(roadLink) {
          return new RoadLinkModel(roadLink);
        });
        roadLinks = _.reject(fetchedRoadLinkModels, function(roadLink) {
          return _.contains(selectedIds, roadLink.getId());
        }).concat(getSelectedRoadLinks());
        eventbus.trigger('roadLinks:fetched');
      });
    };

    this.fetchFromVVH = function(boundingBox) {
      backend.getRoadLinksFromVVH(boundingBox, function(fetchedRoadLinks) {
        var selectedIds = _.map(getSelectedRoadLinks(), function(roadLink) {
          return roadLink.getId();
        });
        var fetchedRoadLinkModels = _.map(fetchedRoadLinks, function(roadLinkGroup) {
          return _.map(roadLinkGroup, function(roadLink) {
              return new RoadLinkModel(roadLink);
            });
        });
        roadLinks = _.reject(fetchedRoadLinkModels, function(roadLinkGroup) {
          return _.some(roadLinkGroup, function(roadLink) {
            _.contains(selectedIds, roadLink.getId());
          });
        }).concat(getSelectedRoadLinks());
        eventbus.trigger('roadLinks:fetched');
      });
    };

    this.getAllCarTrafficRoads = function() {
      return _.chain(_.flatten(roadLinks))
        .filter(function(roadLink) {
          return roadLink.isCarTrafficRoad();
        })
        .map(function(roadLink) {
          return roadLink.getData();
        })
        .value();
    };

    this.getAll = function() {
      return _.map(_.flatten(roadLinks), function(roadLink) {
        return roadLink.getData();
      });
    };

    this.get = function(id) {
      return _.find(_.flatten(roadLinks), function(road) {
        return road.getId() === id;
      });
    };

    this.getGroup = function(id) {
      return _.find(roadLinks, function(roadLinkGroup) {
        return _.some(roadLinkGroup, function(roadLink) {
          return roadLink.getId() === id;
        });
      });
    };

    this.reset = function(){
      roadLinks = [];
    };
  };
})(this);
