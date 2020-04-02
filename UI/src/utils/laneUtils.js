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

      var numbers = _.sortBy(laneNumbers);

      var odd = _.filter(numbers, function (number) {
        return number % 2 !== 0;
      });
      var even = _.filter(numbers, function (number) {
        return number % 2 === 0;
      });

      var preview = function () {
        var previewList = $('<table class="preview">');

        var numberHeaders = $('<tr style="font-size: 11px;">').append(_.map(_.reverse(even).concat(odd), function (number) {
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
    }
  };
})();
