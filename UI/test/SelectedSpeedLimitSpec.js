/*jshint expr: true*/
define(['chai', 'lodash', 'jquery', 'TestHelpers', 'SelectedSpeedLimit', 'SpeedLimitsCollection', 'RoadCollection', 'Backend', 'EventBus'],
  function(chai, _, $, testHelpers, SelectedSpeedLimit, SpeedLimitsCollection, RoadCollection, EventBus) {
    describe('Selected speed limit', function() {
      var expect = chai.expect;

      var speedLimitTestData = null;
      var selectedSpeedLimit = null;

      before(function() {
        eventbus.stopListening();
        eventbus = {on: function() {}, trigger: function() {}};
        speedLimitTestData = SpeedLimitsTestData.generate();
        var backend = testHelpers.defaultBackend()
          .withSpeedLimitsData(speedLimitTestData);
        var roadCollection = new RoadCollection(backend);
        var speedLimitsCollection = new SpeedLimitsCollection(backend);
        roadCollection.fetch();
        speedLimitsCollection.fetch();

        selectedSpeedLimit = new SelectedSpeedLimit(backend, speedLimitsCollection);
      });

      describe('selected speed limit is separable', function() {
        it('returns true when selection is two-way and has two-way roadlink', function() {
          selectedSpeedLimit.open(speedLimitTestData[0][0], true);
          expect(selectedSpeedLimit.isSeparable()).to.be.true;
        });
        it('returns false when selection has multiple links', function() {
          var multiSegmentSpeedLimitLink = _.find(speedLimitTestData, function(x) { return x.length > 1; })[0];
          selectedSpeedLimit.open(multiSegmentSpeedLimitLink, false);
          expect(selectedSpeedLimit.isSeparable()).to.be.false;
        });
        it('returns false when selection does not have a two-way roadlink', function() {
          selectedSpeedLimit.open(speedLimitTestData[1][0], true);
          expect(selectedSpeedLimit.isSeparable()).to.be.false;
        });
      });

      describe('separating speed limit', function() {
        it('separates selection into two', function() {
          selectedSpeedLimit.open(speedLimitTestData[0][0], true);
          selectedSpeedLimit.separate();

          expect(selectedSpeedLimit.get()).to.have.length(2);
        });
      });
    });
  });
