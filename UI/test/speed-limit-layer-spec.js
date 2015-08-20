define(['chai', 'TestHelpers', 'Layer', 'SpeedLimitLayer', 'SpeedLimitsCollection', 'SelectedSpeedLimit', 'zoomlevels', 'GeometryUtils', 'LinearAsset'],
       function(chai, testHelpers, BaseLayer, SpeedLimitLayer, SpeedLimitsCollection, SelectedSpeedLimit, zoomLevels, GeometryUtils, LinearAsset) {
  var assert = chai.assert;

  describe('SpeedLimitLayer', function() {
    describe('when moving map', function() {
      var layer;
      before(function() {
        eventbus = Backbone.Events;
        var speedLimitsCollection = new SpeedLimitsCollection({
          getSpeedLimits: function() {
            var speedLimitData = [
              [{id: 1, sideCode: 1, points: [{x: 0, y: 0}, {x: 5, y: 5}], position: 0}],
              [{id: 2, sideCode: 1, points: [{x: 10, y: 10}, {x: 15, y: 15}], position: 0}]
            ];
            return $.Deferred().resolve(speedLimitData);
          }
        });
        var selectedSpeedLimit = new SelectedSpeedLimit(speedLimitsCollection);
        var map = {
          addControl: function(control) {
            if (control.handlers) control.handlers.feature.activate = function() {};
            control.map = map;
          },
          events: {
            register: function() {},
            unregister: function() {},
            registerPriority: function() {}
          },
          getZoom: function() {
            return 10;
          },
          viewPortDiv: "",
          getExtent: function() {}
        };
        layer = new SpeedLimitLayer({
          map: map,
          application: {
            getSelectedTool: function() { return 'Select'; },
            isReadOnly: function() { return true; }
          },
          collection: speedLimitsCollection,
          selectedSpeedLimit: selectedSpeedLimit,
          geometryUtils: new GeometryUtils(),
          linearAsset: new LinearAsset(),
          roadCollection: {
            fetchFromVVH: function() {  eventbus.trigger('roadLinks:fetched'); },
            getAll: function() {}
          },
          roadLayer: {
            drawRoadLinks:  function() {}
          }
        });
        layer.update(9, null);
        eventbus.trigger('map:moved', {selectedLayer: 'speedLimit', bbox: null, zoom: 10});
      });
      after(function() {
        eventbus.stopListening();
      });

      it('should contain each speed limit only once', function() {
        var getFirstPointOfFeature = function(feature) {
          return feature.geometry.getVertices()[0];
        };

        assert.equal(testHelpers.getLineStringFeatures(layer.vectorLayer).length, 2);
        assert.equal(getFirstPointOfFeature(testHelpers.getLineStringFeatures(layer.vectorLayer)[0]).x, 0);
        assert.equal(getFirstPointOfFeature(testHelpers.getLineStringFeatures(layer.vectorLayer)[0]).y, 0);
        assert.equal(getFirstPointOfFeature(testHelpers.getLineStringFeatures(layer.vectorLayer)[1]).x, 10);
        assert.equal(getFirstPointOfFeature(testHelpers.getLineStringFeatures(layer.vectorLayer)[1]).y, 10);
      });

      describe('and zooming out', function() {
        before(function() {
          eventbus.trigger('map:moved', {selectedLayer: 'speedLimit', bbox: null, zoom: 8});
        });

        it('hides features', function() {
          assert.equal(testHelpers.getLineStringFeatures(layer.vectorLayer)[0].getVisibility(), false);
        });

        describe('and zooming in', function() {
          before(function() {
            eventbus.trigger('map:moved', {selectedLayer: 'speedLimit', bbox: null, zoom: 9});
          });

          it('should contain speed limits', function() {
            assert.equal(testHelpers.getLineStringFeatures(layer.vectorLayer).length, 2);
          });
        });
      });
    });
  });
});
