(function(root) {

  var generate = function() {
    return [{
      "statusCode": 1,
      "name": "test1",
      "statusDescription": "Keskeneräinen",
      "dateModified": "05.05.2017",
      "id": 455035,
      "createdBy": "silari",
      "additionalInfo": "Desc",
      "startDate": "05.05.2017",
      "modifiedBy": "-",
      "createdDate": "2017-05-05T17:23:52.000+03:00"
    }];
  };

  var generateNormalLinkData = function(){
    return [
      [
        {"modifiedAt":"29.10.2015 15:34:02","linkId":5172099,"startAddressM":3555,"roadNameFi":"Raimantie","roadPartNumber":1,"administrativeClass":"State","segmentId":190723,"municipalityCode":749,"roadLinkType":1,"constructionType":0,"roadNumber":16333,"trackCode":0,"roadClass":5,"sideCode":3,"points":[{"x":533002.493,"y":6989057.755,"z":111.33999999999651},{"x":533003.047,"y":6989059.19,"z":111.30999999999767},{"x":533009.396,"y":6989075.615,"z":110.94800000000396},{"x":533017.076,"y":6989094.236,"z":110.43499999999767},{"x":533028.861,"y":6989120.633,"z":109.42999999999302},{"x":533036.808,"y":6989144.175,"z":108.49800000000687},{"x":533041.456,"y":6989165.199,"z":107.57000000000698},{"x":533043.6549813771,"y":6989195.388744326,"z":106.4870091717406}],"id":190723,"roadType":"Yleinen tie","status":99,"anomaly":0,"startMValue":0.0,"endAddressM":3699,"endMValue":144.847,"linkType":99,"calibrationPoints":[],"mmlId":318833294,"modifiedBy":"vvh_modified","elyCode":8,"discontinuity":5,"roadLinkSource":1}
      ]
    ];
  };

  var generateReservedProjectLinkData = function(){
    return [
      [
        {"modifiedAt":"29.10.2015 15:34:02","linkId":5172134,"startAddressM":677,"roadNameFi":"VT 5","roadPartNumber":205,"administrativeClass":"State","segmentId":454370,"municipalityCode":749,"roadLinkType":1,"constructionType":0,"roadNumber":5,"trackCode":2,"roadClass":1,"sideCode":2,"points":[{"x":533347.708,"y":6988359.985,"z":111.10899999999674},{"x":533341.472,"y":6988382.846,"z":111.92299999999523}],"id":454370,"roadType":"Yleinen tie","status":0,"anomaly":0,"startMValue":0.0,"endAddressM":700,"endMValue":23.696265886857788,"linkType":99,"calibrationPoints":[],"mmlId":318834488,"modifiedBy":"vvh_modified","elyCode":8,"discontinuity":5,"roadLinkSource":1}
      ]
    ];
  };

  var generateTerminatedProjectLinkData = function(){
    return [
      [
        {"modifiedAt":"12.02.2016 10:55:04","linkId":5172091,"startAddressM":734,"roadNameFi":"VT 5","roadPartNumber":205,"administrativeClass":"State","segmentId":454340,"municipalityCode":749,"roadLinkType":1,"constructionType":0,"roadNumber":5,"trackCode":1,"roadClass":1,"sideCode":2,"points":[{"x":533350.231,"y":6988423.725,"z":112.24599999999919},{"x":533341.188,"y":6988460.23,"z":112.53699999999662},{"x":533327.561,"y":6988514.938,"z":113.028999999995},{"x":533316.53,"y":6988561.507,"z":113.4149999999936},{"x":533299.757,"y":6988642.994,"z":113.67600000000675},{"x":533284.518,"y":6988725.655,"z":113.58000000000175},{"x":533270.822,"y":6988809.254,"z":113.16300000000047},{"x":533259.436,"y":6988892.555,"z":112.38000000000466},{"x":533249.153,"y":6988975.982,"z":111.625},{"x":533245.308,"y":6989012.096,"z":111.29899999999907},{"x":533244.289,"y":6989018.034,"z":111.36000000000058}],"id":454340,"roadType":"Yleinen tie","status":1,"anomaly":0,"startMValue":0.0,"endAddressM":1340,"endMValue":604.2852198046263,"linkType":99,"calibrationPoints":[],"mmlId":318834500,"modifiedBy":"vvh_modified","elyCode":8,"discontinuity":5,"roadLinkSource":1}
      ]
    ];
  };

  var generateRoadPartChecker = function(){
    return {"success":"ok","roadparts":[{"roadPartNumber":205,"roadNumber":5,"ely":8,"length":6730.0,"roadPartId":195379,"discontinuity":"Jatkuva"},{"roadPartNumber":206,"roadNumber":5,"ely":8,"length":4750.0,"roadPartId":195879,"discontinuity":"Jatkuva"}]
    };
  };

  root.RoadAddressProjectTestData = {
    generate: generate,
    generateNormalLinkData: generateNormalLinkData,
    generateReservedProjectLinkData: generateReservedProjectLinkData,
    generateTerminatedProjectLinkData: generateTerminatedProjectLinkData,
    generateRoadPartChecker: generateRoadPartChecker
  };
}(this));
