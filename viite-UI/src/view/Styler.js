/**
 * Created by pedrosag on 19-01-2017.
 */
(function(root) {
  root.Styler = function() {

    var generateStrokeColor = function (roadClass, anomaly, constructionType) {
      if (anomaly !== 1) {
        if(constructionType === 1) {
          return 'rgba(164, 164, 162, 1)';
        }
        else {
          switch (roadClass) {
            case 1 : return 'rgba(255, 0, 0, 1)';
            case 2 : return 'rgba(255, 102, 0, 1)';
            case 3 : return 'rgba(255, 153, 51, 1)';
            case 4 : return 'rgba(0, 17, 187, 1)';
            case 5 : return 'rgba(51, 204, 204, 1)';
            case 6 : return 'rgba(224, 29, 217, 1)';
            case 7 : return 'rgba(0, 204, 221, 1)';
            case 8 : return 'rgba(136, 136, 136, 1)';
            case 9 : return 'rgba(255, 85, 221, 1)';
            case 10 : return 'rgba(255, 85, 221, 1)';
            case 11 : return 'rgba(68, 68, 68, 1)';
            case 99 : return 'rgba(164, 164, 162, 1)';
          }
        }
      } else {
        if(constructionType === 1) {
          return 'rgba(255, 153, 0, 1)';
        } else {
          return 'rgba(0, 0, 0, 1)';
        }
      }
    };

    var determineZIndex = function (roadLinkType, anomaly){
      var zIndex = 0;
      if (anomaly === 0) {
        if (roadLinkType === 3)
          zIndex = 4;
        else if(roadLinkType === -1) {
          zIndex = 2;
        }
      } else {
        zIndex = 3;
      }

      return zIndex;
    };

    var strokeWidthByZoomLevel = function (zoomLevel){
      switch (zoomLevel) {
        case 6 : return 1  ;
        case 7 : return 2  ;
        case 8 : return 3  ;
        case 9 : return 3  ;
        case 10: return 5  ;
        case 11: return 8  ;
        case 12: return 10 ;
        case 13: return 10 ;
        case 14: return 14 ;
        case 15: return 14 ;
      }
    };

    var generateStyleByFeature = function(roadLinkData, currentZoom){
      var stroke = new ol.style.Stroke({
        width: strokeWidthByZoomLevel(currentZoom),
        color: generateStrokeColor(roadLinkData.roadClass, roadLinkData.anomaly, roadLinkData.constructionType)
      });
      var style = new ol.style.Style({
        stroke: stroke
      });
      style.setZIndex(determineZIndex(roadLinkData.roadLinkType, roadLinkData.anomaly));
      return style;
    };

    return {
      generateStyleByFeature: generateStyleByFeature
    };
  };
})(this);