(function() {
  window.laneUtils = {
    filterByValidityDirection: function(validityDirection, lanes){
      switch (validityDirection) {
        case validitydirections.sameDirection: return _.filter(lanes, function (lane) { return lane.toString().charAt(0) == 1; });
        case validitydirections.oppositeDirection: return _.filter(lanes, function (lane) {  return lane.toString().charAt(0) == 2;  });
        default: return [];
      }
    },

    createPreviewHeaderElement: function(laneNumbers) {
      var createNumber = function (number) {
        return $('<td class="preview-lane not-highlight-lane">' + number + '</td>');
      };

      var numbersPartitioned = _.partition(_.sortBy(laneNumbers), function(number){return number % 2 === 0;});
      var odd = _.last(numbersPartitioned);
      var even = _.head(numbersPartitioned);

      var preview = function () {
        var previewList = $('<table class="preview">');

        var numberHeaders = $('<tr class="number-header">').append(_.map(_.reverse(even).concat(odd), function (number) {
          return $('<th>' + (number.toString()[1] == '1' ? 'Pääkaista' : 'Lisäkaista') + '</th>');
        }));

        var oddListElements = _.map(odd, function (number) {
          return createNumber(number);
        });

        var evenListElements = _.map(even, function (number) {
          return createNumber(number);
        });

        return $('<div class="preview-div">').append(previewList.append(numberHeaders).append($('<tr>').append(evenListElements).append(oddListElements))).append('<hr class="form-break">');
      };

      return preview();
    },

    offsetByLaneNumber: function (asset, isRoadlink, reverse) {
      function getOffsetPoint(asset, baseOffset) {
        asset.points = _.map(asset.points, function (point, index, geometry) {
          return GeometryUtils.offsetPoint(point, index, geometry, asset.sideCode, baseOffset);
        });
        return asset;
      }

      var baseOffset = reverse ? 3.5 : -3.5;

      if(isRoadlink) {
        if (_.isUndefined(asset.sideCode) || asset.sideCode === 1) {
          return asset;
        }

        return getOffsetPoint(asset, baseOffset);
      }else{
        var laneCode = Property.getPropertyByPublicId(asset.properties, 'lane_code');

        if (_.head(laneCode.values).value.toString()[1] == '1') {
          if (asset.sideCode === 1) {
            return asset;
          }
          return getOffsetPoint(asset, baseOffset);
        }

        if (asset.sideCode === 1) {
          reverse = reverse || (asset.trafficDirection === "AgainstDigitizing" && (!asset.marker || (asset.marker && asset.id !== 0)));
          baseOffset = _.head(laneCode.values).value % 2 === 0 ? (reverse ? -1.5 : 1.5) : (reverse ? 1.5 : -1.5);
        }else{
          baseOffset = _.head(laneCode.values).value % 2 === 0 ? (reverse ? 2 : -2) : (reverse ? 5 : -5);
        }
        return getOffsetPoint(asset, baseOffset);
      }
    }
  };
})();