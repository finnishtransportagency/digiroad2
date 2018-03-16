define(['chai', 'assetGrouping', 'zoomlevels', 'geometrycalculator'], function(chai, AssetGrouping) {
  var assert = chai.assert;
  var assetGrouping = new AssetGrouping(36);

  describe('Asset grouping', function() {
    describe('should be distance aware', function() {
      var assetLat1Lon1 = { id: 1, lat: 1, lon: 1 };
      var assetLat100Lon100 = { id: 2, lat: 100, lon: 100 };
      var assetLat200Lon300 = { id: 3, lat: 200, lon: 200 };
      var assetLat300Lon300 = { id: 4, lat: 300, lon: 300 };
      var assets = [ assetLat1Lon1, assetLat100Lon100, assetLat200Lon300, assetLat300Lon300 ];

      it('should not find any groups, if not within distance', function() {
        var actual = assetGrouping.groupByDistance(assets, zoomlevels.maxZoomLevel);
        assert.deepEqual(actual, [[assetLat1Lon1], [assetLat100Lon100], [assetLat200Lon300], [assetLat300Lon300]]);
      });

      it('should find group if overlapping', function() {
        var testAsset = { id: 5, lat: 1, lon: 1 };
        var testAssets = assets.concat([testAsset]);
        var actual = assetGrouping.groupByDistance(testAssets, zoomlevels.maxZoomLevel);
        var expected = [[assetLat1Lon1, testAsset], [assetLat100Lon100], [assetLat200Lon300], [assetLat300Lon300]];
        assert.deepEqual(actual, expected);
      });

      it('should not find group if same lat and different lon', function() {
        var testAsset = { id: 5, lat: 1, lon: 15 };
        var testAssets = assets.concat([testAsset]);
        var actual = assetGrouping.groupByDistance(testAssets, zoomlevels.maxZoomLevel);
        var expected = [[assetLat1Lon1], [assetLat100Lon100], [assetLat200Lon300], [assetLat300Lon300], [testAsset]];
        assert.deepEqual(actual, expected);
      });

      it('should find group if in proximity', function() {
        var testAsset = { id: 5, lat: 102, lon: 102 };
        var testAssets = assets.concat([testAsset]);
        var actual = assetGrouping.groupByDistance(testAssets, zoomlevels.maxZoomLevel);
        var expected = [[assetLat1Lon1], [assetLat100Lon100, testAsset], [assetLat200Lon300], [assetLat300Lon300]];
        assert.deepEqual(actual, expected);
      });

      it('should find groups for many assets', function() {
        var testAsset = { id: 5, lat: 1, lon: 1 };
        var testAsset2 = { id: 6, lat: 102, lon: 102 };
        var testAssets = assets.concat([testAsset, testAsset2]);
        var actual = assetGrouping.groupByDistance(testAssets, zoomlevels.maxZoomLevel);
        var expected = [[assetLat1Lon1, testAsset], [assetLat100Lon100, testAsset2], [assetLat200Lon300], [assetLat300Lon300]];
        assert.deepEqual(actual, expected);
      });

      it('should find groups for several assets in proximity', function() {
        var testAsset = { id: 5, lat: 297, lon: 301 };
        var testAsset2 = { id: 6, lat: 299, lon: 301 };
        var testAssets = assets.concat([testAsset, testAsset2]);
        var actual = assetGrouping.groupByDistance(testAssets, zoomlevels.maxZoomLevel);
        var expected = [[assetLat1Lon1], [assetLat100Lon100], [assetLat200Lon300], [assetLat300Lon300, testAsset, testAsset2]];
        assert.deepEqual(actual, expected);
      });
    });
  });
});
