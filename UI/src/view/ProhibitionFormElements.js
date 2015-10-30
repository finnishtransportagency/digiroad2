(function(root) {
  root.ProhibitionFormElements = function(unit, editControlLabels, className, defaultValue, elementType, possibleValues) {
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

      function exceptionsElement() {
        var elementLabel = '<label>Rajoitus ei koske seuraavia ajoneuvoja:</label>';

        function existingExceptionElements() {
          var items = _(prohibition.exceptions).map(function(exception) {
            return '' +
              '<div class="form-group existing-exception">' +
              deleteButton() +
              '  <select class="form-control select">' +
              exceptionOptions(exception) +
              '  </select>' +
              '</div>';
          });
          return items.join('');
        }

        return '' +
          '<div class="exception-group">' +
          elementLabel +
          existingExceptionElements() +
          newExceptionElement() +
          '</div>';
      }

      function validityPeriodsElement() {
        var dayLabels = {
          Weekday: "Ma–Pe",
          Saturday: "La",
          Sunday: "Su"
        };

        function label(period) {
          return '' +
            '<label class="control-label">' +
            dayLabels[period.days] +
            '</label>';
        }

        function hourOptions(selectedOption, type) {
          var range = type === 'start' ? _.range(0, 24) : _.range(1, 25);
          return _.map(range, function(hour) {
            var selected = hour === selectedOption ? 'selected' : '';
            return '<option value="' + hour + '" ' + selected + '>' + hour + '</option>';
          });
        }

        function hourElement(selectedHour, type) {
          var className = type + '-hour';
          return '' +
            '<select class="form-control select ' + className + '">' +
            hourOptions(selectedHour, type) +
            '</select>';
        }

        var items = _(prohibition.validityPeriods).map(function(period) {
          return '' +
            '<div class="form-group existing-validity-period">' +
            deleteButton() +
            label(period) +
            hourElement(period.startHour, 'start') +
            hourElement(period.endHour, 'end') +
            '</div>';
        });

        return '' +
          '<div class="validity-period-group">' +
          items.join('') +
          '</div>';
      }

      return '' +
        '<div class="form-group edit-control-group">' +
        deleteButton() +
        typeElement() +
        validityPeriodsElement() +
        exceptionsElement() +
        '</div>';
    }

    function exceptionOptions(exception) {
      var elements = _.map(exceptionValues, function(name, key) {
        var selected = exception && (exception.toString() === key) ? 'selected' : '';
        return '' +
          '<option value="' + key + '" ' + selected + '>' +
          name +
          '</option>';
      });
      return elements.join('');
    }

    function newExceptionElement() {
      return '' +
        '<div class="form-group new-exception">' +
        '  <select class="form-control select">' +
        '    <option class="empty" disabled selected>Lisää poikkeus</option>' +
        exceptionOptions() +
        '  </select>' +
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
      $(rootElement).on('change', '.existing-exception select', function() {
        selectedLinearAsset.setValue(extractValue(rootElement));
      });

      $(rootElement).on('change', '.new-exception select', function(evt) {
        $(evt.target).parent().removeClass('new-exception').addClass('existing-exception');
        $(evt.target).before(deleteButton());
        $(evt.target).closest('.exception-group').append(newExceptionElement());
        selectedLinearAsset.setValue(extractValue(rootElement));
      });

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
      function extractExceptions(element) {
        var exceptionElements = $(element).find('.existing-exception select');
        return _.map(exceptionElements, function(exception) {
          return parseInt($(exception).val(), 10);
        });
      }

      function extractValidityPeriods(element) {

      }

      var prohibitionElements = $(rootElement).find('.existing-prohibition');

      return _.map(prohibitionElements, function(element) {
        return {
          typeId: parseInt($(element).val(), 10),
          exceptions: extractExceptions($(element).parent()),
          validityPeriods: extractValidityPeriods($(element).parent())
        };
      });
    }
  };
})(this);
