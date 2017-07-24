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

    root.RoadAddressInfoPopup = function(map){

        var infoContainer = document.getElementById('popup');
        var infoContent = document.getElementById('popup-content');

        var run = function (event) {
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
                (_.contains(RoadAddressInfoData.roles, 'operator') || _.contains(RoadAddressInfoData.roles, 'busStopMaintainer'))
        };

        var overlay = new ol.Overlay(({
            element: infoContainer
        }));

        var hasRoadAddressInfo = function(roadData){
            if(_.isUndefined(roadData.roadNumber))
                return false;

            return roadData.roadNumber!==0 &&roadData.roadPartNumber!==0&&roadData.roadPartNumber!==99;
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

            if (hasRoadAddressInfo(roadData)){
                infoContent.innerHTML = '<p>' +
                    'Tienumero: ' + roadData.roadNumber + '<br>' +
                    'Tieosanumero: ' + roadData.roadPartNumber + '<br>' +
                    'Ajorata: ' + roadData.track + '<br>' +
                    'AET: ' + roadData.startAddrMValue + '<br>' +
                    'LET: ' + roadData.endAddrMValue + '<br>' +'</p>';

            } else {
                infoContent.innerHTML = '<p>' +
                    'Tuntematon tien segmentti' +'</p>'; // road with no address
            }

            overlay.setPosition(map.getEventCoordinate(event.originalEvent));
        };

        return {
            start: start,
            stop: stop
        };
    }

})(this);