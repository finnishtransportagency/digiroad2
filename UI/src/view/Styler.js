(function(root) {
  root.Styler = function() {

    var borderWidth = 7;
    var dashedLinesRoadClasses = [7, 8, 9, 10];
    //TODO thoses rule configurations on the LinkPropertyLayerStyle
    //TODO create support  for externalGraphic
    var functionalClassRules = [
      new StyleRule().where('functionalClass').is(1).use({ stroke : { color: '#f00'}, externalGraphic: 'images/link-properties/arrow-drop-red.svg' }),
      new StyleRule().where('functionalClass').is(2).use({ stroke : { color: '#f00'}, externalGraphic: 'images/link-properties/arrow-drop-red.svg' }),
      new StyleRule().where('functionalClass').is(3).use({ stroke : { color: '#f5d'}, externalGraphic: 'images/link-properties/arrow-drop-pink.svg' }),
      new StyleRule().where('functionalClass').is(4).use({ stroke : { color: '#f5d'}, externalGraphic: 'images/link-properties/arrow-drop-pink.svg' }),
      new StyleRule().where('functionalClass').is(5).use({ stroke : { color: '#01b'}, externalGraphic: 'images/link-properties/arrow-drop-blue.svg' }),
      new StyleRule().where('functionalClass').is(6).use({ stroke : { color: '#01b'}, externalGraphic: 'images/link-properties/arrow-drop-blue.svg' }),
      new StyleRule().where('functionalClass').is(7).use({ stroke : { color: '#888'}, externalGraphic: 'images/link-properties/arrow-drop-grey.svg' }),
      new StyleRule().where('functionalClass').is(8).use({ stroke : { color: '#888'}, externalGraphic: 'images/link-properties/arrow-drop-grey.svg' })
    ];

    var unknownFunctionalClassDefaultRules = [
      new StyleRule().where('functionalClass').is(99).use({ stroke : { color: '#888', opacity: 0.6}, externalGraphic: 'images/link-properties/arrow-drop-black.svg' })
    ];

    //TODO create support  for pointRadius
    var zoomLevelRules = [
      new StyleRule().where('zoomLevel').is(9).use({ stroke: {width: 3 }, pointRadius: 0 }),
      new StyleRule().where('zoomLevel').is(10).use({ stroke: {width: 5 }, pointRadius: 10 }),
      new StyleRule().where('zoomLevel').is(11).use({ stroke: {width: 8 }, pointRadius: 12 }),
      new StyleRule().where('zoomLevel').is(12).use({ stroke: {width: 10 }, pointRadius: 13 }),
      new StyleRule().where('zoomLevel').is(13).use({ stroke: {width: 10 }, pointRadius: 14 }),
      new StyleRule().where('zoomLevel').is(14).use({ stroke: {width: 14 }, pointRadius: 16 }),
      new StyleRule().where('zoomLevel').is(15).use({ stroke: {width: 14 }, pointRadius: 16 })
    ];

    var styleProvider = new StyleRuleProvider({stroke : { color: "#a4a4a2", opacity: 0.7 }});
    styleProvider.addRules(functionalClassRules);
    styleProvider.addRules(unknownFunctionalClassDefaultRules);
    styleProvider.addRules(zoomLevelRules);

    /**
     * Inspired on the LinkPropertyLayerStyles roadClassRules, unknownRoadAddressAnomalyRules and constructionTypeRules.
     * @param roadClass The roadLink roadClass.
     * @param anomaly The roadLink anomaly value (if 1 then this is an anomalous roadlink).
     * @param constructionType The roadLink constructionType.
     * @returns {string} The default solid color of a line in the RGBA format.
     */
    var generateStrokeColor = function (roadClass, anomaly, constructionType) {
      return 'rgba(255, 0, 0, 0.40)';
      //
      //if (anomaly !== 1) {
      //  if(constructionType === 1) {
      //    return 'rgba(164, 164, 162, 0.40)';
      //  }
      //  else {
      //    switch (roadClass) {
      //      case 1 : return 'rgba(255, 0, 0, 0.40)';
      //      case 2 : return 'rgba(255, 102, 0, 0.40)';
      //      case 3 : return 'rgba(255, 153, 51, 0.40)';
      //      case 4 : return 'rgba(0, 17, 187, 0.40)';
      //      case 5 : return 'rgba(51, 204, 204, 0.40)';
      //      case 6 : return 'rgba(224, 29, 217, 0.40)';
      //      case 7 : return 'rgba(0, 204, 221, 0.40)';
      //      case 8 : return 'rgba(136, 136, 136, 0.40)';
      //      case 9 : return 'rgba(255, 85, 221, 0.40)';
      //      case 10 : return 'rgba(255, 85, 221, 0.40)';
      //      case 11 : return 'rgba(68, 68, 68, 0.40)';
      //      case 99 : return 'rgba(164, 164, 162, 0.40)';
      //    }
      //  }
      //} else {
      //  if(constructionType === 1) {
      //    return 'rgba(255, 153, 0, 0.40)';
      //  } else {
      //    return 'rgba(56, 56, 54, 0.40)';
      //  }
      //}
    };

    /**
     * Inspired in the LinkPropertyLayerStyles complementaryRoadAddressRules and unknownRoadAddressAnomalyRules,
     * @param roadLinkType The roadLink roadLinkType.
     * @param anomaly The roadLink anomaly value (if 1 then this is an anomalous roadlink).
     * @returns {number} The zIndex for the feature.
     */
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
    /**
     * Will indicate what stroke dimension will be used based on the zoom level provided.
     * @param zoomLevel The actual zoom level.
     * @returns {number} The stroke width of a line.
     */
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

    /**
     * Method that changes color properties via a multiplicative factor.
     * @param lineColor The RGBA string of the color.
     * @param mult The multiplicative parameter. To darken use values between 0 and 1 to brighten use values > 1
     * @param changeOpacity If we want to change the opacity.
     * @param changeColor If we want to change the color.
     * @returns {string} The changed color.
     */
    var modifyColorProperties = function(lineColor, mult, changeColor, changeOpacity){
      var rgba = lineColor.slice(5, lineColor.length-1).split(", ");
      var red = parseInt(rgba[0]) * (changeColor ? mult : 1);
      var green = parseInt(rgba[1]) * (changeColor ? mult : 1);
      var blue = parseInt(rgba[2]) * (changeColor ? mult : 1);
      var opacity = parseFloat(rgba[3]) * (changeOpacity ? mult : 1);
      return 'rgba(' + Math.round(red) + ', ' + Math.round(green) + ', ' + Math.round(blue) + ', ' + opacity + ')';
    };

    /**
     * Method evoked by feature that will determine what kind of style said feature will have.
     * @param roadLinkData The roadLink details of a feature
     * @param currentZoom The value of the current application zoom.
     * @returns {*[ol.style.Style, ol.style.Style]} And array of ol.style.Style, the first is for the border the second is for the line itself.
     */
    var generateStyleByFeature = function(roadLinkData, currentZoom){
      console.log(roadLinkData.functionalClass);
      roadLinkData.zoomLevel = currentZoom;

      var returnedStyle = styleProvider.getStyle(roadLinkData);

      return [ returnedStyle ];
/*
      var strokeWidth = strokeWidthByZoomLevel(currentZoom);
      var lineColor = generateStrokeColor(roadLinkData.roadClass, roadLinkData.anomaly, roadLinkData.constructionType);
      var borderColor = modifyColorProperties(lineColor, 0.75, true, true);
      var lineCap  = 'round';

      var lineBorder = new ol.style.Stroke({
        width: strokeWidth + borderWidth,
        color: borderColor,
        lineCap: lineCap
      });
      var line = new ol.style.Stroke({
        width: strokeWidth,
        color: lineColor,
        lineCap: lineCap
      });

      if(_.contains(dashedLinesRoadClasses, roadLinkData.roadClass)){
        lineBorder.setLineDash([20, 60]);
        line.setLineDash([20, 60]);
      }

      //Declaration of the Line Styles
      var borderStyle = new ol.style.Style({
        stroke: lineBorder
      });
      var lineStyle = new ol.style.Style({
        stroke: line
      });
      var zIndex = determineZIndex(roadLinkData.roadLinkType, roadLinkData.anomaly);
      borderStyle.setZIndex(zIndex);
      lineStyle.setZIndex(zIndex+1);
      return [lineStyle, borderStyle];*/
    };

    return {
      generateStyleByFeature: generateStyleByFeature
    };
  };
})(this);