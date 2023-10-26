(function (root) {
  root.URLRouter = function(map, backend, models) {

    var mapCenterAndZoom = function(x, y, zoom){
      var mapView = map.getView();
      mapView.setCenter([x, y]);
      mapView.setZoom(zoom);
    };

    function fetchLinearAssetEvent (asset, result) {
      eventbus.once(asset.multiElementEventCategory + ':fetched', function() {
        var linearAsset = asset.selectedLinearAsset.getLinearAsset(result.id);
        if (linearAsset) {
          asset.selectedLinearAsset.open(linearAsset, true);
          applicationModel.setSelectedTool('Select');
        }
      });
    }

    function fetchSpeedLimitEvent (asset, result) {
      eventbus.once('speedLimits:redrawed', function() {
        var speedLimit = asset.getSpeedLimitById(result.id);
        if (speedLimit) {
          eventbus.trigger('speedLimits:enableTrafficSigns');
          asset.open(speedLimit, true);
          applicationModel.setSelectedTool('Select');
        }
      });
    }

    var linearCentering = function(layerName, id){
      applicationModel.selectLayer(layerName);
      var asset = _(models.linearAssets).find({ layerName: layerName });
      var linearAsset = asset.selectedLinearAsset.getLinearAsset(parseInt(id));
      if (linearAsset) {
        asset.selectedLinearAsset.open(linearAsset, true);
        applicationModel.setSelectedTool('Select');
      }
      backend.getLinearAssetMidPoint(asset.typeId, id).then(function (result) {
        if(result.success){
          if(result.source === 1){
            fetchLinearAssetEvent(asset, result);
          }else if(result.source === 2) {
            eventbus.once(asset.multiElementEventCategory + ':fetched', function () {
              eventbus.trigger(layerName + ':activeComplementaryLayer');
              eventbus.trigger('complementaryLinks:show');
              fetchLinearAssetEvent(asset, result);
            });
          }
          mapCenterAndZoom(result.middlePoint.x, result.middlePoint.y, 12);
        }
      });
    };

    var pointCentering = function (layerName, id) {
      applicationModel.selectLayer(layerName);
      var asset = _(models.pointAssets).find({layerName: layerName});
      backend.getPointAssetById(id, layerName).then(function (result) {
        mapCenterAndZoom(result.lon, result.lat, 12);

        asset.selectedPointAsset.open(result);
      });
    };

    var speedLimitCentering = function (layerName, id) {
      applicationModel.selectLayer(layerName);
      var asset = models.selectedSpeedLimit;
      var speedLimit = asset.getSpeedLimitById(parseInt(id));
      if (speedLimit) {
        asset.open(speedLimit, true);
        applicationModel.setSelectedTool('Select');
      }
      backend.getLinearAssetMidPoint(20, id).then(function (result) {
        if (result.success) {
          if (result.source === 1) {
            fetchSpeedLimitEvent(asset, result);
          } else if (result.source === 2) {
            eventbus.once(asset.multiElementEventCategory + ':fetched', function () {
              eventbus.trigger(layerName + ':activeComplementaryLayer');
              eventbus.trigger('complementaryLinks:show');
              fetchSpeedLimitEvent(asset, result);
            });
          }
          mapCenterAndZoom(result.middlePoint.x, result.middlePoint.y, 12);
        }
      });
    };

    var pointAssetCentering = function (layerName, id,  model) {
        applicationModel.selectLayer(layerName);
        backend.getPointAssetById(id, layerName).then(function (result) {
            mapCenterAndZoom(result.lon, result.lat, 12);
            model.open(result);
        });
    };

    var linearAssetMapCenterAndZoom  = function (layerName, idType, id) {
      if(idType) {
        applicationModel.selectLayer(layerName);
        backend.getRoadLinkByLinkId(id, function (response) {
          if (response.success)
            mapCenterAndZoom(response.middlePoint.x, response.middlePoint.y, 12);
        });
      } else
        linearCentering(layerName, id);
    };

    var pointAssetMapCenterAndZoom  = function (layerName, idType, id) {
      if(idType) {
        applicationModel.selectLayer(layerName);
        backend.getRoadLinkByLinkId(id, function (response) {
          if (response.success)
            mapCenterAndZoom(response.middlePoint.x, response.middlePoint.y, 12);
        });
      } else
        pointCentering(layerName, id);
    };

    var manoeuvreMapCenterAndZoom = function(linkId){
      applicationModel.selectLayer('manoeuvre');

      backend.getRoadLinkByLinkId(linkId, function (response) {
        eventbus.once('manoeuvres:fetched', function () {
          if (!_.isUndefined(models.selectedManoeuvreSource.getByLinkId(linkId)))
            models.selectedManoeuvreSource.open(linkId);
        });
        mapCenterAndZoom(response.middlePoint.x, response.middlePoint.y, 12);
      });
    };

    var getLinearAssetType = function(layerName) {
      return _(models.linearAssets).find({layerName: layerName}).typeId;
    };

    var Router = Backbone.Router.extend({
      initialize: function () {
        // Support legacy format for opening mass transit stop via ...#300289
        this.route(/^(\d+)$/, function (nationalId) {
          this.massTransitStopNationalId(nationalId);
        });

        this.route(/^([A-Za-z]+)$/, function (layer) {
          applicationModel.selectLayer(layer);
        });

        this.route(/^$/, function () {
          applicationModel.selectLayer('massTransitStop');
        });

        this.stateHistory = null;

      },

      routes: {
        'massTransitStopNationalId/:id': 'massTransitStopNationalId',
        'asset/:id': 'massTransitStopNationalId',
        'linkProperty/:linkId': 'linkProperty',
        'linkProperty/mml/:mmlId': 'linkPropertyByMml',
        'speedLimitUnknown/:linkId(/municipality/:municipalityId/:position)': 'speedLimitLinkId',
        'speedLimitErrors/:id': 'speedLimit',
        'hazardousMaterialTransportProhibitionErrors(/:typeId)/:id': 'hazardousMaterialTransportProhibitionErrors',
        'manoeuvreErrors(/:typeId)/:id': 'manoeuvreErrors',
        'heightLimitErrors(/:typeId)/:id': 'heightLimitErrors',
        'bogieWeightLimitErrors(/:typeId)/:id': 'bogieWeightErrors',
        'axleWeightLimitErrors(/:typeId)/:id': 'axleWeightLimitErrors',
        'lengthLimitErrors(/:typeId)/:id': 'lengthLimitErrors',
        'totalWeightLimitErrors(/:typeId)/:id': 'totalWeightLimitErrors',
        'trailerTruckWeightLimitErrors(/:typeId)/:id': 'trailerTruckWeightLimitErrors',
        'widthLimitErrors(/:typeId)/:id': 'widthLimitErrors',
        'pedestrianCrossingsErrors(/:typeId)/:id': 'pedestrianCrossingsErrors',

        'massTransitStop/:id': 'massTransitStop',
        'speedLimit/:id': 'speedLimit',
        'pedestrianCrossings/:id': 'pedestrianCrossings',
        'trafficLights/:id': 'trafficLights',
        'obstacles/:id': 'obstacles',
        'railwayCrossings/:id': 'railwayCrossings',
        'directionalTrafficSigns/:id': 'directionalTrafficSigns',
        'servicePoints/:id': 'servicePoints',
        'trafficSigns/:id': 'trafficSigns',
        'trHeightLimits/:id': 'trHeightLimits',
        'trWidthLimits/:id' : 'trWidthLimits',
        'trWeightLimits/:id'  : 'trWeightLimits',
        'maintenanceRoad/:id': 'maintenanceRoad',
        'litRoad/:id': 'litRoad',
        'roadDamagedByThaw/:id' : 'roadDamagedByThaw',
        'roadWidth/:id': 'roadWidth',
        'pavedRoad/:id': 'pavedRoad',
        'trafficVolume/:id': 'trafficVolume',
        'numberOfLanes/:id': 'numberOfLanes',
        'massTransitLanes/:id': 'massTransitLanes',
        'winterSpeedLimits/:id': 'winterSpeedLimits',
        'trSpeedLimits/:id': 'trSpeedLimits',
        'prohibition/:id': 'prohibition',
        'hazardousMaterialTransportProhibition/:id': 'hazardousMaterialTransportProhibition',
        'europeanRoads/:id' : 'europeanRoads',
        'exitNumbers/:id': 'exitNumbers',
        'totalWeightLimit/:id': 'totalWeightLimit',
        'trailerTruckWeightLimit/:id': 'trailerTruckWeightLimit',
        'axleWeightLimit/:id': 'axleWeightLimit',
        'bogieWeightLimit/:id': 'bogieWeightLimit',
        'heightLimit/:id': 'heightLimit',
        'lengthLimit/:id': 'lengthLimit',
        'widthLimit/:id': 'widthLimit',
        'manoeuvres/:id': 'manoeuvres',
        'parkingProhibition/:id': 'parkingProhibition',
        'cyclingAndWalking/:id': 'cyclingAndWalking',
        'laneModellingTool/id': 'laneModellingTool',
        'roadWorksAsset/:id': 'roadWork',
        'work-list/speedLimit(/:administrativeClass)': 'speedLimitWorkList',
        'work-list/speedLimit/municipality(/:id)': 'speedLimitMunicipalitiesWorkList',
        'work-list/speedLimitErrors(/:administrativeClass)': 'speedLimitErrorsWorkList',

        'work-list/hazardousMaterialTransportProhibitionErrors': 'hazardousMaterialProhibitionErrorsWorkList',
        'work-list/manoeuvreErrors': 'manoeuvreErrorsWorkList', 
        'work-list/manoeuvreSamuutusWorkList': 'manoeuvreSamuutusWorkList',
        'work-list/heightLimitErrors': 'heightLimitErrorsWorkList',
        'work-list/bogieWeightLimitErrors': 'bogieWeightLimitErrorsWorkList',
        'work-list/lengthLimitErrors': 'lengthLimitErrorsWorkList',
        'work-list/axleWeightLimitErrors': 'axleWeightLimitErrorsWorkList',
        'work-list/totalWeightLimitErrors': 'totalWeightLimitErrorsWorkList',
        'work-list/trailerTruckWeightLimitErrors': 'trailerTruckWeightLimitErrorsWorkList',
        'work-list/widthLimitErrors': 'widthLimitErrorsWorkList',
        'work-list/pedestrianCrossingsErrors': 'pedestrianCrossingsErrorsWorkList',

        'work-list/linkProperty': 'linkPropertyWorkList',
        'work-list/massTransitStop': 'massTransitStopWorkList',
        'work-list/pedestrianCrossings': 'pedestrianCrossingWorkList',
        'work-list/trafficLights': 'trafficLightWorkList',
        'work-list/obstacles': 'obstacleWorkList',
        'work-list/railwayCrossings': 'railwayCrossingWorkList',
        'work-list/directionalTrafficSigns': 'directionalTrafficSignsWorkList',
        'work-list/trafficSigns': 'trafficSignWorkList',
        'work-list/maintenanceRoad': 'maintenanceRoadWorkList',
        'work-list/municipality(/:municipalityCode)': 'municipalityWorkList',
        'work-list/privateRoads/:municipalityCode' : 'privateRoadsWorkList',
        'work-list/municipality': 'municipalityWorkList',
        'work-list/suggestedAssets/:municipalityId/:assetTypeId': 'suggestedAssetsWorkList',
        'work-list/autoGeneratedAssets': 'autoGeneratedAssets',
        'work-list/csvReports': 'csvReports',
        'work-list/laneChecklist': 'laneWorkList',
        'work-list/:layerName': 'unverifiedLinearAssetWorkList',
        ':layerName/linkId/:linkId': 'mapMoving'
      },

      massTransitStopNationalId: function (id) {
        applicationModel.selectLayer('massTransitStop');
        var assetFound = function (massTransitStop) {
          eventbus.once('massTransitStops:available', function () {
            models.selectedMassTransitStopModel.changeByNationalId(id);
          });
            // center and zoom only if request succeed
            // when calling MassServiceStopByNationalId
          if(massTransitStop.success !== false){
              mapCenterAndZoom(massTransitStop.lon, massTransitStop.lat, 12);
          }
        };

        backend.getMassTransitStopByNationalId(id, assetFound);
        backend.getMassServiceStopByNationalId(id, assetFound);
      },

      massTransitStop: function (id) {
        applicationModel.selectLayer('massTransitStop');
        var assetFound = function (massTransitStop) {
          eventbus.once('massTransitStops:available', function () {
            models.selectedMassTransitStopModel.changeById(id);
          });
          mapCenterAndZoom(massTransitStop.lon, massTransitStop.lat, 12);
        };

        backend.getMassTransitStopById(id, assetFound);
        backend.getMassServiceStopById(id, assetFound);
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
          else {
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

      speedLimitLinkId: function (linkId, municipalityId,  position) {
        if(position)
          this.stateHistory = {municipality: Number(municipalityId), position: position};

        applicationModel.selectLayer('speedLimit');
        backend.getRoadLinkByLinkId(linkId, function (response) {
          eventbus.once('speedLimits:fetched', function () {
            var asset = models.selectedSpeedLimit.getSpeedLimit(response.id);
            models.selectedSpeedLimit.open(asset, true);
            models.selectedSpeedLimit.cancel();
          });
          mapCenterAndZoom(response.middlePoint.x, response.middlePoint.y, 12);
        });
      },

      speedLimit: function (id) {
         speedLimitCentering('speedLimit', id);
      },

      manoeuvres: function(linkId){
        applicationModel.selectLayer('manoeuvre');
            backend.getRoadLinkByLinkId(linkId, function (response) {
                if (response.success) {
                    mapCenterAndZoom(response.middlePoint.x, response.middlePoint.y, 12);
                    eventbus.once('manoeuvres:fetched', function () {
                        models.selectedManoeuvreSource.open(response.id);
                    });
                }
            });
      },

      pedestrianCrossings: function (id) {
        pointAssetCentering('pedestrianCrossings', id,  models.selectedPedestrianCrossing);
      },

      trafficLights: function (id) {
        pointAssetCentering('trafficLights', id,  models.selectedTrafficLight);
      },

      trafficSigns: function (id) {
        pointAssetCentering('trafficSigns', id,  models.selectedTrafficSign);
      },

      obstacles: function (id) {
        pointAssetCentering('obstacles', id,  models.selectedObstacle);
      },

      railwayCrossings: function (id) {
        pointAssetCentering('railwayCrossings', id,  models.selectedRailwayCrossing);
      },

      directionalTrafficSigns: function (id) {
        pointAssetCentering('directionalTrafficSigns', id,  models.selectedDirectionalTrafficSign);
      },

      servicePoints: function (id) {
        pointAssetCentering('servicePoints', id,  models.selectedServicePoint);
      },

      trHeightLimits : function (id) {
        pointAssetCentering('trHeightLimits', id,  models.selectedTrHeightLimits);
      },

      trWidthLimits:  function (id) {
        pointAssetCentering('trWidthLimits', id,  models.selectedTrWidthLimits);
      },

      trWeightLimits: function (id) {
        applicationModel.selectLayer('trWeightLimits');
        backend.getPointAssetById(id, 'groupedPointAssets').then(function (result) {
            mapCenterAndZoom(result[0].lon, result[0].lat, 12);
            models.selectedGroupPointAsset.open(result[0]);
        });
      },

      speedLimitWorkList: function (municipality) {
        eventbus.trigger('workList:select', 'speedLimitUnknown', backend.getUnknownLimits(municipality));
      },

      speedLimitMunicipalitiesWorkList: function (id) {
        if(id || this.stateHistory) {
          var municipalityId = id ? id : this.stateHistory.municipality;
          eventbus.trigger('speedLimitMunicipality:select', backend.getUnknownLimitsMunicipality(municipalityId), this.stateHistory);
        }
        else
          eventbus.trigger('municipalities:select', backend.getMunicipalitiesWithUnknowns());
        this.stateHistory = null;
      },

      speedLimitErrorsWorkList: function (municipality) {
        eventbus.trigger('workList-inaccurate:select', 'speedLimitErrors', backend.getSpeedLimitErrors(municipality));
      },

      hazardousMaterialProhibitionErrorsWorkList: function () {
        eventbus.trigger('workList-inaccurate:select', 'hazardousMaterialTransportProhibitionErrors', backend.getInaccurateAssets(getLinearAssetType('hazardousMaterialTransportProhibition')));
      },

      manoeuvreErrorsWorkList: function () {
        eventbus.trigger('workList-inaccurate:select', 'manoeuvreErrors', backend.getInaccurateManoeuvre());
      },

      heightLimitErrorsWorkList: function () {
        eventbus.trigger('workList-inaccurate:select', 'heightLimitErrors', backend.getInaccurateAssets(getLinearAssetType('heightLimit')));
      },

      lengthLimitErrorsWorkList: function(){
        eventbus.trigger('workList-inaccurate:select', 'lengthLimitErrors', backend.getInaccurateAssets(getLinearAssetType('lengthLimit')));
      },

      bogieWeightLimitErrorsWorkList: function () {
        eventbus.trigger('workList-inaccurate:select', 'bogieWeightLimitErrors', backend.getInaccurateAssets(getLinearAssetType('bogieWeightLimit')));
      },

      axleWeightLimitErrorsWorkList: function () {
        eventbus.trigger('workList-inaccurate:select', 'axleWeightLimitErrors', backend.getInaccurateAssets(getLinearAssetType('axleWeightLimit')));
      },

      totalWeightLimitErrorsWorkList: function () {
        eventbus.trigger('workList-inaccurate:select', 'totalWeightLimitErrors', backend.getInaccurateAssets(getLinearAssetType('totalWeightLimit')));
      },

      trailerTruckWeightLimitErrorsWorkList: function () {
        eventbus.trigger('workList-inaccurate:select', 'trailerTruckWeightLimitErrors', backend.getInaccurateAssets(getLinearAssetType('trailerTruckWeightLimit')));
      },

      widthLimitErrorsWorkList: function () {
        eventbus.trigger('workList-inaccurate:select', 'widthLimitErrors', backend.getInaccurateAssets(getLinearAssetType('widthLimit')));
      },

      pedestrianCrossingsErrorsWorkList: function () {
        eventbus.trigger('workList-inaccurate:select', 'pedestrianCrossingsErrors', backend.getInaccuratePointAssets());
      },

      linkPropertyWorkList: function () {
        eventbus.trigger('workList:select', 'linkProperty', backend.getIncompleteLinks());
      },

      massTransitStopWorkList: function () {
        eventbus.trigger('workList:select', 'massTransitStopNationalId', backend.getFloatingMassTransitStops());
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
        eventbus.trigger('workList:select', 'maintenanceRoad', backend.getUncheckedLinearAsset(290));
      },

      municipalityWorkList: function (municipalityCode) {
        eventbus.trigger('municipality:select', backend.getMunicipalities(), municipalityCode);
      },

      suggestedAssetsWorkList: function (municipalityCode, assetTypeId) {
        eventbus.trigger('suggestedAssets:select', backend.getSuggestedAssetsById(municipalityCode, assetTypeId));
      },

      autoGeneratedAssets: function() {
        eventbus.trigger('autoGeneratedAssets:select');
      },

      csvReports: function () {
        eventbus.trigger('csvReports:select', backend.getMunicipalities());
      },
        manoeuvreSamuutusWorkList: function () {
            eventbus.trigger('workList-manoeuvreSamuutus:select','manoeuvreSamuutusWorkList', backend.getManoeuvreSamuutusWorkList());
        },

      laneWorkList: function () {
          eventbus.trigger('workList-laneModellingTool:select', 'lanes', backend.getLaneWorkList());
      },


      hazardousMaterialTransportProhibitionErrors: function (idType , linkId) {
        linearAssetMapCenterAndZoom('hazardousMaterialTransportProhibition', idType , linkId);
      },

      manoeuvreErrors: function (idType , linkId) {
        manoeuvreMapCenterAndZoom(linkId);
      },

      mapMoving: function (layerName, linkId) {
        applicationModel.selectLayer(layerName);
        backend.getRoadLinkByLinkId(linkId, function (response) {
          if (response.success)
            mapCenterAndZoom(response.middlePoint.x, response.middlePoint.y, 12);
        });
      },

      heightLimitErrors: function (idType , linkId) {
        linearAssetMapCenterAndZoom('heightLimit', idType, linkId);
      },

      bogieWeightErrors: function (idType, linkId) {
        linearAssetMapCenterAndZoom('bogieWeightLimit', idType, linkId);
      },

      axleWeightLimitErrors: function (idType, linkId) {
        linearAssetMapCenterAndZoom('axleWeightLimit', idType, linkId);
      },

      lengthLimitErrors: function (idType, linkId) {
        linearAssetMapCenterAndZoom('lengthLimit', idType, linkId);
      },

      totalWeightLimitErrors: function (idType, linkId) {
        linearAssetMapCenterAndZoom('totalWeightLimit', idType, linkId);
      },

      trailerTruckWeightLimitErrors: function (idType, linkId) {
        linearAssetMapCenterAndZoom('trailerTruckWeightLimit', idTypev, linkId);
      },

      widthLimitErrors: function (idType, linkId) {
        linearAssetMapCenterAndZoom('widthLimit', idType, linkId);
      },

      pedestrianCrossingsErrors: function (idType, id) {
        pointAssetMapCenterAndZoom('pedestrianCrossings', idType, id);
      },

      maintenanceRoad: function (id) {
        linearCentering('maintenanceRoad', id);
      },

      litRoad: function (id) {
        linearCentering('litRoad', id);
      },

      roadDamagedByThaw:   function (id) {
        linearCentering('roadDamagedByThaw', id);
      },

      roadWidth: function (id) {
        linearCentering('roadWidth', id);
      },

      pavedRoad:  function (id) {
        linearCentering('pavedRoad', id);
      },

      trafficVolume: function (id) {
          linearCentering('trafficVolume', id);
      },

      numberOfLanes: function (id) {
        linearCentering('numberOfLanes', id);
      },

      massTransitLanes: function (id) {
        linearCentering('massTransitLanes', id);
      },

      winterSpeedLimits: function (id) {
        linearCentering('winterSpeedLimits', id);
      },

      trSpeedLimits:  function (id) {
        linearCentering('trSpeedLimits', id);
      },

      prohibition: function (id) {
        linearCentering('prohibition', id);
      },

      hazardousMaterialTransportProhibition: function (id) {
        linearCentering('hazardousMaterialTransportProhibition', id);
      },

      europeanRoads: function (id) {
        linearCentering('europeanRoads', id);
      },

      exitNumbers: function (id) {
        linearCentering('exitNumbers', id);
      },

      totalWeightLimit: function (id) {
        linearCentering('totalWeightLimit', id);
      },

      trailerTruckWeightLimit: function (id) {
        linearCentering('trailerTruckWeightLimit', id);
      },

      axleWeightLimit: function (id) {
        linearCentering('axleWeightLimit', id);
      },

      bogieWeightLimit: function (id) {
        linearCentering('bogieWeightLimit', id);
      },

      heightLimit: function (id) {
        linearCentering('heightLimit', id);
      },

      lengthLimit: function (id) {
        linearCentering('lengthLimit', id);
      },

      widthLimit: function (id) {
        linearCentering('widthLimit', id);
      },

      parkingProhibition: function (id) {
        linearCentering('parkingProhibition', id);
      },

      cyclingAndWalking: function (id) {
        linearCentering('cyclingAndWalking', id);
      },

      laneModellingTool: function (id) {
        linearCentering('laneModellingTool', id);
      },

      roadWork: function (id) {
        linearCentering('roadWork', id);
      },

      unverifiedLinearAssetWorkList: function(layerName) {
        var typeId = _.find(models.linearAssets, function(assetSpec) { return assetSpec.layerName == layerName; }).typeId;
        eventbus.trigger('verificationList:select', layerName, backend.getUnverifiedLinearAssets(typeId));
      },

      privateRoadsWorkList: function(municipalityCode) {
        eventbus.trigger('privateRoadsWorkList:select', backend.getPrivateRoadAssociationsByMunicipality(municipalityCode));
      }
    });

    var router = new Router();

    // We need to restart the router history so that tests can reset
    // the application before each test.
    Backbone.history.stop();
    Backbone.history.start();

    eventbus.on('asset:closed', function () {
      router.navigate('massTransitStopNationalId');
    });

    eventbus.on('asset:fetched asset:created', function (asset) {
      router.navigate('massTransitStopNationalId/' + asset.nationalId);
    });

    // Focus to mass transit stop asset after national id search
    eventbus.on('nationalId:selected', function (result) {
      router.navigate('massTransitStopNationalId/' + result.nationalId, {trigger: true});
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

    eventbus.on('roles:fetched', function(userInfo) {
      if(_.includes(userInfo.roles, "serviceRoadMaintainer") && !_.includes(userInfo.roles, "elyMaintainer"))
          applicationModel.selectLayer('maintenanceRoad');
    });

  };
})(this);