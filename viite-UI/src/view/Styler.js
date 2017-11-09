//TODO: adjust to OTH needs - this is from Viite

(function(root) {
  root.Styler = function() {

    var roadNormalType = 0;
    var borderWidth = 3;
    var dashedLinesRoadClasses = [7, 8, 9, 10];

    var LINKSOURCE_NORMAL = 1;
    var LINKSOURCE_COMPLEM = 2;
    var LINKSOURCE_SURAVAGE = 3;
    var LINKSOURCE_FROZEN = 4;
    var LINKSOURCE_HISTORIC = 5;

    var LINKTYPE_NORMAL = 0;
    var LINKTYPE_COMPLEM = 1;
    var LINKTYPE_UNKNOWN = 3;
    var LINKTYPE_FLOATING = -1;

    var PROJECTLINKSTATUS_NOTHANDLED = 0;
    var PROJECTLINKSTATUS_TERMINATED = 1;

    /**
     * Inspired on the LinkPropertyLayerStyles roadClassRules, unknownRoadAddressAnomalyRules and constructionTypeRules.
     * @param roadClass The roadLink roadClass.
     * @param anomaly The roadLink anomaly value (if 1 then this is an anomalous roadlink).
     * @param constructionType The roadLink constructionType.
     * @param roadLinkType Describes what is the type of the roadLink.
     * @param gapTransfering Indicates if said link is in a gapTransfering process.
     * @param roadLinkSource Indicates what is the source of said road.
     * @returns {string} The default solid color of a line in the RGBA format.
     */
    var opacityMultiplier=1;

    var generateStrokeColor = function (roadClass, anomaly, constructionType, roadLinkType, gapTransfering, roadLinkSource) {
      if(roadLinkSource === LINKSOURCE_SURAVAGE) {
        return 'rgba(211, 175, 246,'+ 0.65 * opacityMultiplier+')';
      } else if (anomaly !== 1) {
        if(roadLinkType === -1){
          if(constructionType === 1) {
            return 'rgba(164, 164, 162,'+ 0.65 * opacityMultiplier+')';
          } else {
            return 'rgba(247, 254, 46,'+ 0.45 *opacityMultiplier+')';
          }
        } else  {
          switch (roadClass) {
            case 1 : return 'rgba(255, 0, 0,' + 0.65 * opacityMultiplier+')';
            case 2 : return 'rgba(255, 102, 0,' + 0.65 * opacityMultiplier+')';
            case 3 : return 'rgba(255, 153, 51,' + 0.65 * opacityMultiplier+')';
            case 4 : return 'rgba(0, 17, 187,' + 0.65 * opacityMultiplier+')';
            case 5 : return 'rgba(51, 204, 204,'+ 0.65 * opacityMultiplier+')';
            case 6 : return 'rgba(224, 29, 217,'+ 0.65 * opacityMultiplier+')';
            case 7 : return 'rgba(0, 204, 221,' + 0.65 * opacityMultiplier+')';
            case 8 : return 'rgba(252, 109, 160,'+ 0.65 * opacityMultiplier+')';
            case 9 : return 'rgba(255, 85, 221,'+ 0.65 * opacityMultiplier+')';
            case 10 : return 'rgba(255, 85, 221,'+ 0.65 * opacityMultiplier+')';
            case 11 : return 'rgba(68, 68, 68,' +0.75 * opacityMultiplier+')';
            case 97 : return 'rgba(30, 30, 30,'+   opacityMultiplier +')';
            case 98 : return 'rgba(250, 250, 250,' +  opacityMultiplier+')';
            case 99 : return 'rgba(164, 164, 162,'+ 0.65 * opacityMultiplier+')';
          }
        }
      } else {
        if(constructionType === 1) {
          return 'rgba(255, 153, 0,'+ 0.95 * opacityMultiplier+')';
        } else if (gapTransfering === true ) {
          return 'rgb(0, 255, 0,'+ 0.75 * opacityMultiplier+')';
        } else {
          return 'rgba(56, 56, 54,'+ opacityMultiplier+')';
        }
      }
    };

    /**
     * Inspired in the LinkPropertyLayerStyles complementaryRoadAddressRules and unknownRoadAddressAnomalyRules,
     * @param roadLinkType The roadLink roadLinkType.
     * @param anomaly The roadLink anomaly value (if 1 then this is an anomalous roadlink).
     * @param roadLinkSource The link source for this road link
     * @param projectLinkStatus Optional project link status (only in project mode, undef otherwise)
     * @returns {number} The zIndex for the feature.
     */
    var determineZIndex = function (roadLinkType, anomaly, roadLinkSource, projectLinkStatus){
      var zIndex = 0;
      if(roadLinkSource === LINKSOURCE_SURAVAGE) {
        zIndex = 9;
      } else if(roadLinkSource === LINKSOURCE_COMPLEM){
        zIndex = 8;
      } else if (anomaly === 0) {
        if (roadLinkType === LINKTYPE_UNKNOWN)
          zIndex = 4;
        else if(roadLinkType === LINKTYPE_FLOATING) {
          zIndex = 5;
        } else {
          zIndex = 6;
        }
      } else {
        zIndex = 6;
      }
      return zIndex;
    };
    /**
     * Will indicate what stroke dimension will be used based on the zoom level provided.
     * @param zoomLevel The actual zoom level.
     * @returns {number} The stroke width of a line.
     */
    var strokeWidthByZoomLevel = function (zoomLevel, roadLinkType, anomaly, roadLinkSource, notSelection, constructionType){
      var width = 0;

      switch (zoomLevel) {
        case 5 : {
          width = 1;
          break;
        }
        case 6 : {
          width = 1;
          break;
        }
        case 7 : {
          width = 2;
          break;
        }
        case 8 : {
          width = 2;
          break;
        }
        case 9 : {
          width = 2;
          break;
        }
        case 10: {
          width = 3;
          break;
        }
        case 11: {
          width = 3;
          break;
        }
        case 12: {
          width = 5;
          break;
        }
        case 13: {
          width = 8;
          break;
        }
        case 14: {
          width = 12;
          break;
        }
        case 15: {
          width = 12;
          break;
        }
      }

      if (roadLinkType === -1){
        width = width + 13;
      }

      if (roadLinkType !== -1 && anomaly === 1 && constructionType !== 1){
        width = 7;
      }
      if(roadLinkSource === 2 && !notSelection){
        width = width + 4;
      }

      return width;
    };

    /**
     * Method that changes color properties via a multiplicative factor.
     * @param lineColor The RGBA string of the color.
     * @param mult The multiplicative parameter. To darken use values between 0 and 1 to brighten use values > 1
     * @param changeOpacity If we want to change the opacity.
     * @param changeColor If we want to change the color.
     * @returns {string} The changed color.
     */
    var modifyColorProperties = function(lineColor, mult, changeColor, changeOpacity){
      var rgba = lineColor.slice(5, lineColor.length - 1).split(", ");
      var red = parseInt(rgba[0]) * (changeColor ? mult : 1);
      var green = parseInt(rgba[1]) * (changeColor ? mult : 1);
      var blue = parseInt(rgba[2]) * (changeColor ? mult : 1);
      var opacityParced =parseFloat(rgba[3]);
      if (!isNaN(opacityParced))
      {
        var opacity = opacityParced * (changeOpacity ? mult : 1);
        return 'rgba(' + Math.round(red) + ', ' + Math.round(green) + ', ' + Math.round(blue) + ', ' + opacity * opacityMultiplier+ ')';
      }
      else
        return 'rgba(' + Math.round(red) + ', ' + Math.round(green) + ', ' + Math.round(blue) + ', ' + 1+ ')';
    };


    /**
     * Method evoked by feature that will determine what kind of style said feature will have.
     * @param roadLinkData The roadLink details of a feature
     * @param currentZoom The value of the current application zoom.
     * @returns {*[ol.style.Style, ol.style.Style, ol.style.Style]} And array of ol.style.Style, the first is for the gray line, the second is for the border and the third is for the line itself.
     */
    var generateStyleByFeature = function(roadLinkData, currentZoom, notSelection){
      var strokeWidth = strokeWidthByZoomLevel(currentZoom, roadLinkData.roadLinkType, roadLinkData.anomaly, roadLinkData.roadLinkSource, notSelection, roadLinkData.constructionType);
      //Gray line behind all of the styles present in the layer.
      var underLineColor = generateStrokeColor(99, roadLinkData.anomaly, roadLinkData.constructionType, roadLinkData.roadLinkType, roadLinkData.gapTransfering, roadLinkData.roadLinkSource);
      //If the line we need to generate is a dashed line, middleLineColor will be the white one sitting behind the dashed/colored line and above the border and grey lines
      var middleLineColor;
      var adminClassColor;
      var borderColor;
      var lineCap;
      var borderCap;
      var middleLineCap;
      var adminClassWidth;
      var lineColor = generateStrokeColor(roadLinkData.roadClass, roadLinkData.anomaly, roadLinkData.constructionType, roadLinkData.roadLinkType, roadLinkData.gapTransfering, roadLinkData.roadLinkSource);
      if(roadLinkData.roadClass >= 7 && roadLinkData.roadClass <= 10 ){
        borderColor = lineColor;
        middleLineColor = generateStrokeColor(98,  roadLinkData.anomaly, roadLinkData.constructionType, roadLinkData.roadLinkType, roadLinkData.gapTransfering, roadLinkData.roadLinkSource);
        lineCap  = 'butt';
        middleLineCap = 'butt';
        borderCap = 'round';
      } else if (roadLinkData.roadClass == 99 && roadLinkData.constructionType == 1) {
        borderColor = lineColor;
        middleLineColor = generateStrokeColor(97, roadNormalType, roadNormalType, roadLinkData.roadLinkType, roadLinkData.gapTransfering, roadLinkData.roadLinkSource);
        lineCap = 'butt';
        middleLineCap = 'butt';
        borderCap = 'round';
      } else {
        borderColor = modifyColorProperties(lineColor, 1.45, true, false);
        borderColor = modifyColorProperties(borderColor,0.75, false, true);
        lineCap  = 'round';
        borderCap = 'round';
        middleLineColor = lineColor;
      }
      var lineBorder = new ol.style.Stroke({
        width: strokeWidth + borderWidth,
        color: borderColor,
        lineCap: borderCap
      });
      var middleLineWidth = strokeWidth;
      if(roadLinkData.id !== 0 && roadLinkData.administrativeClass == "Municipality"){
        adminClassColor = generateStrokeColor(97, roadNormalType, roadNormalType, roadLinkData.roadLinkType, roadLinkData.gapTransfering, roadLinkData.roadLinkSource);
        adminClassWidth = middleLineWidth+7;
      }
      var middleLine = new ol.style.Stroke({
        width: middleLineWidth,
        color: middleLineColor,
        lineCap: middleLineCap
      });
      var line = new ol.style.Stroke({
        width: strokeWidth,
        color: lineColor,
        lineCap: lineCap
      });
      var underline = new ol.style.Stroke({
        width: strokeWidth,
        color: underLineColor,
        lineCap: lineCap
      });

      var adminClassLine = new ol.style.Stroke({
        width: adminClassWidth,
        color: adminClassColor,
        lineCap: lineCap
      });

      //Dash lines
      if(_.contains(dashedLinesRoadClasses, roadLinkData.roadClass)){
        line.setLineDash([10, 10]);
      }

      if(roadLinkData.roadClass == 99 && roadLinkData.constructionType == 1){
        line.setLineDash([10, 10]);
      }

      //Declaration of the Line Styles
      var borderStyle = new ol.style.Style({
        stroke: lineBorder
      });
      var middleLineStyle = new ol.style.Style({
        stroke: middleLine
      });
      var lineStyle = new ol.style.Style({
        stroke: line
      });
      var underlineStyle = new ol.style.Style({
        stroke: underline
      });
      var adminClassStyle = new ol.style.Style({
        stroke: adminClassLine
      });
      var zIndex = determineZIndex(roadLinkData.roadLinkType, roadLinkData.anomaly, roadLinkData.roadLinkSource);
      underlineStyle.setZIndex(zIndex-1);
      borderStyle.setZIndex(zIndex);
      middleLineStyle.setZIndex(zIndex+1);
      lineStyle.setZIndex(zIndex+2);
      adminClassStyle.setZIndex(zIndex-2);
      return [borderStyle , underlineStyle, middleLineStyle, lineStyle, adminClassStyle];
    };

    var SetOpacityMultiplier = function(multiplier){
      opacityMultiplier=multiplier;

    };

    return {
      generateStyleByFeature: generateStyleByFeature,
      strokeWidthByZoomLevel: strokeWidthByZoomLevel,
      determineZIndex: determineZIndex,
      opacityMultiplier: SetOpacityMultiplier
    };
  };
})(this);
