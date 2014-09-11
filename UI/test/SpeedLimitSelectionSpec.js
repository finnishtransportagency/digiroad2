/*jshint expr: true*/
define(['chai', 'TestHelpers'], function(chai, testHelpers) {
  var expect = chai.expect;
  var speedLimitsData = SpeedLimitsTestData.generate(1);
  var speedLimit = _.first(speedLimitsData);

  var speedLimitConstructor = function(id) {
    var points = speedLimitsData[0].points;
    return {
      id: id,
      endpoints: [_.first(points), _.last(points)],
      modifiedBy: 'modifier',
      createdBy: 'creator'
    };
  };

  var selectSpeedLimit = function(map, speedLimitId) {
    var control = _.first(map.getControlsBy('displayClass', 'olControlSelectFeature'));
    var feature = _.find(testHelpers.getSpeedLimitFeatures(map), function(feature) {
      return feature.attributes.id === speedLimitId;
    });
    control.select(feature);
  };

  var clickElement = function(element) {
    var event = document.createEvent('MouseEvent');
    event.initMouseEvent('click', true, true, window, null, 0, 0, 0, 0, false, false, false, false, 0, null);
    element.dispatchEvent(event);
  };

  var endPointFeatures = function(features) {
    return _.filter(features, function(x) { return x.attributes.type === 'endpoint'; });
  };

  describe('when loading application with speed limit data', function() {
    var openLayersMap;
    before(function(done) {
      testHelpers.restartApplication(function(map) {
        openLayersMap = map;
        $('.speed-limits').click();
        done();
      }, testHelpers.defaultBackend().withSpeedLimitsData(speedLimitsData).withSpeedLimitConstructor(speedLimitConstructor));
    });

    describe('and selecting speed limit', function() {
      before(function() {
        selectSpeedLimit(openLayersMap, speedLimit.id);
      });
      it('it displays speed limit segment ID in asset form', function() {
        expect($('#feature-attributes header')).to.have.text('Segmentin ID: 1123812');
      });
      it('it displays speed limit creator and modifier', function() {
        expect($('#feature-attributes .asset-log-info:first')).to.have.text('Lisätty järjestelmään: creator');
        expect($('#feature-attributes .asset-log-info:last')).to.have.text('Muokattu viimeksi: modifier');
      });
      it('shows speed limit end point markers at both ends of one link speed limit segment', function() {
        var endPoints = endPointFeatures(testHelpers.getSpeedLimitFeatures(openLayersMap));
        var endPointCoordinates = _.map(_.pluck(endPoints, 'geometry'), function(geometry) { return {x: geometry.x, y: geometry.y}; });
        expect(endPointCoordinates).to.have.length(2);
        expect(endPointCoordinates[0]).to.deep.equal(speedLimit.points[0]);
        expect(endPointCoordinates[1]).to.deep.equal(speedLimit.points[1]);
      });

      describe('and zooming in', function() {
        before(function() {
          $('.pzbDiv-plus').click();
        });
        it('maintains speed limit selection', function() {
          expect($('#feature-attributes header')).to.have.text('Segmentin ID: 1123812');
        });
      });

      describe('and clicking on the background map', function() {
        before(function() {
          var layer = $('.olLayerDiv').filter(function(i, e) { return _.contains($(e).attr('id'), 'OpenLayers_Layer_WMTS'); });
          clickElement(_.first(layer));
        });
        it('deselects speed limit', function() {
          expect($('#feature-attributes header')).not.to.exist;
        });
      });
    });

    describe('and selecting speed limit', function() {
      before(function() {
        selectSpeedLimit(openLayersMap, speedLimit.id);
      });
      describe('and selecting assets layer', function() {
        before(function() {
          $('.panel-header').filter(':visible').filter(function (i, element) {return _.contains($(element).text(), 'Joukkoliikenteen pysäkit'); }).click();
        });
        describe('and reselecting speed limits layer', function() {
          before(function() {
            $('.speed-limits').click();
          });
          it('deselects speed limit', function() {
            expect($('#feature-attributes header')).not.to.exist;
          });
        });
      });
    });
  });

  describe('when loading application with speed limits', function() {
    var openLayersMap;
    var speedLimitId = 13;
    var speedLimits = _.filter(SpeedLimitsTestData.generate(), function(link) { return link.id === speedLimitId; });
    var speedLimitConstructor = function(id) {
      return {
        id: id,
        endpoints: [speedLimits[0].points[0], _.last(speedLimits[1].points)]
      };
    };

    before(function(done) {
      testHelpers.restartApplication(function(map) {
        openLayersMap = map;
        $('.speed-limits').click();
        done();
      }, testHelpers.defaultBackend().withSpeedLimitsData(speedLimits).withSpeedLimitConstructor(speedLimitConstructor));
    });

    describe('and selecting speed limit that spans over multiple links', function() {
      before(function() {
        selectSpeedLimit(openLayersMap, speedLimitId);
      });
      it('shows speed limit end point markers at both ends of speed limit segment that spans over multiple links', function() {
        var endPoints = endPointFeatures(testHelpers.getSpeedLimitFeatures(openLayersMap));
        var endPointCoordinates = _.map(_.pluck(endPoints, 'geometry'), function(geometry) {
          return {x: geometry.x, y: geometry.y};
        });
        expect(endPointCoordinates).to.have.length(2);
        expect(endPointCoordinates[0]).to.deep.equal(speedLimits[0].points[0]);
        expect(endPointCoordinates[1]).to.deep.equal(_.last(speedLimits[1].points));
      });
    });
  });

  describe('when loading application in edit mode with speed limits', function() {
    var openLayersMap;
    var speedLimitId = 13;
    var speedLimits = _.filter(SpeedLimitsTestData.generate(), function(link) { return link.id === speedLimitId; });

    before(function(done) {
      testHelpers.restartApplication(function(map) {
        openLayersMap = map;
        $('.speed-limits').click();
        applicationModel.setReadOnly(false);
        done();
      }, testHelpers.defaultBackend().withSpeedLimitsData(speedLimits).withSpeedLimitConstructor(SpeedLimitsTestData.generateSpeedLimitConstructor(speedLimits)));
    });

    describe('and changing value of speed limit', function() {
      before(function() {
        selectSpeedLimit(openLayersMap, speedLimitId);
        $('#feature-attributes .form-control.speed-limit').val('100').change();
      });
      it('should update all speed limit links on map', function() {
        var features = _.filter(testHelpers.getSpeedLimitFeatures(openLayersMap), function(feature) {
          return feature.attributes.id === speedLimitId;
        });
        expect(features.length).not.to.equal(0);
        _.each(features, function(feature) {
          expect(feature.attributes.limit).to.equal(100);
        });
      });

      describe('and cancelling the change', function() {
        before(function() {
          $('#feature-attributes button.cancel').click();
        });
        it('resets the update but maintains selection state', function() {
          var features = _.filter(testHelpers.getSpeedLimitFeatures(openLayersMap), function(feature) {
            return feature.attributes.id === speedLimitId;
          });
          expect(features.length).not.to.equal(0);
          _.each(features, function(feature) {
            expect(feature.attributes.limit).to.equal(60);
            expect(feature.attributes.isSelected).to.be.true;
          });
        });
      });
    });
  });

  describe('when loading application in edit mode with speed limits', function() {
    var openLayersMap;
    var speedLimitId = 13;
    var speedLimits = _.filter(SpeedLimitsTestData.generate(), function(link) {
      return link.id === speedLimitId;
    });
    var speedLimitConstructor = SpeedLimitsTestData.generateSpeedLimitConstructor(speedLimits);

    before(function(done) {
      testHelpers.restartApplication(function(map) {
        openLayersMap = map;
        $('.speed-limits').click();
        applicationModel.setReadOnly(false);
        done();
      }, testHelpers.defaultBackend()
        .withSpeedLimitsData(speedLimits)
        .withSpeedLimitConstructor(speedLimitConstructor)
        .withSpeedLimitUpdate(_.merge(speedLimitConstructor(13), {modifiedBy: 'modifier', modifiedDateTime: '10.09.2014 13:36:58', createdBy: 'creator', createdDateTime: '10.09.2014 13:36:57'})));
    });

    describe('and changing value of speed limit', function() {
      before(function() {
        selectSpeedLimit(openLayersMap, speedLimitId);
        $('#feature-attributes .form-control.speed-limit').val('100').change();
      });

      describe('and saving the change', function() {
        before(function() {
          $('#feature-attributes button.save').click();
        });
        it('it updates the modified and created fields', function() {
          expect($('#feature-attributes .asset-log-info:first')).to.have.text('Lisätty järjestelmään: creator 10.09.2014 13:36:57');
          expect($('#feature-attributes .asset-log-info:last')).to.have.text('Muokattu viimeksi: modifier 10.09.2014 13:36:58');
        });
      });
    });
  });
});
