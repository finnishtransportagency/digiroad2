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
          '<div class="form-group editable ' + className + '">' +
            valueElement(currentValue) +
            editElement() +
          '</div>';
      }

      function valueElement(currentValue) {
        var items = _.map(currentValue, function(x) {
          return '' +
            '<li>' +
              prohibitionDisplayElement(x) +
              prohibitionEditElement(x) +
            '</li>';
        });
        return currentValue ? '<ul>' + items.join('') + '</ul>' : '-';
      }

      function prohibitionDisplayElement(prohibition) {
        var typeElement = '<span>' + prohibitionValues[prohibition.typeId] + '</span>';

        function exceptionElement() {
          var exceptionElements = _.map(prohibition.exceptions, function (exceptionId) {
            return '<li>' + exceptionValues[exceptionId] + '</li>';
          }).join('');
          var element = '' +
            '<div>Rajoitus ei koske seuraavia ajoneuvoja: ' +
            '  <ul>' + exceptionElements + '</ul>' +
            '</div>';
          return _.isEmpty(prohibition.exceptions) ?  '' : element;
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

        return '' +
          '<div class="form-control-static">' +
          typeElement +
          validityPeriodElement() +
          exceptionElement() +
          '</div>';
      }

      function deleteButton() {
        return '<button class="delete btn-delete">x</button>';
      }

      function prohibitionEditElement(prohibition) {
        function typeElement() {
          var optionTags = _.map(prohibitionValues, function(name, key) {
            var selected = prohibition.typeId.toString() === key ? 'selected' : '';
            return '<option value="' + key + '"' + ' ' + selected + '>' + name + '</option>';
          });
          return '' +
            '<select class="form-control select existing-prohibition">' +
            optionTags +
            '</select>';
        }

        return '' +
          '<div class="form-group edit-control-group">' +
          deleteButton() +
          typeElement() +
          '</div>';
      }

      function editElement() {
        var optionTags = _.map(prohibitionValues, function(name, key) {
          return '<option value="' + key + '">' + name + '</option>';
        });
        return '' +
          '<div class="form-group edit-control-group">' +
          '  <select class="form-control select new-prohibition">' +
          '    <option class="empty" disabled selected>Lisää kielto</option>' +
          optionTags +
          '  </select>' +
          '</div>';
      }

      function bindEvents(rootElement, selectedLinearAsset) {
        $(rootElement).on('change', 'select.existing-prohibition', function() {
          selectedLinearAsset.setValue(extractValue(rootElement));
        });

        $(rootElement).on('change', 'select.new-prohibition', function(evt) {
          $(evt.target).removeClass('new-prohibition').addClass('existing-prohibition');
          $(evt.target).before(deleteButton());
          $(rootElement).find('.form-group.' + className).append(editElement());
          selectedLinearAsset.setValue(extractValue(rootElement));
        });

        $(rootElement).on('click', 'button.delete', function(evt) {
          $(evt.target).parent().remove();
          selectedLinearAsset.setValue(extractValue(rootElement));
        });
      }

      function extractValue(rootElement) {
        var prohibitionElements = $(rootElement).find('.existing-prohibition');
        return _(prohibitionElements).map(function(element) {
          return {
            typeId: parseInt($(element).val(), 10),
            exceptions: [],
            validityPeriods: []
          };
        });
      }
    }
  };
})(this);
