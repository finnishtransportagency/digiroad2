(function(root) {
    root.RoadAddressInfoData = {};

    root.RoadAddressInfoDataInitializer = {
        initialize : function(isExperimental){
                RoadAddressInfoData = {
                    authorizationPolicy: new InfoPopupAuthorizationPolicy(),
                    isExperimental: isExperimental
                };
        }
    };

    root.RoadAddressInfoPopup = function(map, container, collection){

        var element =
          $('<div class="info-check-box-container">' +
         '<div class="checkbox-wrapper">'+
         '<div class="checkbox">' +
         '<label><input type="checkbox"/>Tieosoiteinfo</label>' +
         '</div>' +
         '</div>' +
         '</div>');

        container.append(element);

        var infoContainer = document.getElementById('popup');
        var infoContent = document.getElementById('popup-content');

        var isShowPopup = false;

        var setShowPopup = function(showPopup){
          isShowPopup = showPopup;
        };

        element.find('.checkbox').find('input[type=checkbox]').on('change', function (event) {
          if ($(event.currentTarget).prop('checked')) {
            setShowPopup(true);
            eventbus.trigger('toggleWithRoadAddress', 'true');
          } else {
            if (applicationModel.isDirty()) {
              $(event.currentTarget).prop('checked', true);
              new Confirm();
            } else {
              setShowPopup(false);
              eventbus.trigger('toggleWithRoadAddress', 'false');
            }
          }
        });

        var run = function (event) {
          if(isShowPopup)
            if (canDisplayRoadAddressInfo())
                displayRoadAddressInfoPopup(event);
        };

        var start = function(){
            eventbus.on('map:mouseMoved', run);
        };

        var stop = function(){
            eventbus.off('map:mouseMoved', run);
        };

        var canDisplayRoadAddressInfo = function(){
          return RoadAddressInfoData.authorizationPolicy.editModeAccess();
        };

        var overlay = new ol.Overlay(({
            element: infoContainer
        }));

        var getPointAssetData = function(roadData){
          var data = _.clone(collection.getRoadLinkByLinkId(_.find([roadData.data, roadData], function(rd){return !_.isUndefined(rd);}).linkId));
          if(!_.isUndefined(data)){
              return data.getData();
          }
        };

        map.addOverlay(overlay);

        var allowedLaneInfoLayers = ["laneModellingTool", "trafficSigns"];

        var displayRoadAddressInfoPopup = function(event) {
          var getLaneInfo = function () {
            if(_.includes(allowedLaneInfoLayers, applicationModel.getSelectedLayer()))
              return 'Kaistat: ' + _.join(_.uniq(roadData.lanes), ', ') + '<br>';
            return '';
          };

            if (event.dragging)
                return;

            var feature = map.forEachFeatureAtPixel(event.pixel, function (feature, vectorLayer) {
                return feature;
            });

            if(_.isUndefined(feature)) {
                overlay.setPosition(undefined);
                return;
            }

            var roadData = feature.getProperties();

            var generateInfoContent = function(roadData){
              infoContent.innerHTML = '<p>' +
                'Tienumero: ' + (roadData.roadNumber !== undefined ? roadData.roadNumber : '') + '<br>' +
                'Tieosanumero: ' + (roadData.roadPartNumber !== undefined ? roadData.roadPartNumber : '') + '<br>' +
                'Ajorata: ' + (roadData.track !== undefined ? roadData.track : '') + '<br>' +
                'AET: ' + (roadData.startAddrMValue !== undefined ? roadData.startAddrMValue : '') + '<br>' +
                'LET: ' + (roadData.endAddrMValue !== undefined ? roadData.endAddrMValue : '') + '<br>' +
                getLaneInfo() + '</p>';
              overlay.setPosition(map.getEventCoordinate(event.originalEvent));
            };

          if(feature.getGeometry().getType() === "Point"){
            var pointAssetData = getPointAssetData(roadData);
            if(!_.isUndefined(pointAssetData))
              generateInfoContent(pointAssetData);

          }else{
            generateInfoContent(roadData);
          }
        };

        return {
            start: start,
            stop: stop
        };
    };

})(this);