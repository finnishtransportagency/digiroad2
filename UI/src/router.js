(function (root) {
  root.URLRouter = function(map, backend, models) {

    var mapCenterAndZoom = function(x, y, zoom){
      var mapView = map.getView();
      mapView.setCenter([x, y]);
      mapView.setZoom(zoom);
    };

    var Router = Backbone.Router.extend({
      initialize: function () {
        // Support legacy format for opening mass transit stop via ...#300289
        this.route(/^(\d+)$/, function (nationalId) {
          this.massTransitStop(nationalId);
        });

        this.route(/^([A-Za-z]+)$/, function (layer) {
          applicationModel.selectLayer(layer);
        });

        this.route(/^$/, function () {
          applicationModel.selectLayer('massTransitStop');
        });
      },

      routes: {
        'massTransitStop/:id': 'massTransitStop',
        'asset/:id': 'massTransitStop',
        'linkProperty/:linkId': 'linkProperty',
        'linkProperty/mml/:mmlId': 'linkPropertyByMml',
        'speedLimit/:linkId': 'speedLimit',
        'pedestrianCrossings/:id': 'pedestrianCrossings',
        'trafficLights/:id': 'trafficLights',
        'obstacles/:id': 'obstacles',
        'railwayCrossings/:id': 'railwayCrossings',
        'directionalTrafficSigns/:id': 'directionalTrafficSigns',
        'trafficSigns/:id': 'trafficSigns',
        'maintenanceRoad/:linkId': 'maintenanceRoad',
        'work-list/speedLimit': 'speedLimitWorkList',
        'work-list/linkProperty': 'linkPropertyWorkList',
        'work-list/massTransitStop': 'massTransitStopWorkList',
        'work-list/pedestrianCrossings': 'pedestrianCrossingWorkList',
        'work-list/trafficLights': 'trafficLightWorkList',
        'work-list/obstacles': 'obstacleWorkList',
        'work-list/railwayCrossings': 'railwayCrossingWorkList',
        'work-list/directionalTrafficSigns': 'directionalTrafficSignsWorkList',
        'work-list/trafficSigns': 'trafficSignWorkList',
        'work-list/maintenanceRoad': 'maintenanceRoadWorkList',
        'work-list/municipality': 'municipalityWorkList'
      },

      massTransitStop: function (id) {
        applicationModel.selectLayer('massTransitStop');
        backend.getMassTransitStopByNationalId(id, function (massTransitStop) {
          eventbus.once('massTransitStops:available', function () {
            models.selectedMassTransitStopModel.changeByExternalId(id);
          });
          mapCenterAndZoom(massTransitStop.lon, massTransitStop.lat, 12);
        });
      },

      linkProperty: function (linkId) {
        applicationModel.selectLayer('linkProperty');
        backend.getRoadLinkByLinkId(linkId, function (response) {
          if (response.success) {
            if (response.source === 1) {
              eventbus.once('linkProperties:available', function () {
                models.selectedLinkProperty.open(response.id);
              });
            } else if (response.source === 2) {
              eventbus.once('linkProperties:available', function () {
                eventbus.trigger('roadLinkComplementaryCheckBox:check');
                eventbus.trigger('roadLinkComplementary:show');
                eventbus.once('linkProperties:available', function () {
                  models.selectedLinkProperty.open(response.id);
                });
              });
            }
            mapCenterAndZoom(response.middlePoint.x, response.middlePoint.y, 12);
          }
          else
          {
            //TODO might be nice to show error message for user if roadlink  applied to #linkProperty/ url does not exist
          }
        });
      },

      linkPropertyByMml: function (mmlId) {
        applicationModel.selectLayer('linkProperty');
        backend.getRoadLinkByMmlId(mmlId, function (response) {
          eventbus.once('linkProperties:available', function () {
            models.selectedLinkProperty.open(response.id);
          });
          mapCenterAndZoom(response.middlePoint.x, response.middlePoint.y, 12);
        });
      },

      speedLimit: function (linkId) {
        var roadLinkReceived = backend.getRoadLinkByLinkId(linkId);
        var layerSelected = eventbus.oncePromise('layer:speedLimit:shown');
        applicationModel.selectLayer('speedLimit');
        $.when(layerSelected).then(function () {
          var mapMoved = $.when(roadLinkReceived).then(function (response) {
            var promise = eventbus.oncePromise('layer:speedLimit:moved');
            mapCenterAndZoom(response.middlePoint.x, response.middlePoint.y, 12);
            return promise;
          });
          $.when(mapMoved).then(function () {
            eventbus.trigger('speedLimit:selectByLinkId', parseInt(linkId, 10));
          });
        });
      },

      maintenanceRoad: function (id) {
        applicationModel.selectLayer('maintenanceRoad');
        var linearAsset = models.selectedMaintenanceRoad.getLinearAsset(parseInt(id));
        if (linearAsset) {
          models.selectedMaintenanceRoad.open(linearAsset, true);
          applicationModel.setSelectedTool('Select');
        }
        backend.getLinearAssetById(id, 'maintenanceRoad').then(function (result) {
          eventbus.once('maintenanceRoads:fetched', function() {
            var linearAsset = models.selectedMaintenanceRoad.getLinearAsset(result.id);
            models.selectedMaintenanceRoad.open(linearAsset, true);
            applicationModel.setSelectedTool('Select');
          });
          mapCenterAndZoom(result.middlePoint.x, result.middlePoint.y, 12);
        });
      },

      pedestrianCrossings: function (id) {
        applicationModel.selectLayer('pedestrianCrossings');
        backend.getPointAssetById(id, 'pedestrianCrossings').then(function (result) {
          mapCenterAndZoom(result.lon, result.lat, 12);
          models.selectedPedestrianCrossing.open(result);
        });
      },

      trafficLights: function (id) {
        applicationModel.selectLayer('trafficLights');
        backend.getPointAssetById(id, 'trafficLights').then(function (result) {
          mapCenterAndZoom(result.lon, result.lat, 12);
          models.selectedTrafficLight.open(result);
        });
      },

      trafficSigns: function (id) {
        applicationModel.selectLayer('trafficSigns');
        backend.getPointAssetById(id, 'trafficSigns').then(function (result) {
          mapCenterAndZoom(result.lon, result.lat, 12);
          models.selectedTrafficSign.open(result);
        });
      },

      obstacles: function (id) {
        applicationModel.selectLayer('obstacles');
        backend.getPointAssetById(id, 'obstacles').then(function (result) {
          mapCenterAndZoom(result.lon, result.lat, 12);
          models.selectedObstacle.open(result);
        });
      },

      railwayCrossings: function (id) {
        applicationModel.selectLayer('railwayCrossings');
        backend.getPointAssetById(id, 'railwayCrossings').then(function (result) {
          mapCenterAndZoom(result.lon, result.lat, 12);
          models.selectedRailwayCrossing.open(result);
        });
      },

      directionalTrafficSigns: function (id) {
        applicationModel.selectLayer('directionalTrafficSigns');
        backend.getPointAssetById(id, 'directionalTrafficSigns').then(function (result) {
          mapCenterAndZoom(result.lon, result.lat, 12);
          models.selectedDirectionalTrafficSign.open(result);
        });
      },

      speedLimitWorkList: function () {
        eventbus.trigger('workList:select', 'speedLimit', backend.getUnknownLimits());
      },

      linkPropertyWorkList: function () {
        eventbus.trigger('workList:select', 'linkProperty', backend.getIncompleteLinks());
      },

      massTransitStopWorkList: function () {
        eventbus.trigger('workList:select', 'massTransitStop', backend.getFloatingMassTransitStops());
      },

      pedestrianCrossingWorkList: function () {
        eventbus.trigger('workList:select', 'pedestrianCrossings', backend.getFloatinPedestrianCrossings());
      },

      trafficLightWorkList: function () {
        eventbus.trigger('workList:select', 'trafficLights', backend.getFloatingTrafficLights());
      },

      trafficSignWorkList: function () {
        eventbus.trigger('workList:select', 'trafficSigns', backend.getFloatingTrafficSigns());
      },

      obstacleWorkList: function () {
        eventbus.trigger('workList:select', 'obstacles', backend.getFloatingObstacles());
      },
      railwayCrossingWorkList: function () {
        eventbus.trigger('workList:select', 'railwayCrossings', backend.getFloatingRailwayCrossings());
      },

      directionalTrafficSignsWorkList: function () {
        eventbus.trigger('workList:select', 'directionalTrafficSigns', backend.getFloatingDirectionalTrafficSigns());
      },

      maintenanceRoadWorkList: function () {
        eventbus.trigger('workList:select', 'maintenanceRoad', backend.getLinearAssetUnchecked(290));
      },

      municipalityWorkList: function () {
        eventbus.trigger('municipality:select', backend.getUncheckedMunicipalities());
      }

    });

    var router = new Router();

    // We need to restart the router history so that tests can reset
    // the application before each test.
    Backbone.history.stop();
    Backbone.history.start();

    eventbus.on('asset:closed', function () {
      router.navigate('massTransitStop');
    });

    eventbus.on('asset:fetched asset:created', function (asset) {
      router.navigate('massTransitStop/' + asset.nationalId);
    });

    // Focus to mass transit stop asset after national id search
    eventbus.on('nationalId:selected', function (result) {
      router.navigate('massTransitStop/' + result.nationalId, {trigger: true});
    });

    eventbus.on('linkProperties:unselected', function () {
      router.navigate('linkProperty');
    });

    eventbus.on('linkProperties:selected', function (linkProperty) {
      router.navigate('linkProperty/' + linkProperty.linkId);
    });

    eventbus.on('layer:selected', function (layer) {
      router.navigate(layer);
    });
  };
})(this);