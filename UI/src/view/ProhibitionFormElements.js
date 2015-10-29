(function(root) {
  root.ProhibitionFormElements = function(unit, editControlLabels, className, defaultValue, elementType, possibleValues) {
    return prohibitionFormElements();

    function prohibitionFormElements() {
      var prohibitionValues = {
        3: 'Ajoneuvo',
        2: 'Moottoriajoneuvo',
        23: 'Läpiajo',
        12: 'Jalankulku',
        11: 'Polkupyörä',
        26: 'Ratsastus',
        10: 'Mopo',
        9: 'Moottoripyörä',
        27: 'Moottorikelkka',
        5: 'Linja-auto',
        8: 'Taksi',
        7: 'Henkilöauto',
        6: 'Pakettiauto',
        4: 'Kuorma-auto',
        15: 'Matkailuajoneuvo',
        19: 'Sotilasajoneuvo',
        13: 'Ajoneuvoyhdistelmä',
        14: 'Traktori tai maatalousajoneuvo',
        24: 'Ryhmän A vaarallisten aineiden kuljetus',
        25: 'Ryhmän B vaarallisten aineiden kuljetus'
      };
      var exceptionValues = {
        21: 'Huoltoajo',
        22: 'Tontille ajo',
        10: 'Mopo',
        9: 'Moottoripyörä',
        27: 'Moottorikelkka',
        5: 'Linja-auto',
        8: 'Taksi',
        7: 'Henkilöauto',
        6: 'Pakettiauto',
        4: 'Kuorma-auto',
        15: 'Matkailuajoneuvo',
        19: 'Sotilasajoneuvo',
        13: 'Ajoneuvoyhdistelmä',
        14: 'Traktori tai maatalousajoneuvo',
        24: 'Ryhmän A vaarallisten aineiden kuljetus',
        25: 'Ryhmän B vaarallisten aineiden kuljetus'
      };

      return {
        singleValueElement: singleValueElement,
        bindEvents: bindEvents
      };

      function singleValueElement(currentValue) {
        return '' +
          '<div class="form-group editable">' +
            valueElement(currentValue) +
            editElement() +
          '</div>';
      }

      function valueElement(currentValue) {
        var items = _.map(currentValue, function(x) {
          return '<li>' + prohibitionElement(x) + '</li>';
        });
        return currentValue ? '<ul>' + items.join('') + '</ul>' : '-';
      }

      function prohibitionElement(prohibition) {
        var typeElement = '<span>' + prohibitionValues[prohibition.typeId] + '</span>';

        function exceptionElement() {
          var exceptionElements = _.map(prohibition.exceptions, function (exceptionId) {
            return '<li>' + exceptionValues[exceptionId] + '</li>';
          }).join('');
          return _.isEmpty(prohibition.exceptions) ? '' : '<div>Rajoitus ei koske seuraavia ajoneuvoja: <ul>' + exceptionElements + '</ul></div>';
        }

        function validityPeriodElement() {
          var validityPeriodItems = _.map(prohibition.validityPeriods, function (period) {
            var dayName;
            if (period.days === "Saturday") {
              dayName = "La ";
            } else if (period.days === "Sunday") {
              dayName = "Su ";
            } else {
              dayName = "Ma - Pe ";
            }
            return '<li>' + dayName + period.startHour + ' - ' + period.endHour + '</li>';
          }).join('');
          return '<ul>' + validityPeriodItems + '</ul>';
        }

        return typeElement + validityPeriodElement() + exceptionElement();
      }

      function editElement() {
        var optionTags = _.map(prohibitionValues, function(name, key) {
          return '<option value="' + key + '">' + name + '</option>';
        });
        return '<div class="form-group edit-control-group">' +
          '<select class="form-control select new-prohibition">' +
          '<option class="empty" disabled selected>Lisää kielto</option>' +
          optionTags +
          '</select>' +
          '</div>';
      }

      function bindEvents(rootElement, selectedLinearAsset) {
        $(rootElement).on('change', '.new-prohibition', function(evt) {
          var value = parseInt($(evt.target).val(), 10);
          selectedLinearAsset.setValue([{typeId: value, exceptions: [], validityPeriods: []}]);
        });
      }
    }
  };
})(this);
