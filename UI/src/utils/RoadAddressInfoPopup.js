(function(root) {
    root.RoadAddressInfoData = {};

    root.RoadAddressInfoDataInitializer = {
        initialize : function(isExperimental){
            eventbus.on('roles:fetched', function(roles) {
                RoadAddressInfoData = {
                    roles: roles,
                    isExperimental: isExperimental
                };
            });
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
                return RoadAddressInfoData.roles &&
                    (_.contains(RoadAddressInfoData.roles, 'operator') || _.contains(RoadAddressInfoData.roles, 'busStopMaintainer'));
        };

        var overlay = new ol.Overlay(({
            element: infoContainer
        }));

        var hasRoadAddressInfo = function(roadData){
            if(_.isUndefined(roadData.roadNumber))
                return false;

            return roadData.roadNumber!==0 &&roadData.roadPartNumber!==0&&roadData.roadPartNumber!==99;
        };

        var getPointAssetData = function(roadData){
          var data = _.clone(collection.getRoadLinkByLinkId(_.find([roadData.data, roadData], function(rd){return !_.isUndefined(rd);}).linkId));
          if(!_.isUndefined(data)){
              return data.getData();
          }
        };

        var isStateRoad = function(roadData){
          return (roadData.administrativeClass==1 || roadData.administrativeClass=='State');
        };

        map.addOverlay(overlay);

        var displayRoadAddressInfoPopup = function(event) {

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
                'Tienumero: ' + roadData.roadNumber + '<br>' +
                'Tieosanumero: ' + roadData.roadPartNumber + '<br>' +
                'Ajorata: ' + roadData.track + '<br>' +
                'AET: ' + roadData.startAddrMValue + '<br>' +
                'LET: ' + roadData.endAddrMValue + '<br>' +'</p>';
              overlay.setPosition(map.getEventCoordinate(event.originalEvent));
            };

            var generateUnknownInfoContent = function(){
              infoContent.innerHTML = '<p>' +
                'Tuntematon tien segmentti' +'</p>'; //road with no address
              overlay.setPosition(map.getEventCoordinate(event.originalEvent));
            };

            if(feature.getGeometry().getType() === "Point" || _.isUndefined(roadData.administrativeClass)){
              var pointAssetData = getPointAssetData(roadData);
              if(!_.isUndefined(pointAssetData)){
                if(hasRoadAddressInfo(pointAssetData) && isStateRoad(pointAssetData)){
                  generateInfoContent(pointAssetData);
                }else if(isStateRoad(pointAssetData)){
                  generateUnknownInfoContent();
                }
              }
            }else{
              if(hasRoadAddressInfo(roadData) && isStateRoad(roadData)){
                generateInfoContent(roadData);
              }else if(isStateRoad(roadData)){
                generateUnknownInfoContent();
              }
            }
        };

        return {
            start: start,
            stop: stop
        };
    };

})(this);